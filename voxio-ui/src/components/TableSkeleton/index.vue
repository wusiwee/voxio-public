<template>
  <div class="sb-skeleton" :aria-busy="true" aria-live="polite" :aria-label="label">
    <div class="sb-skeleton__head">
      <span v-for="w in head" :key="'h' + w" class="sb-skeleton__bar" :style="{ width: w }" />
    </div>
    <div v-for="row in rows" :key="row" class="sb-skeleton__row">
      <span v-for="(w, i) in cols" :key="i" class="sb-skeleton__bar" :style="{ width: w }" />
    </div>
  </div>
</template>

<script setup>
/**
 * 列表首屏骨架（阶段12 功能点1）：只在「第一次进页面」时出现，
 * 之后的翻页/查询仍用表格自带的轻量 v-loading，避免整屏遮罩打断视线。
 */
const props = defineProps({
  rows: {
    type: Number,
    default: 6
  },
  label: {
    type: String,
    default: '数据加载中'
  },
  // 列宽用百分比表达：表格列是固定宽度的时候，骨架只是「有内容在路上」的暗示，不追求逐列对齐
  widths: {
    type: Array,
    default: null
  }
})

const DEFAULT_COLS = ['14%', '22%', '18%', '12%', '20%', '10%']
const cols = computed(() => props.widths && props.widths.length ? props.widths : DEFAULT_COLS)
const head = ['16%', '24%', '18%', '14%', '20%']
</script>

<style scoped>
.sb-skeleton {
  padding: 4px 0;
}

.sb-skeleton__head,
.sb-skeleton__row {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 13px 0;
  border-bottom: 1px solid var(--sb-color-border);
}

.sb-skeleton__head {
  background: var(--sb-color-bg-table-header);
  border-bottom-color: var(--sb-color-border);
  padding-left: 12px;
}

.sb-skeleton__head .sb-skeleton__bar {
  height: 10px;
  opacity: .55;
}

.sb-skeleton__row {
  padding-left: 12px;
}

.sb-skeleton__bar {
  height: 14px;
  border-radius: var(--sb-radius-sm);
  background: linear-gradient(90deg,
      var(--sb-color-bg-hover) 0%,
      var(--sb-color-border) 40%,
      var(--sb-color-bg-hover) 80%);
  background-size: 300% 100%;
  animation: sb-skeleton-wave var(--sb-motion-skeleton-duration) var(--sb-motion-ease-standard) infinite;
}

@keyframes sb-skeleton-wave {
  from {
    background-position: 100% 0;
  }

  to {
    background-position: -100% 0;
  }
}

/* 系统设置里「减少动效」的用户不该看到一直闪的骨架 */
@media (prefers-reduced-motion: reduce) {
  .sb-skeleton__bar {
    animation: none;
  }
}
</style>
