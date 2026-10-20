import request from '@/utils/request'

// 离线留言列表
export function listOffline(query) {
  return request({ url: '/kf/offline/list', method: 'get', params: query })
}

// 离线留言详情
export function getOffline(msgId) {
  return request({ url: '/kf/offline/' + msgId, method: 'get' })
}

// 留言转工单
export function offlineToTicket(msgId, data) {
  return request({ url: '/kf/offline/' + msgId + '/toTicket', method: 'post', data: data })
}

// 关闭留言
export function closeOffline(msgId) {
  return request({ url: '/kf/offline/' + msgId + '/close', method: 'put' })
}
