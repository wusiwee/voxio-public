<template>
  <transition name="fade">
    <div v-if="!online" class="sb-offline-bar" role="status" aria-live="polite">
      <el-icon>
        <Warning />
      </el-icon>
      <span>网络已断开，页面内容可能不是最新的。恢复连接后这条提示会自动消失。</span>
    </div>
  </transition>
</template>

<script setup>
/**
 * 断网提示条（阶段12 功能点8）
 *
 * 只做一件事：浏览器说没网了就在内容区顶上挂一条，回来了自动收掉。
 * 不拦截请求、不弹模态 —— 弱网时用户最需要的是「知道现在是什么状况」，而不是被一个弹窗挡住。
 * 访客端有对应实现（useChatSession + Chat.vue），两边文案逐字一致。
 */
import { onMounted, onUnmounted, ref } from 'vue'
import { Warning } from '@element-plus/icons-vue'

const online = ref(navigator.onLine !== false)

const goOnline = () => { online.value = true }
const goOffline = () => { online.value = false }

onMounted(() => {
  window.addEventListener('online', goOnline)
  window.addEventListener('offline', goOffline)
})

onUnmounted(() => {
  window.removeEventListener('online', goOnline)
  window.removeEventListener('offline', goOffline)
})
</script>
