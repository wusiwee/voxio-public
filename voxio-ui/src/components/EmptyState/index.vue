<template>
  <transition name="fade">
    <div class="sb-empty">
      <!-- 图标语义与三端一致（功能点22）：会话=ChatDotRound、工单=Tickets、通用空=Inbox 图形由 el-empty 自带 -->
      <el-empty v-if="!icon" :image-size="72" :description="description" />
      <template v-else>
        <el-icon class="sb-empty-icon" :size="34">
          <component :is="icon" />
        </el-icon>
        <p class="sb-empty-title">{{ description }}</p>
      </template>
      <p v-if="hint" class="sb-empty-hint">{{ hint }}</p>
      <div v-if="$slots.default" class="sb-empty-action">
        <slot />
      </div>
    </div>
  </transition>
</template>

<script setup>
/**
 * 统一空状态（阶段12 功能点5）：图标 + 一句话 + 可选下一步动作。
 *
 * description 说「现在没有东西」，hint 说「怎么才会有东西」——
 * 只写「暂无数据」等于没说话，所以这里强制把两句分开填，页面各自给具体文案。
 */
defineProps({
  description: {
    type: String,
    default: '这里还什么都没有'
  },
  hint: {
    type: String,
    default: ''
  },
  // 传图标名时改用图标形态（列表内嵌的小空态更克制），不传则用 el-empty 的插画
  icon: {
    type: String,
    default: ''
  }
})
</script>

<style scoped>
.sb-empty {
  padding: 8px 0 16px;
}

.sb-empty-icon {
  display: flex;
  margin: 0 auto;
  color: var(--sb-color-text-disabled);
}

.sb-empty-title {
  margin: 8px 0 0;
  text-align: center;
  font-size: var(--sb-font-size-sm);
  color: var(--sb-color-text-secondary);
}

.sb-empty-hint {
  margin: 0;
  text-align: center;
  font-size: var(--sb-font-size-xs);
  color: var(--sb-color-text-secondary);
}

.sb-empty-action {
  margin-top: 12px;
  text-align: center;
}
</style>
