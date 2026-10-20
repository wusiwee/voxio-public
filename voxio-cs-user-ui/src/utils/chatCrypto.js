/**
 * 访客端网关加密层
 *
 * 与后端 ChatCryptoService / ChatFrameMac 同一套契约（加密默认关闭，总开关见 voxio.crypto.enabled）：
 *   AUTH_OK 里带回 encryptEnabled/algo/keyVersion/sm2PublicKey，据此建立连接级 HMAC 密钥（由令牌派生）
 *   发 CHAT 前先做一轮 KEY_EXCHANGE：本端随机生成 SM4 会话密钥，用服务端 SM2 公钥加密上送；
 *   若会话已有密钥（客服先协商），服务端会用本端的临时 SM2 公钥把既有密钥回传，两端最终收敛到同一把
 *   帧头字段：encrypt/algo/hmac/keyVersion，正文 packed = base64(iv12 || ciphertext || tag16)
 */
import {
  controlKeyHex,
  randomKeyHex,
  sm2Encrypt,
  sm2DecryptText,
  sm2GenerateKeyPair,
  sm4GcmEncrypt,
  sm4GcmDecryptText,
  signFrame,
  verifyFrame
} from './sm.js'

const ENCRYPT_FLAG = 'sm4-gcm'
const KEY_EXCHANGE_TIMEOUT = 8000

export function createChatCrypto() {
  let localSeq = 0
  /** sid -> { keyHex, keyVersion } */
  const keys = new Map()
  /** KEY_EXCHANGE 的 seq -> resolve */
  const waiters = new Map()
  let state = { enabled: false, algo: 'SM4-GCM', keyVersion: 1, serverPublicKey: null, controlKey: null }

  function configure(authBody, token) {
    const body = authBody || {}
    state = {
      enabled: body.encryptEnabled === true,
      algo: body.algo || 'SM4-GCM',
      keyVersion: Number(body.keyVersion || 1),
      serverPublicKey: body.sm2PublicKey || null,
      controlKey: body.encryptEnabled === true ? controlKeyHex(token) : null
    }
    // 重连后线路密钥要重新协商，旧连接上的密钥不作复用
    keys.clear()
  }

  function seal(frame) {
    if (!state.enabled) return frame
    const entry = frame.sid == null ? null : keys.get(String(frame.sid))
    const content = frame.body && frame.body.content
    if (frame.cmd === 'CHAT' && typeof content === 'string' && content.length > 0 && entry) {
      frame.body.content = sm4GcmEncrypt(entry.keyHex, content)
      frame.encrypt = ENCRYPT_FLAG
      frame.algo = state.algo
      frame.keyVersion = entry.keyVersion
    }
    if (state.controlKey) {
      frame.hmac = signFrame(state.controlKey, frame)
    }
    return frame
  }

  function open(frame) {
    if (!state.enabled) return frame
    if (state.controlKey && frame.hmac && !verifyFrame(state.controlKey, frame, frame.hmac)) {
      throw new Error('下行帧完整性校验失败：' + frame.cmd)
    }
    const entry = frame.sid == null ? null : keys.get(String(frame.sid))
    if (frame.encrypt === ENCRYPT_FLAG && entry && frame.body && typeof frame.body.content === 'string') {
      frame.body.content = sm4GcmDecryptText(entry.keyHex, frame.body.content)
    }
    return frame
  }

  /** 是否为密钥协商的回包；是则唤醒等待方并返回 true，调用方不必再按普通 ACK 处理 */
  function takeAck(frame) {
    const body = frame && frame.body
    if (!body || body.action !== 'keyExchange') return false
    const waiter = waiters.get(String(frame.seq))
    if (waiter) {
      waiters.delete(String(frame.seq))
      waiter(body)
    }
    return true
  }

  async function ensureKey(sid, sendFrame) {
    if (!state.enabled || sid == null) return false
    const key = String(sid)
    if (keys.has(key)) return true
    if (!state.serverPublicKey) throw new Error('缺少服务端 SM2 公钥，无法协商会话密钥')
    const keyHex = randomKeyHex()
    const pair = sm2GenerateKeyPair()
    const frame = {
      v: 1,
      cmd: 'KEY_EXCHANGE',
      seq: ++localSeq,
      sid: Number(sid),
      ts: Date.now(),
      body: { cipher: sm2Encrypt(state.serverPublicKey, keyHex), clientPublicKey: pair.publicKey }
    }
    const ack = await new Promise((resolve, reject) => {
      waiters.set(String(frame.seq), resolve)
      setTimeout(() => {
        if (waiters.delete(String(frame.seq))) reject(new Error('会话密钥协商超时'))
      }, KEY_EXCHANGE_TIMEOUT)
      sendFrame(frame)
    })
    // 服务端沿用既有会话密钥时会把密钥加密回传，本端生成的那把随即作废
    const effective = ack.cipher ? sm2DecryptText(pair.privateKey, ack.cipher) : keyHex
    keys.set(key, { keyHex: effective, keyVersion: Number(ack.keyVersion || state.keyVersion) })
    return true
  }

  return {
    configure,
    /**
     * 新连接建立前必须回到「未配置」状态：AUTH_OK 本身是未签名的（签名参数正是它下发的），
     * 若沿用上一条连接的 enabled/controlKey，重连时的 AUTH_OK 会被 open() 当成验签失败丢掉，
     * 于是 authed 永远置不上，访客端卡在「连接中」只能刷新页面（阶段15 全链路实测复现）。
     */
    reset() {
      state = { enabled: false, algo: 'SM4-GCM', keyVersion: 1, serverPublicKey: null, controlKey: null }
      // 线路密钥与协商状态都属于上一条连接，新连接一律重新协商；
      // waiters 不清：ensureKey 的超时靠 waiters.delete 的返回值判断是否 reject，清掉会让那条 Promise 永挂
      keys.clear()
    },
    seal,
    open,
    takeAck,
    ensureKey,
    get enabled() {
      return state.enabled
    },
    hasKey: (sid) => sid != null && keys.has(String(sid)),
    /** 会话结束或切换会话时清掉，避免同一把密钥跨会话复用 */
    forget: (sid) => keys.delete(String(sid)),
    nextSeq: () => ++localSeq
  }
}
