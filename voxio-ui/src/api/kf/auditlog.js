import request from '@/utils/request'

// 业务审计留痕列表（只增不删的表，这里只有查询；删除/修改既无按钮也无接口）
export function listAuditLog(query) {
  return request({ url: '/kf/auditlog/list', method: 'get', params: query })
}
