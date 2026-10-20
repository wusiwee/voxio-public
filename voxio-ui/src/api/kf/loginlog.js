import request from '@/utils/request'

// 客服登录日志列表（只读，成功与失败都在这一张表里）
export function listLoginLog(query) {
  return request({ url: '/kf/loginlog/list', method: 'get', params: query })
}
