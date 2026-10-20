import request from '@/utils/request'

// 工单列表
export function listTicket(query) {
  return request({ url: '/kf/ticket/list', method: 'get', params: query })
}

// 工单详情（含流转与回复记录、关联会话）
export function getTicket(ticketId) {
  return request({ url: '/kf/ticket/' + ticketId, method: 'get' })
}

// 新建工单
export function addTicket(data) {
  return request({ url: '/kf/ticket', method: 'post', data: data })
}

// 修改工单基础信息
export function updateTicket(data) {
  return request({ url: '/kf/ticket', method: 'put', data: data })
}

// 工单状态流转
export function changeTicketStatus(ticketId, status, note) {
  return request({ url: '/kf/ticket/' + ticketId + '/status', method: 'put', data: { status: status, note: note } })
}

// 追加回复（replyType 1对外回复 3内部备注）
export function addTicketReply(ticketId, content, replyType) {
  return request({ url: '/kf/ticket/' + ticketId + '/reply', method: 'post', data: { content: content, replyType: replyType } })
}

// 删除工单（仅已解决/已关闭）
export function delTicket(ticketId) {
  return request({ url: '/kf/ticket/' + ticketId, method: 'delete' })
}

// 手动同步工单到第三方系统
export function pushTicket(ticketId) {
  return request({ url: '/kf/ticket/' + ticketId + '/push', method: 'post' })
}
