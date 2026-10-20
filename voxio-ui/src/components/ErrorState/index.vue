<template>
  <div class="sb-error">
    <div class="sb-error__code">{{ code }}</div>
    <h2 class="sb-error__title">{{ title }}</h2>
    <p class="sb-error__hint">{{ hint }}</p>
    <div class="sb-error__actions">
      <el-button @click="back">返回上一页</el-button>
      <el-button type="primary" @click="home">回到首页</el-button>
    </div>
  </div>
</template>

<script setup>
/**
 * 统一错误页（阶段12 功能点7/28）
 *
 * 三个页面（401/404/500）共用这一块，差别只在文案：
 * 每个 code 都必须说明「发生了什么」和「下一步做什么」，而不是只把状态码甩在屏幕上。
 * 返回入口两颗都在：跳错了能退回去，也能直接回首页。
 */
const props = defineProps({
  code: {
    type: String,
    required: true
  },
  title: {
    type: String,
    required: true
  },
  hint: {
    type: String,
    default: ''
  }
})

const router = useRouter()

function back() {
  // 直接跳首页会丢掉用户原来所在的列表页，能退回上一页时先退
  if (window.history.length > 1) {
    router.back()
  } else {
    router.push('/index')
  }
}

function home() {
  router.push('/index')
}

defineExpose({ title: () => props.title })
</script>

<style scoped>
.sb-error {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 420px;
  padding: 40px 20px;
  text-align: center;
  animation: sb-error-in var(--sb-motion-duration-slow) var(--sb-motion-ease-enter);
}

@keyframes sb-error-in {
  from {
    opacity: 0;
    transform: translateY(var(--sb-motion-shift));
  }

  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.sb-error__code {
  font-size: 56px;
  font-weight: 600;
  letter-spacing: 2px;
  color: var(--sb-color-primary);
}

.sb-error__title {
  margin: 8px 0 0;
  font-size: var(--sb-font-size-lg);
  color: var(--sb-color-text);
}

.sb-error__hint {
  margin: 10px 0 0;
  max-width: 460px;
  font-size: var(--sb-font-size-sm);
  line-height: var(--sb-font-line-height);
  color: var(--sb-color-text-secondary);
}

.sb-error__actions {
  display: flex;
  gap: 12px;
  margin-top: 24px;
}
</style>
