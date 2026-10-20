import { ref, computed } from 'vue'

/**
 * 有没有写操作正在飞（阶段12 功能点4/13）
 *
 * 提交类按钮统一绑这一把锁：点下去立刻进 loading 并禁用，请求回来才放开，
 * 连点第二次根本发不出去 —— 比每个弹窗各自维护 isXxxSubmitting 更难写漏。
 * 读请求（GET）不计入：翻页与查询不该把「保存」按钮锁成灰色。
 */
const pending = ref(0)

export const writeBusy = computed(() => pending.value > 0)

export function beginWrite() {
  pending.value++
}

export function endWrite() {
  if (pending.value > 0) pending.value--
}
