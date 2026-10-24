import axios from 'axios'
import { beginWrite, endWrite } from '@/utils/writeBusy'
import { ElNotification , ElMessageBox, ElMessage, ElLoading } from 'element-plus'
import { getToken } from '@/utils/auth'
import errorCode from '@/utils/errorCode'
import { tansParams, blobValidate } from '@/utils/voxio'
import cache from '@/plugins/cache'
import { saveAs } from 'file-saver'
import useUserStore from '@/store/modules/user'

let downloadLoadingInstance
// 是否显示重新登录
export let isRelogin = { show: false }

axios.defaults.headers['Content-Type'] = 'application/json;charset=utf-8'
// 创建axios实例
const service = axios.create({
  // axios中请求配置有baseURL选项，表示请求URL公共部分
  baseURL: import.meta.env.VITE_APP_BASE_API,
  // 超时
  timeout: 10000
})

/*
 * 阶段11 功能点17：相同请求合并 + 页面切换取消未完成请求
 *
 * 后台一次渲染里经常有 2~3 处请求同一份数据（同一个列表页的字典、统计卡与表格都拉同一批接口），
 * 浏览器到公网后端的每一次往返都要几十毫秒，重复发就是白白多等一轮。这里在 adapter 层做两件事：
 *   1) 同一个 GET 还在飞行中时，第二个调用者共用同一次往返；
 *   2) 每个请求挂一个 AbortController，路由切换时把上一个页面没跑完的 GET 取消掉，
 *      既腾出连接，也避免「切回来闪一下旧数据」。
 * 只处理 GET：POST/PUT 有副作用，合并或取消都可能让业务误判为没提交成功。
 * 需要退出合并的调用（例如轮询必须拿到最新一次响应）传 headers: { 'X-Dedupe': 'off' }。
 */
const httpAdapter = axios.getAdapter(axios.defaults.adapter)
const inflightGet = new Map()
const pendingControllers = new Set()

service.defaults.adapter = config => {
  const method = (config.method || 'get').toLowerCase()
  const controller = new AbortController()
  config.signal = controller.signal
  pendingControllers.add(controller)
  const release = () => pendingControllers.delete(controller)

  const canDedupe = method === 'get' && !config.responseType
    && String(config.headers?.['X-Dedupe'] || '') !== 'off'
  if (!canDedupe) {
    return httpAdapter(config).finally(release)
  }
  const key = (config.baseURL || '') + (config.url || '')
  const running = inflightGet.get(key)
  if (running) {
    return running
  }
  const once = httpAdapter(config).finally(() => {
    inflightGet.delete(key)
    release()
  })
  inflightGet.set(key, once)
  return once
}

/** 路由切换时取消上一页尚未完成的请求 */
export function cancelPendingRequests() {
  pendingControllers.forEach(controller => controller.abort())
  pendingControllers.clear()
}

// request拦截器
service.interceptors.request.use(config => {
  // 写操作计数：提交类按钮的 loading / 防重复由它驱动（阶段12 功能点4/13）
  const isWrite = String(config.method || 'get').toLowerCase() !== 'get'
  if (isWrite) {
    config.__sbWrite = true
    beginWrite()
  }
  // 是否需要设置 token
  const isToken = (config.headers || {}).isToken === false
  // 是否需要防止数据重复提交
  const isRepeatSubmit = (config.headers || {}).repeatSubmit === false
  // 间隔时间(ms)，小于此时间视为重复提交
  const interval = (config.headers || {}).interval || 1000
  if (getToken() && !isToken) {
    config.headers['Authorization'] = 'Bearer ' + getToken() // 让每个请求携带自定义token 请根据实际情况自行修改
  }
  // get请求映射params参数
  if (config.method === 'get' && config.params) {
    let url = config.url + '?' + tansParams(config.params)
    url = url.slice(0, -1)
    config.params = {}
    config.url = url
  }
  if (!isRepeatSubmit && (config.method === 'post' || config.method === 'put')) {
    const requestObj = {
      url: config.url,
      data: typeof config.data === 'object' ? JSON.stringify(config.data) : config.data,
      time: new Date().getTime()
    }
    const requestSize = Object.keys(JSON.stringify(requestObj)).length // 请求数据大小
    const limitSize = 5 * 1024 * 1024 // 限制存放数据5M
    if (requestSize >= limitSize) {
      console.warn(`[${config.url}]: ` + '请求数据大小超出允许的5M限制，无法进行防重复提交验证。')
      return config
    }
    const sessionObj = cache.session.getJSON('sessionObj')
    if (sessionObj === undefined || sessionObj === null || sessionObj === '') {
      cache.session.setJSON('sessionObj', requestObj)
    } else {
      const s_url = sessionObj.url                // 请求地址
      const s_data = sessionObj.data              // 请求数据
      const s_time = sessionObj.time              // 请求时间
      if (s_data === requestObj.data && requestObj.time - s_time < interval && s_url === requestObj.url) {
        const message = '数据正在处理，请勿重复提交'
        console.warn(`[${s_url}]: ` + message)
        return Promise.reject(new Error(message))
      } else {
        cache.session.setJSON('sessionObj', requestObj)
      }
    }
  }
  return config
}, error => {
    // 少了 return 时这个 reject 只是被丢弃的 Promise：拦截器返回 undefined，
    // 请求继续往下走并在别处以更奇怪的方式失败。控制台也不该承接生产错误。
    return Promise.reject(error)
})

// 响应拦截器
service.interceptors.response.use(res => {
    // 写操作回来（成功或失败）都要放开提交按钮的锁，否则一次报错就把按钮永久按住
    if (res.config && res.config.__sbWrite) endWrite()
    // 未设置状态码则默认成功状态
    const code = res.data.code || 200
    // 获取错误信息
    const msg = errorCode[code] || res.data.msg || errorCode['default']
    // 二进制数据则直接返回
    if (res.request.responseType ===  'blob' || res.request.responseType ===  'arraybuffer') {
      return res.data
    }
    if (code === 401) {
      if (!isRelogin.show) {
        isRelogin.show = true
        ElMessageBox.confirm('登录状态已过期，您可以继续留在该页面，或者重新登录', '系统提示', { confirmButtonText: '重新登录', cancelButtonText: '取消', type: 'warning' }).then(() => {
          isRelogin.show = false
          useUserStore().logOut().then(() => {
            location.href = import.meta.env.BASE_URL + 'index'
          })
      }).catch(() => {
        isRelogin.show = false
      })
    }
      return Promise.reject('无效的会话，或者会话已过期，请重新登录。')
    } else if (code === 500) {
      ElMessage({ message: msg, type: 'error' })
      return Promise.reject(new Error(msg))
    } else if (code === 601) {
      ElMessage({ message: msg, type: 'warning' })
      return Promise.reject(new Error(msg))
    } else if (code !== 200) {
      ElNotification.error({ title: msg })
      return Promise.reject('error')
    } else {
      return  Promise.resolve(res.data)
    }
  },
  error => {
    if (error.config && error.config.__sbWrite) endWrite()
    // 页面切换主动取消的请求不是故障：静默丢弃，别弹「系统接口请求超时」这种误导提示
    if (axios.isCancel(error)) {
      return Promise.reject(error)
    }
    // 下面已经把错误翻译成用户能看懂的提示并弹出来了，不需要再往控制台丢一份
    let { message } = error
    if (message == "Network Error") {
      message = "后端接口连接异常"
    } else if (message.includes("timeout")) {
      message = "系统接口请求超时"
    } else if (message.includes("Request failed with status code")) {
      message = "系统接口" + message.slice(-3) + "异常"
    }
    ElMessage({ message: message, type: 'error', duration: 5 * 1000 })
    return Promise.reject(error)
  }
)

// 通用下载方法
export function download(url, params, filename, config) {
  downloadLoadingInstance = ElLoading.service({ text: "正在下载数据，请稍候", background: "rgba(0, 0, 0, 0.7)", })
  return service.post(url, params, {
    transformRequest: [(params) => { return tansParams(params) }],
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    responseType: 'blob',
    ...config
  }).then(async (data) => {
    const isBlob = blobValidate(data)
    if (isBlob) {
      const blob = new Blob([data])
      saveAs(blob, filename)
    } else {
      const resText = await data.text()
      const rspObj = JSON.parse(resText)
      const errMsg = errorCode[rspObj.code] || rspObj.msg || errorCode['default']
      ElMessage.error(errMsg)
    }
    downloadLoadingInstance.close()
  }).catch((r) => {
    console.error(r)
    ElMessage.error('下载文件出现错误，请联系管理员！')
    downloadLoadingInstance.close()
  })
}

export default service
