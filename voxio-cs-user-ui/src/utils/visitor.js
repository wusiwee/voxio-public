/**
 * 访客身份本地存储（功能点2：匿名 visitorNo，可恢复会话，不做登录）
 *
 * visitorNo 一旦生成就长期保留，用于服务端识别"同一个人回来继续咨询"；
 * visitorToken 是会话级凭证，过期或会话结束后重新签发。
 */
const KEY_VISITOR_NO = 'voxio_visitor_no'
const KEY_TOKEN = 'voxio_visitor_token'
const KEY_SESSION = 'voxio_visitor_session'
const KEY_SESSION_NO = 'voxio_visitor_session_no'
const KEY_TOKEN_EXPIRE = 'voxio_visitor_token_exp'
const KEY_AGENT_NICK = 'voxio_visitor_agent_nick'

function randomId() {
  const buffer = new Uint8Array(8)
  crypto.getRandomValues(buffer)
  return Array.from(buffer, (b) => b.toString(16).padStart(2, '0')).join('')
}

export function getVisitorNo() {
  let value = localStorage.getItem(KEY_VISITOR_NO)
  if (!value) {
    value = 'v-' + Date.now().toString(36) + '-' + randomId()
    localStorage.setItem(KEY_VISITOR_NO, value)
  }
  return value
}

export function getToken() {
  const token = localStorage.getItem(KEY_TOKEN)
  if (!token) return ''
  const expire = Number(localStorage.getItem(KEY_TOKEN_EXPIRE) || 0)
  // 令牌留 60s 余量，避免刚好在边界上被判过期
  if (expire && Date.now() > expire - 60000) {
    clearSession()
    return ''
  }
  return token
}

export function getSessionId() {
  return localStorage.getItem(KEY_SESSION) || ''
}

/** tokenMinutes 与后端 kf.visitor.token.expireTime 对齐，用于本地预判过期 */
export function saveSession(data, tokenMinutes = 1440) {
  if (data?.visitorToken) {
    localStorage.setItem(KEY_TOKEN, data.visitorToken)
    localStorage.setItem(KEY_TOKEN_EXPIRE, String(Date.now() + tokenMinutes * 60000))
  }
  if (data?.sessionId) {
    localStorage.setItem(KEY_SESSION, String(data.sessionId))
  }
  if (data?.sessionNo) {
    localStorage.setItem(KEY_SESSION_NO, String(data.sessionNo))
  }
  if (data?.agentNick) {
    localStorage.setItem(KEY_AGENT_NICK, String(data.agentNick))
  } else if (data?.status === '0') {
    localStorage.removeItem(KEY_AGENT_NICK)
  }
}

export function getAgentNick() {
  return localStorage.getItem(KEY_AGENT_NICK) || ''
}

export function clearSession() {
  localStorage.removeItem(KEY_TOKEN)
  localStorage.removeItem(KEY_SESSION)
  localStorage.removeItem(KEY_SESSION_NO)
  localStorage.removeItem(KEY_TOKEN_EXPIRE)
  localStorage.removeItem(KEY_AGENT_NICK)
}
