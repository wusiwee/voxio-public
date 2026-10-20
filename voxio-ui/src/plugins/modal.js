import { ElMessage, ElMessageBox, ElNotification, ElLoading } from 'element-plus'

/**
 * 阶段12 功能点9/10/21：提示与确认的唯一出口。
 *
 * 三条口径（与访客端逐字一致）：
 * 1. 文案说清「发生了什么 + 下一步做什么」，不出现「系统异常」「操作失败！」这类没有信息量的措辞；
 * 2. 位置统一顶部居中，四种类型（info/success/warning/error）共用同一进入动效与时长；
 * 3. 危险操作一律走 confirmDanger，标题写动作、正文写后果，主按钮是执行动作的那一颗。
 */

let loadingInstance

const DURATION = 3000
const OFFSET = 24

const toast = (type, content) => ElMessage({
  message: content,
  type,
  duration: DURATION,
  offset: OFFSET,
  // 同一句话连发多次只留一条，批量操作时不会被自己刷屏
  grouping: true
})

export default {
  // 消息提示
  msg(content) {
    toast('info', content)
  },
  // 错误消息
  msgError(content) {
    toast('error', content)
  },
  // 成功消息
  msgSuccess(content) {
    toast('success', content)
  },
  // 警告消息
  msgWarning(content) {
    toast('warning', content)
  },
  // 弹出提示
  alert(content) {
    ElMessageBox.alert(content, '提示')
  },
  // 错误提示
  alertError(content) {
    ElMessageBox.alert(content, '出错了', { type: 'error' })
  },
  // 成功提示
  alertSuccess(content) {
    ElMessageBox.alert(content, '完成', { type: 'success' })
  },
  // 警告提示
  alertWarning(content) {
    ElMessageBox.alert(content, '请注意', { type: 'warning' })
  },
  // 通知提示
  notify(content) {
    ElNotification.info({ message: content, duration: 4000 })
  },
  // 错误通知
  notifyError(content) {
    ElNotification.error({ message: content, duration: 4000 })
  },
  // 成功通知
  notifySuccess(content) {
    ElNotification.success({ message: content, duration: 4000 })
  },
  // 警告通知
  notifyWarning(content) {
    ElNotification.warning({ message: content, duration: 4000 })
  },
  /**
   * 普通确认：标题说明要做什么，正文说明会发生什么。
   * closeOnClickModal=false —— 误点遮罩不该把一个要改数据的动作确认掉。
   */
  confirm(content, options = {}) {
    return ElMessageBox.confirm(content, options.title || '请确认', {
      confirmButtonText: options.okText || '确定',
      cancelButtonText: '取消',
      type: 'warning',
      closeOnClickModal: false,
      ...options
    })
  },
  /**
   * 危险操作二次确认（功能点10）：删除、结束会话、重置密码这一类
   * 点下去就退不回去的动作，必须写明后果，主按钮用动作本身而不是含糊的「确定」。
   */
  confirmDanger(content, options = {}) {
    return this.confirm(content, {
      title: options.title || '确认操作',
      okText: options.okText || '继续',
      okButtonClass: 'el-button--danger',
      type: 'error',
      ...options
    })
  },
  // 输入窗体
  prompt(content) {
    return ElMessageBox.prompt(content, '系统提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消'
    })
  },
  // 全局 loading（仅用于「整页数据在等」的场景，列表二次加载请用 v-loading）
  loading(content) {
    loadingInstance = ElLoading.service({
      lock: true,
      text: content,
      background: 'rgba(255, 255, 255, 0.72)'
    })
    return loadingInstance
  },
  closeLoading() {
    if (loadingInstance) loadingInstance.close()
  }
}
