import request from './request'
import { cachedOpen } from '@/utils/localCache'

/** 入口状态：服务时间、在线客服数、是否可留言（首屏用缓存，点「人工客服」时 force 取最新） */
export function getEntryStatus(force = false) {
  return cachedOpen('status', 'entry', () => request({ url: '/kf/open/status', method: 'get' }), force)
}

/** 创建或恢复会话，返回 sessionId / sessionNo / status / visitorToken / position / agentNick */
export function createSession(data) {
  return request({ url: '/kf/open/session', method: 'post', data })
}

/** 拉取历史消息（beforeSeq 向前翻页），拉取即视为已读 */
export function getSessionMessages(params) {
  return request({ url: '/kf/open/session/messages', method: 'get', params })
}

/** 访客结束会话 */
export function endSession(sessionId, reason) {
  return request({ url: '/kf/open/session/end', method: 'post', data: { sessionId, reason } })
}

/** 提交离线留言 */
export function submitOffline(data) {
  return request({ url: '/kf/open/offline', method: 'post', data })
}
