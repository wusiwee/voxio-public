import request from '@/utils/request'

// 查询操作日志列表
// 阶段10.5 P0-3：审计留痕防删除，删除与清空接口已从后端摘除，前端不再封装
export function list(query) {
  return request({
    url: '/monitor/operlog/list',
    method: 'get',
    params: query
  })
}
