import request from '@/utils/request'

// 查询业务设置
export function getBusiness() {
  return request({ url: '/kf/settings/business', method: 'get' })
}

// 保存业务设置
export function updateBusiness(data) {
  return request({ url: '/kf/settings/business', method: 'put', data: data })
}
