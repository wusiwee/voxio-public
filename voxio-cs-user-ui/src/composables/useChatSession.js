import { computed, ref, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'

import { createChatCrypto } from '../utils/chatCrypto'

/**
 * 访客端聊天客户端（功能点5：只走 Netty 网关 WebSocket，不使用 Spring WebSocket/STOMP）
 *
 * 协议：AUTH → AUTH_OK →（按会话）KEY_EXCHANGE，之后 CHAT/ACK、READ/READ_NOTIFY、SYNC 补发、
 * SESSION_* 事件、PING/PONG。聊天正文一律 SM4-GCM 密文上送，帧带 HMAC-SM3 完整性摘要（阶段10.2）。
 * 断线自动重连并在鉴权成功后用已知最大 seq 增量补发，保证离线期间消息不丢。
 */
const HEARTBEAT_MS = 30000
const RECONNECT_STEPS = [1000, 2000, 4000, 8000, 15000, 30000]

// 阶段11 功能点22：一条消息发出后多久没等到 ACK 就算「这一趟没走通」。
// 弱网下 WebSocket 的 send() 会成功返回（数据进了浏览器缓冲），但链路已经断了，
// 不盯这个超时，气泡就会一直停在「发送中」，访客只能干等或者手动重发。
const ACK_TIMEOUT_MS = 8000
const ACK_RETRIES = 2

const ERROR_TEXT = {
  4001: '接入凭证无效，请重新发起咨询',
  4002: '登录状态已失效，请重新发起咨询',
  4003: '客服当前不在线，消息将稍后送达',
  4004: '会话已结束或无权访问',
  4005: '发送太快了，请稍后再试',
  4006: '消息内容过长',
  4007: '消息格式不正确',
  4008: '该连接已绑定，无需重复登录',
  4009: '没有权限执行该操作',
  4010: '会话密钥尚未就绪，正在重新协商，请稍后重试',
  4011: '消息完整性校验失败，本条未发送成功'
}

function uid(prefix = 'c') {
  return prefix + '-' + Date.now().toString(36) + '-' + Math.random().toString(36).slice(2, 10)
}

export function useChatSession(options = {}) {
  const messages = ref([])
  const connected = ref(false)
  const authed = ref(false)
  const sessionStatus = ref('')
  const agent = ref(null)
  const queuePosition = ref(0)
  const peerTyping = ref(false)
  const connecting = ref(false)

  let ws = null
  let seq = 0
  let lastSeq = 0
  let attempts = 0
  let manualClose = false
  let heartbeat = null
  let reconnectTimer = null
  let typingTimer = null
  const pendingByClientId = new Map()
  const crypto = createChatCrypto()

  const token = () => options.getToken()

  function findIndex(clientMsgId) {
    return messages.value.findIndex((m) => m.clientMsgId === clientMsgId)
  }

  /**
   * 待 ACK 条数（阶段12 功能点4）：非 0 时发送按钮转圈并禁用。
   * 网关有 4005 频率限制，连点两下既会被限流也可能发出两条，前端先把入口锁住。
   */
  const pendingCount = computed(() => messages.value.reduce((n, m) => n + (m.status === 'sending' ? 1 : 0), 0))

  /**
   * 进入动效只在「刚发生」的那条上放一次（阶段12 功能点17）。
   *
   * 历史列表整页重建时若每行都带动画，访客一进来就看见满屏气泡依次淡入，
   * 那不是动效是抖动；所以只有实时链路（本地发出、WS 收到、系统事件）打标记，
   * fromHistory 不打，动画跑完再把标记清掉，避免组件被复用时重播。
   */
  function markFresh(message) {
    message.fresh = true
    setTimeout(() => { message.fresh = false }, 320)
  }

  function pushLocal(message) {
    markFresh(message)
    messages.value.push(message)
    scrollToBottom()
  }

  function pushSystem(content) {
    pushLocal({
      mid: uid('sys'),
      clientMsgId: uid('sys'),
      seq: null,
      local: true,
      senderType: '3',
      msgType: '4',
      content,
      createTime: Date.now(),
      status: 'sent'
    })
  }

  function scrollToBottom() {
    if (options.onAppend) options.onAppend()
  }

  function ensureOrder(message) {
    // 服务端消息按 seq 升序合并，重连补发时不会出现乱序
    const list = messages.value
    if (!message.seq) {
      list.push(message)
      return
    }
    let i = list.length - 1
    while (i >= 0 && list[i].seq && list[i].seq > message.seq) i--
    list.splice(i + 1, 0, message)
  }

  /** 落库编码与协议名互转：访客端只发文本，网关协议用 text/event，数据库用 1/4 */
  const WIRE_TYPE = { '1': 'text', '4': 'event' }

  function frame(cmd, body = {}, extra = {}) {
    const payload = { v: 1, cmd, seq: ++seq, body, ...extra }
    if (options.getSessionId()) payload.sid = options.getSessionId()
    return payload
  }

  function rawSend(payload) {
    if (ws && ws.readyState === WebSocket.OPEN) {
      // 封包（正文加密 + 帧 hmac）由加密层统一处理，未启用加密时原样发出
      ws.send(JSON.stringify(crypto.seal(payload)))
      return true
    }
    return false
  }

  function toViewMessage(frame) {
    const body = frame.body || {}
    return {
      mid: String(body.msgId || frame.mid),
      clientMsgId: body.clientMsgId,
      seq: body.seq,
      senderType: body.senderType || (frame.from ? (frame.from.t === 'agent' ? '2' : frame.from.t === 'system' ? '3' : '1') : '1'),
      msgType: frame.type || '1',
      content: body.content || '',
      createTime: body.createTime || frame.ts || Date.now(),
      status: 'sent'
    }
  }

  function upsertReceived(message) {
    // 同一 clientMsgId 可能既来自本地发送也来自补发，按 clientMsgId 去重
    if (message.clientMsgId) {
      const idx = findIndex(message.clientMsgId)
      if (idx >= 0) {
        messages.value[idx] = { ...messages.value[idx], ...message, status: message.status || messages.value[idx].status }
        return
      }
    }
    if (message.seq && message.seq <= lastSeq && message.senderType !== '3') {
      // 已处理过的服务端序号，直接丢弃
      return
    }
    if (message.seq) lastSeq = Math.max(lastSeq, message.seq)
    markFresh(message)
    ensureOrder(message)
    scrollToBottom()
  }

  function handleFrame(raw) {
    let frame = raw
    if (crypto.enabled) {
      try {
        frame = crypto.open(raw)
      } catch (error) {
        // 验签或解密失败只提示一次，不让单帧异常打断整个会话
        ElMessage.warning('收到一条校验失败的消息，已忽略')
        return
      }
      if (crypto.takeAck(frame)) return
    }
    switch (frame.cmd) {
      case 'AUTH_OK':
        authed.value = true
        connecting.value = false
        attempts = 0
        // 加密参数随 AUTH_OK 下发，之后每一帧都带 HMAC，聊天正文再按会话密钥加密
        crypto.configure(frame.body, token())
        prepareSessionKey((frame.body && frame.body.sessionId) || options.getSessionId())
        startHeartbeat()
        if (frame.body && frame.body.sessionId) options.onSessionId?.(frame.body.sessionId)
        // 断线重连后补发离线消息
        if (lastSeq > 0) rawSend(frame2('SYNC', { sinceSeq: lastSeq }))
        // 再把断线前没拿到 ACK 的消息重发一遍：弱网下这才是「我说的话没丢」的关键一步
        flushPending()
        break
      case 'PONG':
        break
      case 'CHAT':
        upsertReceived(toViewMessage(frame))
        if (frame.body && frame.body.senderType === '2') {
          peerTyping.value = false
          markReadSoon()
        }
        break
      case 'ACK': {
        const body = frame.body || {}
        if (body.action === 'send') {
          const clientId = frame.mid
          const idx = findIndex(clientId)
          const record = pendingByClientId.get(clientId)
          if (idx >= 0) {
            messages.value[idx].serverSeq = body.seq
            messages.value[idx].serverMsgId = body.msgId
            messages.value[idx].status = 'sent'
            if (body.seq) lastSeq = Math.max(lastSeq, body.seq)
          }
          if (record) clearTimeout(record.timer)
          pendingByClientId.delete(clientId)
        } else if (body.action === 'sync') {
          if (body.maxSeq) lastSeq = Math.max(lastSeq, body.maxSeq)
        }
        break
      }
      case 'READ_NOTIFY':
        messages.value.forEach((m) => {
          if (m.senderType === '1' && m.serverSeq && (!frame.body.untilSeq || m.serverSeq <= frame.body.untilSeq)) {
            m.status = 'read'
          }
        })
        break
      case 'SESSION_QUEUED':
        sessionStatus.value = '0'
        queuePosition.value = frame.body.position || 0
        if (frame.body.tip) pushSystem(frame.body.tip)
        break
      case 'SESSION_ASSIGNED':
        sessionStatus.value = '1'
        queuePosition.value = 0
        agent.value = { nick: frame.body.agentNick || '客服', avatar: frame.body.agentAvatar }
        pushSystem(`客服 ${frame.body.agentNick || '客服'} 已为您接入`)
        prepareSessionKey(frame.sid || options.getSessionId())
        options.onAssigned?.(frame.body)
        break
      case 'SESSION_NEW':
        // 访客侧不会收到新会话分配给自己，忽略
        break
      case 'SESSION_TRANSFERRED':
        if (frame.body.tip) pushSystem(frame.body.tip)
        agent.value = null
        break
      case 'SESSION_END':
        sessionStatus.value = '2'
        options.onEnded?.({ endBy: frame.body.endBy })
        disconnect()
        break
      case 'AGENT_STATUS':
        break
      case 'TYPING':
        if (frame.body.typing === 1) {
          peerTyping.value = true
          clearTimeout(typingTimer)
          typingTimer = setTimeout(() => (peerTyping.value = false), 4000)
        } else {
          peerTyping.value = false
        }
        break
      case 'KICKED':
        ElMessage.warning(frame.body.reason || '已在其他页面打开本次咨询')
        manualClose = true
        break
      case 'ERROR':
        handleError(frame)
        break
      default:
        break
    }
  }

  function frame2(cmd, body) {
    return { v: 1, cmd, seq: ++seq, body, sid: options.getSessionId() }
  }

  function handleError(frame) {
    const code = frame.body && frame.body.code
    const text = (frame.body && frame.body.msg) || ERROR_TEXT[code] || '消息发送失败'
    if (code === 4002 || code === 4001) {
      options.onInvalidToken?.()
      manualClose = true
      disconnect()
      return
    }
    if (code === 4004) {
      sessionStatus.value = '2'
    }
    // 被拒绝的消息标为失败，可点击重发；这类是「内容/频率」问题，不必再等 ACK
    if (code === 4006 || code === 4005) {
      const idx = findIndex(frame.mid)
      if (idx >= 0) messages.value[idx].status = 'failed'
      const record = pendingByClientId.get(frame.mid)
      if (record) {
        clearTimeout(record.timer)
        pendingByClientId.delete(frame.mid)
      }
    }
    ElMessage.error(text)
  }

  let readTimer = null
  function markReadSoon() {
    clearTimeout(readTimer)
    readTimer = setTimeout(() => {
      if (lastSeq > 0) rawSend(frame2('READ', { untilSeq: lastSeq }))
    }, 600)
  }

  function startHeartbeat() {
    clearInterval(heartbeat)
    heartbeat = setInterval(() => {
      if (authed.value) rawSend(frame2('PING', {}))
    }, HEARTBEAT_MS)
  }

  function connect() {
    const accessToken = token()
    if (!accessToken || !options.getSessionId()) return
    if (ws && (ws.readyState === WebSocket.OPEN || ws.readyState === WebSocket.CONNECTING)) return
    connecting.value = true
    manualClose = false
    // 新连接一律从「未配置」开始：AUTH_OK 自己是不带签名的（签名参数正是它下发的），
    // 若沿用上一条连接的 enabled/controlKey，重连时的 AUTH_OK 会被 open() 判成验签失败丢掉，
    // 结果 authed 永远置不上，访客端卡在「连接中」只能刷新页面（阶段15 全链路实测复现）。
    crypto.reset()
    authed.value = false
    // 同源 WS 地址：开发经 vite 代理、生产经 nginx 转发到网关，不直连后端主机
    ws = new WebSocket(`${location.protocol === 'https:' ? 'wss' : 'ws'}://${location.host}${import.meta.env.VITE_APP_WS_PREFIX}`)
    ws.onopen = () => {
      connected.value = true
      rawSend({ v: 1, cmd: 'AUTH', seq: ++seq, body: { token: accessToken } })
    }
    ws.onmessage = (event) => {
      let frame = null
      try {
        frame = JSON.parse(event.data)
      } catch {
        return
      }
      handleFrame(frame)
    }
    ws.onclose = () => {
      connected.value = false
      authed.value = false
      connecting.value = false
      clearInterval(heartbeat)
      if (!manualClose) scheduleReconnect()
    }
    ws.onerror = () => {
      connected.value = false
    }
  }

  function scheduleReconnect() {
    if (sessionStatus.value === '2') return
    const delay = RECONNECT_STEPS[Math.min(attempts, RECONNECT_STEPS.length - 1)]
    attempts++
    clearTimeout(reconnectTimer)
    reconnectTimer = setTimeout(() => {
      if (!manualClose) connect()
    }, delay)
  }

  /**
   * 系统告诉我们「网络回来了」（地铁里重连、切回 Wi-Fi）：别再等退避计时器走完，立刻重连。
   * 弱网场景下这一步最能改善体感——链路已经可用，却要按 8s/15s/30s 的阶梯干等。
   */
  function onNetworkBack() {
    if (manualClose || sessionStatus.value === '2') return
    if (connected.value && authed.value) return
    attempts = 0
    clearTimeout(reconnectTimer)
    connect()
  }
  if (typeof window !== 'undefined') {
    window.addEventListener('online', onNetworkBack)
  }

  function disconnect() {
    manualClose = true
    clearInterval(heartbeat)
    clearTimeout(reconnectTimer)
    if (ws) {
      try {
        ws.close()
      } catch {
        /* 忽略 */
      }
      ws = null
    }
    connected.value = false
    authed.value = false
  }

  function send(payload) {
    const clientMsgId = payload.clientMsgId || uid()
    const message = {
      mid: clientMsgId,
      clientMsgId,
      senderType: '1',
      msgType: payload.msgType || '1',
      content: payload.content || '',
      createTime: Date.now(),
      status: 'sending'
    }
    pushLocal(message)
    const sid = options.getSessionId()
    const chatFrame = () => ({
      v: 1,
      cmd: 'CHAT',
      seq: ++seq,
      mid: clientMsgId,
      sid,
      type: WIRE_TYPE[message.msgType] || 'text',
      body: {
        content: message.content
      }
    })
    // 幂等靠的是同一个 clientMsgId：服务端 uk_session_client 命中就回原 seq，不会重复入库，
    // 所以超时重发同一帧是安全的，可以放心自动重试
    const record = { message, chatFrame, retries: 0, timer: null }
    pendingByClientId.set(clientMsgId, record)
    const deliver = () => {
      if (!rawSend(chatFrame())) {
        message.status = 'failed'
        ElMessage.warning('连接已断开，正在重连，请稍后重发')
        scheduleReconnect()
        return
      }
      watchAck(clientMsgId)
    }
    // 本会话还没协商过密钥：先做一轮 KEY_EXCHANGE 再发，正文一律密文出网
    if (crypto.enabled && sid && !crypto.hasKey(sid)) {
      crypto.ensureKey(sid, rawSend).then(deliver, () => {
        message.status = 'failed'
        ElMessage.warning('会话密钥协商失败，请点击重发')
      })
    } else {
      deliver()
    }
    return message
  }

  /** 等 ACK：超时先自动重发，重发用完再标失败让访客手动重试 */
  function watchAck(clientMsgId) {
    const record = pendingByClientId.get(clientMsgId)
    if (!record) return
    clearTimeout(record.timer)
    record.timer = setTimeout(() => {
      if (!pendingByClientId.has(clientMsgId)) return
      if (authed.value && record.retries < ACK_RETRIES) {
        record.retries++
        if (rawSend(record.chatFrame())) {
          watchAck(clientMsgId)
          return
        }
      }
      // 弱网/断线时不弹红字打扰，气泡上的「重发」入口还在
      record.message.status = 'failed'
      pendingByClientId.delete(clientMsgId)
    }, ACK_TIMEOUT_MS)
  }

  /** 重连并鉴权成功后，把上次没拿到 ACK 的消息补发出去（同一 clientMsgId，服务端幂等） */
  function flushPending() {
    pendingByClientId.forEach((record, clientMsgId) => {
      if (record.message.status === 'failed') {
        record.message.status = 'sending'
      }
      if (rawSend(record.chatFrame())) {
        watchAck(clientMsgId)
      }
    })
  }

  /**
   * 会话确定后提前协商密钥
   *
   * 协商完成后立刻按 lastSeq 增量补发：密钥未就绪期间服务端不会明文下发消息（宁缺不泄），
   * 这些消息会以「已入库未送达」留在库里，靠这一次 SYNC 取回。
   */
  function prepareSessionKey(sid) {
    if (!crypto.enabled || !sid || !authed.value) return
    if (crypto.hasKey(sid)) return
    crypto.ensureKey(sid, rawSend).then(() => {
      rawSend(frame2('SYNC', { sinceSeq: lastSeq }))
    }).catch(() => {
      /* 协商失败会在发消息时再试一次，这里不打断界面 */
    })
  }

  function sendText(content) {
    const text = (content || '').trim().slice(0, 2000)
    if (!text) return null
    return send({ content: text })
  }

  function resend(message) {
    const index = findIndex(message.clientMsgId)
    if (index >= 0) messages.value.splice(index, 1)
    const stale = pendingByClientId.get(message.clientMsgId)
    if (stale) clearTimeout(stale.timer)
    pendingByClientId.delete(message.clientMsgId)
    return send({
      clientMsgId: message.clientMsgId,
      msgType: message.msgType,
      content: message.content
    })
  }

  /** 历史消息（HTTP 返回的 KfMessage）转成界面消息；首屏与向前翻页共用一份映射 */
  function fromHistory(item) {
    return {
      mid: String(item.msgId),
      clientMsgId: item.clientMsgId || String(item.msgId),
      seq: item.seq,
      senderType: item.senderType,
      msgType: item.msgType,
      content: item.content || '',
      createTime: item.createTime ? new Date(item.createTime).getTime() : Date.now(),
      status: item.deliverStatus === '2' ? 'read' : 'sent'
    }
  }

  function loadHistory(list) {
    // 首屏历史来自 HTTP（KfMessage 结构），按 seq 升序写入并记录 lastSeq 供 SYNC 使用
    messages.value = (list || []).map(fromHistory)
    messages.value.forEach((m) => {
      if (m.seq) lastSeq = Math.max(lastSeq, m.seq)
    })
    messages.value.sort((a, b) => (a.seq || 0) - (b.seq || 0))
    scrollToBottom()
  }

  /**
   * 向前翻页（阶段11 功能点3/20）：把更早的一页插到最前面，而不是整窗重刷。
   *
   * 返回真正新增的条数，页面据此决定「还要不要继续显示加载更早」；
   * 同一 clientMsgId 的行会被跳过，避免访客来回翻页把同一条消息排两遍。
   */
  function prependOlder(list) {
    const known = new Set(messages.value.map((m) => m.clientMsgId))
    const older = (list || []).map(fromHistory).filter((m) => !known.has(m.clientMsgId))
    if (!older.length) return 0
    older.sort((a, b) => (a.seq || 0) - (b.seq || 0))
    messages.value = [...older, ...messages.value]
    return older.length
  }

  function noteServerSeq(value) {
    if (value) lastSeq = Math.max(lastSeq, value)
  }

  onUnmounted(() => {
    if (typeof window !== 'undefined') {
      window.removeEventListener('online', onNetworkBack)
    }
    // 组件卸载后没有归属了，未决的 ACK 计时器要清掉，否则回调会往已销毁的列表里写状态
    pendingByClientId.forEach((record) => clearTimeout(record.timer))
    pendingByClientId.clear()
    disconnect()
  })

  return {
    messages,
    pendingCount,
    connected,
    authed,
    connecting,
    sessionStatus,
    agent,
    queuePosition,
    peerTyping,
    connect,
    disconnect,
    sendText,
    resend,
    loadHistory,
    prependOlder,
    pushSystem,
    noteServerSeq,
    markRead: () => lastSeq > 0 && rawSend(frame2('READ', { untilSeq: lastSeq })),
    sendTyping: (typing) => rawSend(frame2('TYPING', { typing: typing ? 1 : 0 }))
  }
}
