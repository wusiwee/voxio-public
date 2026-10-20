import request from '@/utils/request'

// 查询客服参数列表
export function listKfConfig(query) {
  return request({ url: '/kf/config/list', method: 'get', params: query })
}

// 按键名查询参数值
export function getKfConfigKey(configKey) {
  return request({ url: '/kf/config/configKey/' + configKey, method: 'get' })
}

// 参数详情
export function getKfConfig(configId) {
  return request({ url: '/kf/config/' + configId, method: 'get' })
}

// 新增参数
export function addKfConfig(data) {
  return request({ url: '/kf/config', method: 'post', data: data })
}

// 修改参数
export function updateKfConfig(data) {
  return request({ url: '/kf/config', method: 'put', data: data })
}

// 删除参数
export function delKfConfig(configId) {
  return request({ url: '/kf/config/' + configId, method: 'delete' })
}

// 刷新缓存
export function refreshKfConfigCache() {
  return request({ url: '/kf/config/refreshCache', method: 'delete' })
}
