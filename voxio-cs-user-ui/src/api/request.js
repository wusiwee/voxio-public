import axios from 'axios'
import { ElMessage } from 'element-plus'
import { clearSession, getVisitorNo, getToken, saveSession } from '@/utils/visitor'

const service = axios.create({
  baseURL: import.meta.env.VITE_APP_BASE_API,
  timeout: 20000
})

/**
 * 访客令牌只有「创建会话」这一个签发入口，而首屏的留言本来就排在建会话之前：
 * 无客服在线时点「留言」，此刻既没有会话也没有令牌，直接提交只会拿回一句「访客身份已失效」，
 * 而访客从没被要求登录过，这句话他既看不懂也解决不了。缺身份时先补一次签发再发原请求。
 *
 * 只覆盖需要令牌的开放接口。/kf/open/status 这类入口探测仍按原样匿名调用，
 * 否则访客只是打开页面就会凭空多出一条排队会话、把排队人数顶高。
 * 后端闸门一个字节都没放宽：不带令牌的 /kf/open/offline 仍然 401。
 */
const IDENTITY_URL = '/kf/open/session'
const NEEDS_IDENTITY = /^\/kf\/open\/(offline|session\/(?:messages|end))/

// 留言框与建会话可能同一帧各发一次，两边都发现没令牌时只补签一次
let pendingIdentity = null

function bootstrapIdentity() {
  if (!pendingIdentity) {
    pendingIdentity = service
      .post(IDENTITY_URL, {
        visitorNo: getVisitorNo(),
        nickname: '访客',
        sourcePage: location.href,
        referrer: document.referrer || ''
      }, { silent: true })
      .then((res) => saveSession(res.data || {}))
      .catch(() => {})
      .finally(() => {
        pendingIdentity = null
      })
  }
  return pendingIdentity
}

service.interceptors.request.use(async (config) => {
  if (NEEDS_IDENTITY.test(config.url || '') && !getToken()) {
    await bootstrapIdentity()
  }
  const token = getToken()
  if (token) {
    config.headers.Authorization = 'Bearer ' + token
  }
  return config
})

service.interceptors.response.use(
  async (response) => {
    const data = response.data
    const cfg = response.config || {}
    // 若依风格返回：{code, msg, ...}，非 200 一律按失败处理
    if (data && data.code !== undefined && data.code !== 200) {
      if (data.code === 401) {
        clearSession()
        // 令牌在服务端已经作废、本地却还没到点（换 jar 重启、清 Redis、隔了一夜），
        // 这时访客会白点一次「提交留言」才知道能用：补签一次并重放这一个请求，只重放一次。
        if (NEEDS_IDENTITY.test(cfg.url || '') && !cfg.identityRetried) {
          await bootstrapIdentity()
          const fresh = getToken()
          if (fresh) {
            return service.request({
              ...cfg,
              headers: { ...cfg.headers, Authorization: 'Bearer ' + fresh },
              identityRetried: true
            })
          }
        }
      }
      if (!cfg.silent) {
        ElMessage.error(data.msg || '请求失败')
      }
      return Promise.reject(Object.assign(new Error(data.msg || '请求失败'), { code: data.code, silent: cfg.silent }))
    }
    return data
  },
  (error) => {
    const silent = (error.config || {}).silent
    if (!silent) {
      ElMessage.error(error.code === 'ECONNABORTED' ? '请求超时，请稍后再试' : '网络异常，请检查网络后重试')
    }
    return Promise.reject(error)
  }
)

export default service
