import request from '@/utils/request'

// 查询客服账号列表
export function listAgent(query) {
  return request({
    url: '/kf/agent/list',
    method: 'get',
    params: query
  })
}

// 查询客服账号详细
export function getAgent(agentId) {
  return request({
    url: '/kf/agent/' + agentId,
    method: 'get'
  })
}

// 新增客服账号
export function addAgent(data) {
  return request({
    url: '/kf/agent',
    method: 'post',
    data: data
  })
}

// 修改客服账号
export function updateAgent(data) {
  return request({
    url: '/kf/agent',
    method: 'put',
    data: data
  })
}

// 删除客服账号
export function delAgent(agentId) {
  return request({
    url: '/kf/agent/' + agentId,
    method: 'delete'
  })
}

// 修改客服账号状态（停用会同步强制下线）
export function changeAgentStatus(data) {
  return request({
    url: '/kf/agent/changeStatus',
    method: 'put',
    data: data
  })
}

// 重置客服登录密码
export function resetAgentPwd(data) {
  return request({
    url: '/kf/agent/resetPwd',
    method: 'put',
    data: data
  })
}

// 强制客服下线
export function forceOfflineAgent(agentId) {
  return request({
    url: '/kf/agent/forceOffline/' + agentId,
    method: 'put'
  })
}
