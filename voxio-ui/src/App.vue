<template>
  <!-- 键盘用户跳过侧栏与标签栏，直达内容区（阶段12 功能点26） -->
  <a class="sb-skip-link" href="#app-main">跳到主内容</a>
  <router-view />
</template>

<script setup>
import useSettingsStore from '@/store/modules/settings'
import { handleThemeStyle } from '@/utils/theme'

onMounted(() => {
  nextTick(() => {
    // 初始化主题样式
    handleThemeStyle(useSettingsStore().theme)
  })
})
</script>
