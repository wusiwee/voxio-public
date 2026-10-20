import request from '@/utils/request'

// 会话监控列表（只读）
export function listMonitorSessions(query) {
  return request({ url: '/kf/monitor/sessions', method: 'get', params: query })
}

// 客服在线状态与实时负载
export function listMonitorAgents(query) {
  return request({ url: '/kf/monitor/agents', method: 'get', params: query })
}

// 会话详情（含访客与最近消息）
export function getMonitorSession(sessionId) {
  return request({ url: '/kf/monitor/session/' + sessionId, method: 'get' })
}

// 人工分配排队中的会话给指定客服
export function assignMonitorSession(sessionId, agentId) {
  return request({ url: '/kf/monitor/session/' + sessionId + '/assign/' + agentId, method: 'put' })
}
