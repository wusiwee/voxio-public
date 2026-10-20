import request from '@/utils/request'

// 登录日志查询
// 阶段10.5 P0-3：审计留痕防删除，删除与清空接口已从后端摘除，前端不再封装
export function list(query) {
  return request({
    url: '/monitor/logininfor/list',
    method: 'get',
    params: query
  })
}

// 解锁用户（安全处置动作，不改动日志本身）
export function unlockLogininfor(userName) {
  return request({
    url: '/monitor/logininfor/unlock/' + userName,
    method: 'get'
  })
}
