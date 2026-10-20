<template>
  <div class="cs-shell">
    <div class="cs-panel chat">
      <div class="cs-header">
        <img class="cs-header__logo" src="@/assets/brand/mark.svg" alt="Voxio 在线客服" />

        <span class="cs-dot" :class="dotClass"></span>
        <div class="chat__who">
          <span class="cs-header__title text-ellipsis">{{ headTitle }}</span>
          <span class="chat__sub cs-muted">{{ headSub }}</span>
        </div>

        <span class="cs-header__spacer"></span>
        <el-button v-if="canOffline && !serving" size="small" @click="offlineVisible = true">留言</el-button>
        <el-button v-if="serving" size="small" text type="danger" @click="confirmEnd">结束会话</el-button>
      </div>

      <!-- 断网横幅独立于会话状态：链路断了先说清楚「为什么没回应」，恢复后自动消失（功能点8） -->
      <div v-if="offlineNow" class="chat-banner chat-banner--offline chat__offline">
        <el-icon><WarningFilled /></el-icon>
        <span>网络已断开，页面内容可能不是最新的。恢复连接后这条提示会自动消失。</span>
      </div>

      <div v-if="queueing" class="chat__queue">
        <el-icon class="is-loading"><Loading /></el-icon>
        <span>{{ queueTipText }}</span>
      </div>
      <div v-else-if="connectTip" class="chat__queue" :class="{ 'chat__queue--warn': !started }">
        <el-icon v-if="started" class="is-loading"><Loading /></el-icon>
        <span>{{ connectTip }}</span>
      </div>
      <div v-else-if="!offlineNow && opened && !connected && !ended" class="chat__queue chat__queue--warn">
        <span>连接已断开，正在自动重连。若长时间没有恢复，可以刷新页面重新进入本次会话。</span>
      </div>

      <div ref="listRef" class="chat__list" @pointerdown="dismissKeyboard">
        <!-- 首屏历史在路上：用四行宽度不一的骨架占住高度，消息到位后原位替换，不会出现「空白→整屏弹出」 -->
        <div v-if="historyLoading && !messages.length" class="chat-skeleton" aria-hidden="true">
          <div class="chat-skeleton__row"><div class="chat-skeleton__bubble" style="width:62%"></div></div>
          <div class="chat-skeleton__row chat-skeleton__row--right"><div class="chat-skeleton__bubble" style="width:46%"></div></div>
          <div class="chat-skeleton__row"><div class="chat-skeleton__bubble" style="width:72%"></div></div>
          <div class="chat-skeleton__row chat-skeleton__row--right"><div class="chat-skeleton__bubble" style="width:34%"></div></div>
        </div>

        <div v-else-if="!messages.length" class="chat__empty">
          <el-icon class="chat__empty-icon"><ChatDotRound /></el-icon>
          <p>还没有消息，直接在下方输入您的问题</p>
        </div>

        <template v-else>
          <div v-if="hasOlder" class="chat__older chat-older">
            <button type="button" class="chat__older-btn" :disabled="olderLoading" @click="loadOlder">
              <el-icon v-if="olderLoading" class="is-loading"><Loading /></el-icon>
              <span>{{ olderLoading ? '加载中…' : '加载更早的消息' }}</span>
            </button>
          </div>
          <MessageItem
            v-for="(item, index) in messages"
            :key="item.clientMsgId || item.mid"
            :message="item"
            :time-divider="dividerAt(index)"
            @resend="resend"
          />
          <div v-if="peerTyping" class="chat__typing cs-muted">客服正在输入…</div>
        </template>
      </div>

      <!-- 接入/留言入口：只在「还没发起 / 已结束」这一屏出现（窄屏用这颗占宽按钮，宽屏改由右下角悬浮气泡承担同一职责） -->
      <button
        v-if="showEntryBubble"
        type="button"
        class="chat__entry-cta"
        :disabled="contacting"
        @click="contactAgent()"
      >
        {{ contacting ? '正在接入…' : canChat ? '联系人工客服' : '留言给客服' }}
      </button>

      <div class="cs-footer">
        <ChatInput
          ref="inputRef"
          :disabled="inputDisabled"
          :placeholder="inputPlaceholder"
          :pending="pendingCount"
          @send="onSend"
          @typing="onTyping"
        />
      </div>

      <!--
        咨询入口的「醒目但不打扰」：只在还没发起会话（或已结束）这一屏出现，挂在面板右下角、
        让位在输入条上方，不遮消息区。窄屏刻意不悬浮——面板已经整屏铺满，浮层会压住输入区，
        那种「方便」不是给访客用的，所以小屏改由上面那颗占宽按钮承担同一职责（功能点1/7）。
      -->
      <transition name="sb-fade">
        <button
          v-if="showEntryBubble"
          type="button"
          class="entry-bubble"
          :class="{ 'entry-bubble--offline': !canChat }"
          :disabled="contacting"
          @click="contactAgent()"
        >
          <el-icon><Headset /></el-icon>
          <span>{{ contacting ? '正在接入…' : canChat ? '联系人工客服' : '留言给客服' }}</span>
        </button>
      </transition>

      <!-- 首次进来的一句轻提示：只出现一次，看过或用过就落本地标记，之后不再打扰（功能点1） -->
      <div v-if="entryTipVisible" class="entry-tip" role="status">
        <span>有问题直接写在这里就行，发送后会自动转人工客服。</span>
        <button type="button" class="entry-tip__close" aria-label="关闭提示" @click="dismissEntryTip">×</button>
      </div>
    </div>

    <OfflineDialog
      v-model="offlineVisible"
      :session-id="currentSessionId"
      :default-content="offlineDraft"
      :reply-tip="replyTip"
    />
  </div>
</template>

<script setup>
import { computed, defineAsyncComponent, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ChatDotRound, Headset, Loading, WarningFilled } from '@element-plus/icons-vue'
import MessageItem from '@/components/MessageItem.vue'
import ChatInput from '@/components/ChatInput.vue'
// 留言是「客服离线」时才可能出现的面板：首屏没必要为它付下载与解析成本，改成用到时再拉（阶段11 功能点20）
const OfflineDialog = defineAsyncComponent(() => import('@/components/OfflineDialog.vue'))
import { createSession, endSession, getEntryStatus, getSessionMessages } from '@/api/open'
import { useChatSession } from '@/composables/useChatSession'
import { clearSession, getAgentNick, getSessionId, getToken, getVisitorNo, saveSession } from '@/utils/visitor'
import { formatTime } from '@/utils/format'

const listRef = ref(null)
const inputRef = ref(null)
const currentSessionId = ref(getSessionId())
const config = ref({})
const canOffline = ref(false)
const canChat = ref(false)
const onlineAgents = ref(0)
const idleAgents = ref(0)
const queuedCount = ref(0)
const offlineVisible = ref(false)
// 从输入框带进留言框的问题，省掉访客再抄一遍（功能点6）
const offlineDraft = ref('')
const serving = ref(false)
const queueing = ref(false)
const ended = ref(false)
// 会话是否已发起：未发起时对话框是空首屏，点了「人工客服」才建会话并接入
const started = ref(!!getToken() && !!currentSessionId.value)
// 是否真的建过会话并连过网关：无客服在线时只提示，不该报「连接已断开」
const opened = ref(false)
const contacting = ref(false)
// 悬浮入口只在「还没发起 / 已结束」这一屏出现；会话一旦建立就该让位给对话本身
const showEntryBubble = computed(() => (!started.value || ended.value) && !contacting.value)
const entryTipVisible = ref(false)
// 点「人工客服」后的接入提示；历史消息会整体覆盖列表，所以这条只能放在横幅里而不是消息气泡
const connectTip = ref('')

const chat = useChatSession({
  getToken,
  getSessionId: () => currentSessionId.value,
  onSessionId: (id) => (currentSessionId.value = id),
  onAssigned: () => {
    serving.value = true
    queueing.value = false
    connectTip.value = ''
  },
  onEnded: () => {
    serving.value = false
    queueing.value = false
    ended.value = true
    announceEnded()
  },
  onInvalidToken: () => {
    clearSession()
    currentSessionId.value = ''
    started.value = false
    serving.value = false
    ended.value = false
    ElMessage.warning('咨询状态已失效，请重新联系人工客服')
  }
});

const {
  messages,
  pendingCount,
  connected,
  authed,
  agent,
  queuePosition,
  peerTyping,
  connect,
  sendText,
  resend,
  loadHistory,
  prependOlder,
  pushSystem
} = chat

// 会话在接入前就已分配时，SESSION_ASSIGNED 不会重放，用本地记录补上昵称
if (!agent.value && getAgentNick()) {
  agent.value = { nick: getAgentNick() }
}

/** 会话结束：不再索取任何反馈，只在对话区落一条与其它系统提示同样式的收尾消息（功能点5） */
function announceEnded() {
  pushSystem('会话已结束，欢迎再次咨询')
}

// 响应时间口径：运营在后台配了就用配的，没配也要说清「什么时候会被处理」，
// 否则离线留言只剩「已提交」三个字，访客不知道自己在等什么（功能点6）。
// 这里刻意不编造「30 分钟内回复」这类库里并不存在的时效承诺，只复述真实配置的服务时间。
const replyTip = computed(() => {
  const tip = config.value['kf.offline.reply.tip']
  if (tip) return tip
  const serviceTime = config.value['kf.service.time']
  return serviceTime
    ? `客服上线后会按您留的联系方式回复（服务时间：${serviceTime}）。`
    : '客服上线后会按您留的联系方式回复。'
})
// 排队横幅直接用后台配置的 kf.queue.tip，{position} 换成实时位次，运营改文案前端不用跟着改。
// 后面补的是「库内已知的事实」（前面还有几个人、现在有几个人空闲），不是估算出来的等待分钟数：
// 任何「预计 3 分钟」这类数字都是假进度条的另一种写法，访客照着等只会更焦躁（功能点3）
const queueTipText = computed(() => {
  const tpl = config.value['kf.queue.tip'] || '您排在第 {position} 位，请稍候。'
  const base = tpl.replace('{position}', String(queuePosition.value || 1))
  const ahead = Math.max(0, (queuePosition.value || 1) - 1)
  const facts = []
  if (ahead > 0) facts.push(`前面还有 ${ahead} 位访客`)
  if (idleAgents.value > 0) facts.push(`${idleAgents.value} 位客服空闲，会优先接进来`)
  return facts.length ? `${base}（${facts.join('，')}）` : base
})

const dotClass = computed(() => {
  if (ended.value) return 'cs-dot--offline'
  if (serving.value && connected.value) return 'cs-dot--online'
  return 'cs-dot--offline'
})

const headTitle = computed(() =>
  serving.value ? agent.value?.nick || '客服' : ended.value ? '会话已结束' : '在线客服'
)
const headSub = computed(() => {
  if (queueing.value) return `排队中，您排在第 ${queuePosition.value || '-'} 位`
  if (ended.value) return '本次咨询已结束，可重新联系人工客服'
  if (serving.value) return connected.value ? '实时对话中' : '连接中'
  if (!started.value) return onlineAgents.value > 0 ? `客服在线 ${onlineAgents.value} 人` : '客服离线，可留言'
  return '正在接入'
})

const canSend = computed(() => serving.value && connected.value && authed.value && !ended.value)
/**
 * 排队中也允许发：网关只拦「已结束」的会话（ChatMessageDispatcher 里唯一的会话状态判定就是 STATUS_ENDED），
 * 排队消息会正常落库，客服接入后从历史里一次看到，比「等接进来再重新打一遍」少一轮往返（功能点3/29）。
 */
const canSendQueued = computed(() => started.value && queueing.value && connected.value && authed.value && !ended.value)
const sendReady = computed(() => canSend.value || canSendQueued.value)
// 只有会话结束后才锁住输入区；其余状态一律让访客先把问题写下来，发送时再按当前状态分流
const inputDisabled = computed(() => ended.value)

const inputPlaceholder = computed(() => {
  if (ended.value) return '会话已结束，可重新联系人工客服'
  if (!started.value) return canChat.value ? '直接写问题，发送后自动转人工客服' : '客服不在线，可写下问题留言'
  if (queueing.value) return '客服接入中，可先把问题写清楚'
  if (!sendReady.value) return '正在接入，请稍候…'
  return '请描述您的问题，Enter 发送 / Shift+Enter 换行'
})

// 相邻消息间隔超过 5 分钟时插入时间分组
const DIVIDER_GAP_MS = 5 * 60 * 1000

function dividerAt(index) {
  const list = messages.value
  const current = Date.parse(list[index]?.createTime || '')
  if (Number.isNaN(current)) return ''
  const prev = index > 0 ? Date.parse(list[index - 1].createTime || '') : NaN
  if (!Number.isNaN(prev) && current - prev < DIVIDER_GAP_MS) return ''
  return formatTime(list[index].createTime)
}

function scrollBottom() {
  nextTick(() => {
    const el = listRef.value
    if (el) el.scrollTop = el.scrollHeight
  })
}

/** 点消息区就把键盘收掉：小屏上这是唯一能被发现的「收起键盘」手势（功能点7） */
function dismissKeyboard() {
  inputRef.value?.blur()
}

function dispatch(payload) {
  sendText(payload.content)
}

/**
 * 发送的唯一入口，按「当前处在哪一屏」分流（功能点2/4/29）：
 * 未发起时这一次点击就同时完成「建会话 + 把这句话发出去」，不再要求访客先找到入口点一次、再点一次发送；
 * 已发起时正常发送，排队中同样允许（见 canSendQueued 注释）。
 */
async function onSend(payload) {
  if (ended.value) return
  if (!started.value) {
    await contactAgent(payload, { sendIt: true })
    return
  }
  if (!sendReady.value) {
    if (canOffline.value) {
      offlineDraft.value = payload.content
      offlineVisible.value = true
      ElMessage.warning('还没接入人工客服，可以先留言，客服上线会按留言回复您')
      return
    }
    ElMessage.warning('正在接入人工客服，稍等片刻再发送')
    return
  }
  dispatch(payload)
}

let typingTimer = null
function onTyping(typing) {
  if (!canSend.value) return
  chat.sendTyping(!!typing)
  clearTimeout(typingTimer)
  if (typing) {
    typingTimer = setTimeout(() => chat.sendTyping(false), 3000)
  }
}

/** 读取排队与在线状态；访客端只信这份实时数据，不在前端写死文案 */
async function loadStatus(force = false) {
  const res = await getEntryStatus(force)
  const data = res.data || {}
  config.value = data.config || {}
  canOffline.value = !!data.canOffline
  canChat.value = !!data.canChat
  onlineAgents.value = data.onlineAgents || 0
  idleAgents.value = data.idleAgents || 0
  queuedCount.value = data.queuedCount || 0
  return { canChat: !!data.canChat, canOffline: !!data.canOffline, config: config.value }
}

/** 建会话 + 拉历史 + 连网关，status 决定当前是排队还是已接入 */
async function openConversation() {
  opened.value = true
  started.value = true
  let data = await ensureSession()
  if (data.status === '2') {
    // 上次的会话已结束，重新发起一次，避免访客对着一个只读的旧会话
    clearSession()
    data = await ensureSession()
  }
  currentSessionId.value = data.sessionId
  queueing.value = data.status === '0'
  serving.value = data.status === '1'
  queuePosition.value = data.position || 0
  ended.value = false
  if (serving.value) connectTip.value = ''
  if (data.agentNick) agent.value = { nick: data.agentNick }
  await loadHistoryMessages()
  scrollBottom()
  connect()
}

async function ensureSession() {
  const res = await createSession({
    visitorNo: getVisitorNo(),
    nickname: '访客',
    sourcePage: location.href,
    referrer: document.referrer || ''
  })
  const data = res.data || {}
  saveSession(data)
  return data
}

/**
 * openConversation 只把 connect() 发出去，AUTH_OK 是异步回来的。
 * 「打字即发起」要在那一刻之后才发得出消息，否则会被 send() 判成「连接已断开」。
 * 这里等一个有上限的窗口，等不到就把文字退回输入框，不让访客写的内容消失。
 */
function waitAuthed(timeoutMs = 8000) {
  if (authed.value) return Promise.resolve(true)
  return new Promise((resolve) => {
    let timer = null
    const stop = watch(authed, (on) => {
      if (!on) return
      stop()
      clearTimeout(timer)
      resolve(true)
    })
    timer = setTimeout(() => {
      stop()
      resolve(authed.value)
    }, timeoutMs)
  })
}

/** 入口按钮与首屏直接发送都汇到这里 */
async function contactAgent(input, { sendIt = false } = {}) {
  const payload = !input
    ? null
    : typeof input === 'string'
      ? { type: 'text', content: input }
      : input
  contacting.value = true
  dismissEntryTip()
  try {
    const status = await loadStatus(true)
    if (!status.canChat) {
      // 无客服在线：保持空首屏，只在横幅里给离线口径，并把留言对话框打开
      connectTip.value = status.config['kf.agent.offline.tip']
        || '当前客服不在线，您可以留言或提交工单，我们会尽快联系您。'
      if (status.canOffline) {
        // 首屏已经写了一半的问题，直接带进留言框，不让访客再抄一遍（功能点6）
        if (payload) offlineDraft.value = payload.content
        offlineVisible.value = true
      }
      return
    }
    connectTip.value =
      idleAgents.value > 0 && queuedCount.value === 0
        ? '当前客服空闲，正在为您接入人工客服…'
        : queuedCount.value > 0
          ? `当前有 ${queuedCount.value} 位访客正在排队，正在为您接入人工客服…`
          : '客服正在接待其他访客，已为您排队等候…'
    await openConversation()
    if (!payload) return
    if (!sendIt) {
      inputRef.value?.setText(payload.content)
      return
    }
    // 「打字 → 直接发出去」：省掉预填之后再点一次发送这一跳（功能点2）
    const ready = await waitAuthed()
    if (ready && !ended.value) {
      dispatch(payload)
    } else {
      inputRef.value?.setText(payload.content)
      ElMessage.warning('接入还没就绪，问题已留在输入框，稍后请再点一次发送')
    }
  } catch {
    ElMessage.warning('会话建立失败，可先留言')
    if (canOffline.value) {
      if (payload) offlineDraft.value = payload.content
      offlineVisible.value = true
    } else if (payload) {
      // 没开留言又没有兜底入口时，至少把访客刚写的字还回输入框——「点了发送结果话没了」是最让人不敢再试的失败
      inputRef.value?.setText(payload.content)
    }
  } finally {
    contacting.value = false
  }
}

/**
 * 排队期间轮询自己的位次：同一 visitorNo 再 POST /kf/open/session 只会命中活动会话并回读状态，
 * 不会重复建会话，也不会补写欢迎语；前面的人被接走后，位次靠这个刷新才动得起来
 */
let queueTimer = null
async function refreshQueue() {
  try {
    const data = await ensureSession()
    currentSessionId.value = data.sessionId
    queuePosition.value = data.position || 0
    if (data.status === '1') {
      queueing.value = false
      serving.value = true
      connectTip.value = ''
      if (data.agentNick) agent.value = { nick: data.agentNick }
      await loadHistoryMessages()
      scrollBottom()
      connect()
    } else if (data.status === '2') {
      queueing.value = false
      ended.value = true
      announceEnded()
    }
  } catch {
    /* 网络抖动下一轮再试 */
  }
}

watch(queueing, (on) => {
  clearInterval(queueTimer)
  if (on) queueTimer = setInterval(refreshQueue, 8000)
})

onUnmounted(() => {
  clearInterval(queueTimer)
  window.removeEventListener('offline', offlineHandler)
  window.removeEventListener('online', onlineHandler)
  if (window.visualViewport) window.visualViewport.removeEventListener('resize', viewportHandler)
  else window.removeEventListener('resize', viewportHandler)
})

// 首屏只拉最近一页，更早的历史由访客自己点「加载更早的消息」翻（阶段11 功能点3/20）
const HISTORY_PAGE = 30
const olderLoading = ref(false)
const hasOlder = ref(false)
// 首屏历史是否在飞：只在列表仍为空时用来渲染骨架，避免二次刷新把已有消息换成灰块闪一下（功能点2）
const historyLoading = ref(false)
// 浏览器离线状态：断网期间常驻顶部提示条，online 事件一到就消失（功能点8）
const offlineNow = ref(typeof navigator !== 'undefined' && navigator.onLine === false)

async function loadHistoryMessages() {
  if (!currentSessionId.value) return
  historyLoading.value = true
  try {
    const res = await getSessionMessages({ sessionId: currentSessionId.value, limit: HISTORY_PAGE })
    const data = res.data || {}
    if (data.sessionId) currentSessionId.value = data.sessionId
    const list = data.messages || []
    loadHistory(list)
    hasOlder.value = list.length >= HISTORY_PAGE
    const status = data.status
    if (status === '1') serving.value = true
    else if (status === '0') queueing.value = true
    else if (status === '2') {
      ended.value = true
      serving.value = false
      announceEnded()
    }
  } finally {
    historyLoading.value = false
  }
}

/** 向前翻页：按当前最早一条的 seq 做游标，比 offset 翻页稳（期间有人发消息也不会漏条重条） */
async function loadOlder() {
  if (olderLoading.value || !currentSessionId.value) return
  const seqs = messages.value.map((m) => m.seq).filter((s) => !!s)
  if (!seqs.length) {
    hasOlder.value = false
    return
  }
  olderLoading.value = true
  // 前面插入内容会把滚动位置顶走，先记下高度差再补回去
  const el = listRef.value
  const keepBottom = el ? el.scrollHeight - el.scrollTop : 0
  try {
    const res = await getSessionMessages({
      sessionId: currentSessionId.value,
      beforeSeq: Math.min(...seqs),
      limit: HISTORY_PAGE
    })
    const list = (res.data || {}).messages || []
    const added = prependOlder(list)
    hasOlder.value = list.length >= HISTORY_PAGE
    if (added && el) {
      nextTick(() => (el.scrollTop = el.scrollHeight - keepBottom))
    }
  } catch {
    /* 请求层已提示，保留按钮让访客再点一次 */
  } finally {
    olderLoading.value = false
  }
}

async function confirmEnd() {
  try {
    await ElMessageBox.confirm('结束后需要重新发起咨询，确定结束本次会话吗？', '结束会话', {
      confirmButtonText: '结束会话',
      cancelButtonText: '再聊一会',
      type: 'warning'
    })
  } catch {
    return
  }
  await endSession(currentSessionId.value, '访客主动结束')
  serving.value = false
  queueing.value = false
  ended.value = true
  chat.disconnect()
  announceEnded()
}

// 浏览器只告诉「网卡断了」，不告诉链路质量；恢复时顺手让网关客户端立刻重连（useChatSession 里已监听 online）
const offlineHandler = () => (offlineNow.value = true)
const onlineHandler = () => (offlineNow.value = false)

/**
 * 小屏键盘不该把输入区顶出屏幕：visualViewport 给的是「键盘之上还能画多高」，把它写成 CSS 变量交给
 * .cs-shell 当高度，输入区就永远贴在键盘上方（功能点7）。
 * 取到 0 一律不写（内置 webview 没挂载、页面在后台标签里都会给 0，实测过）——把 0px 塞进布局会把整页压平，
 * 那比少一个键盘优化严重得多：宁可不优化，也不能把页面弄没。
 */
function applyViewportHeight() {
  const vv = window.visualViewport
  const h = Math.round(vv ? vv.height : window.innerHeight)
  if (h > 0) document.documentElement.style.setProperty('--sb-viewport-height', `${h}px`)
  else document.documentElement.style.removeProperty('--sb-viewport-height')
}
const viewportHandler = () => {
  applyViewportHeight()
  if (started.value && !ended.value) scrollBottom()
}

const ENTRY_TIP_KEY = 'sb.entry.tip.v1'

function dismissEntryTip() {
  if (!entryTipVisible.value) return
  entryTipVisible.value = false
  try {
    localStorage.setItem(ENTRY_TIP_KEY, '1')
  } catch {
    /* 隐私模式写不进去：宁可下次再提示一次，也不该因此报错 */
  }
}

/** 只在该访客第一次见到这个入口时说一句话，用过/关掉就落本地标记，之后不再打扰（功能点1） */
function maybeShowEntryTip() {
  let seen = true
  try {
    seen = localStorage.getItem(ENTRY_TIP_KEY) === '1'
  } catch {
    seen = true // 读不到就当作已经提示过，「不打扰」优先于「一定看到」
  }
  if (seen || started.value) return
  entryTipVisible.value = true
  setTimeout(dismissEntryTip, 8000)
}

onMounted(async () => {
  window.addEventListener('offline', offlineHandler)
  window.addEventListener('online', onlineHandler)
  applyViewportHeight()
  if (window.visualViewport) window.visualViewport.addEventListener('resize', viewportHandler)
  else window.addEventListener('resize', viewportHandler)
  try {
    await loadStatus()
  } catch {
    onlineAgents.value = 0
  }
  maybeShowEntryTip()
  if (!started.value) return
  // 带着本地令牌回来：恢复上次会话，历史消息与排队状态都从服务端重取
  opened.value = true
  await loadHistoryMessages()
  if (ended.value) return
  if (queueing.value) refreshQueue()
  scrollBottom()
  connect()
})
</script>

<style scoped>
.chat {
  /* 对话框形态按即时通讯习惯收窄，过宽会让气泡一行拉到底、失去对话感 */
  max-width: 560px;
  /* 悬浮入口挂在面板上而不是视口上：面板是居中的窄卡片，贴视口右下角会跑到离内容很远的位置 */
  position: relative;
}

.entry-bubble {
  position: absolute;
  right: 16px;
  /* 让位给输入条：footer 高度会随文本域自增长，这里留出比一条输入栏更高的余量 */
  bottom: 108px;
  z-index: 5;
  display: inline-flex;
  align-items: center;
  gap: 7px;
  padding: 10px 16px;
  border: none;
  border-radius: var(--sb-radius-pill);
  background: var(--sb-color-primary);
  color: #fff;
  font-size: var(--sb-font-size-sm);
  font-family: inherit;
  cursor: pointer;
  box-shadow: var(--sb-shadow-md);
  transition: transform var(--sb-motion-duration-fast) var(--sb-motion-ease-standard),
    box-shadow var(--sb-motion-duration-fast) var(--sb-motion-ease-standard);
}

.entry-bubble:hover {
  transform: translateY(-1px);
  box-shadow: var(--sb-shadow-lg);
}

.entry-bubble:disabled {
  opacity: 0.7;
  cursor: default;
}

/* 客服不在线时不用主色「呼叫」语义，改警告色提示这是留言而不是接入 */
.entry-bubble--offline {
  background: var(--sb-color-warning);
}

.entry-tip {
  position: absolute;
  right: 16px;
  bottom: 164px;
  z-index: 5;
  display: flex;
  align-items: flex-start;
  gap: 8px;
  max-width: 260px;
  padding: 10px 12px;
  border-radius: var(--sb-radius-md);
  background: var(--sb-color-bg-card);
  border: 1px solid var(--sb-color-primary-border);
  box-shadow: var(--sb-shadow-md);
  color: var(--sb-color-text-secondary);
  font-size: var(--sb-font-size-xs);
  line-height: 1.5;
}

.entry-tip__close {
  flex: none;
  border: none;
  background: none;
  color: var(--sb-color-text-secondary);
  font-size: 15px;
  line-height: 1;
  cursor: pointer;
  padding: 0 2px;
}

/* 接入/留言入口：宽屏由右下角悬浮气泡承担，这颗占宽按钮默认隐藏，只给窄屏用，避免两个入口抢视线 */
.chat__entry-cta {
  display: none;
}

@media (max-width: 768px) {
  .entry-bubble,
  .entry-tip {
    display: none;
  }

  .chat__entry-cta {
    display: block;
    width: calc(100% - 24px);
    margin: 10px 12px 0;
    padding: 12px;
    border: none;
    border-radius: var(--sb-radius-md);
    background: var(--sb-color-primary);
    color: #fff;
    font-size: var(--sb-font-size-md);
    font-family: inherit;
    cursor: pointer;
  }

  .chat__entry-cta:disabled {
    opacity: 0.7;
  }
}

.cs-header__logo {
  width: 26px;
  height: 26px;
  flex: none;
}

.chat__who {
  display: flex;
  flex-direction: column;
  line-height: 1.25;
  min-width: 0;
}

.chat__sub {
  font-size: 11px;
}

.chat__queue {
  flex: none;
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  background: var(--sb-color-primary-bg);
  color: var(--sb-color-primary-active);
  font-size: var(--sb-font-size-xs);
}

.chat__queue--warn {
  background: var(--sb-color-warning-bg);
  color: var(--sb-color-warning);
}

/* 断网横幅夹在头部与消息区之间，左右留白与消息区对齐 */
.chat__offline {
  flex: none;
  margin: 8px 12px 0;
}

.chat__list {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 8px 16px 16px;
  -webkit-overflow-scrolling: touch;
  background: var(--sb-color-bg-page);
}

.chat__typing {
  font-size: var(--sb-font-size-xs);
  padding: 4px 2px;
}

/* 向前翻页的入口：居中一行小字按钮，不抢对话气泡的视觉重心。
   高度由 .chat-older 的 min-height 固定，加载态换成 spinner 也不会把列表顶下去一截 */
.chat__older {
  display: flex;
  justify-content: center;
  padding: 2px 0 8px;
}

.chat__older-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  border: none;
  background: none;
  padding: 2px 8px;
  font-size: var(--sb-font-size-xs);
  color: var(--sb-color-text-secondary);
  cursor: pointer;
}

.chat__older-btn:hover {
  color: var(--sb-color-primary);
}

.chat__older-btn:disabled {
  cursor: default;
}

/* 会话已建立但一条消息都没有：说清楚现在能做什么，而不是留一片空白（功能点5） */
.chat__empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 48px 0;
  color: var(--sb-color-text-secondary);
  font-size: var(--sb-font-size-sm);
}

.chat__empty-icon {
  font-size: 28px;
  color: var(--sb-color-text-muted);
}

@media (max-width: 768px) {
  .chat__list {
    padding: 8px 12px 12px;
  }
}
</style>
