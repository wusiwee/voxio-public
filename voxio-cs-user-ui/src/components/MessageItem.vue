<template>
  <div v-if="timeDivider" class="msg-divider">
    <span>{{ timeDivider }}</span>
  </div>

  <div v-if="isSystem" class="msg-system" :class="{ 'msg-fresh': message.fresh }">
    <span>{{ message.content }}</span>
  </div>

  <div v-else class="msg-row" :class="[mine ? 'msg-row--mine' : 'msg-row--peer', { 'msg-fresh': message.fresh }]">
    <div class="msg-avatar" :class="mine ? 'msg-avatar--mine' : 'msg-avatar--peer'">
      <img v-if="!mine && message.avatar" :src="message.avatar" alt="" />
      <span v-else>{{ mine ? '我' : '客服' }}</span>
    </div>

    <div class="msg-content">
      <div class="msg-bubble" :class="mine ? 'msg-bubble--mine' : 'msg-bubble--peer'">
        <span class="msg-text">{{ message.content }}</span>
      </div>

      <div class="msg-meta">
        <span>{{ timeText }}</span>
        <span v-if="mine" class="msg-status">
          <em v-if="message.status === 'sending'" class="msg-status--sending">
            <el-icon class="is-loading"><Loading /></el-icon>发送中
          </em>
          <em v-else-if="message.status === 'failed'" class="msg-status--failed" title="网络或链路有问题，这一条没有发出去" @click="$emit('resend', message)">发送失败，点击重发</em>
          <em v-else-if="message.status === 'read'" class="cs-muted">已读</em>
          <em v-else class="cs-muted">已送达</em>
        </span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { Loading } from '@element-plus/icons-vue'
import { formatTime } from '@/utils/format'

const props = defineProps({
  message: { type: Object, required: true },
  // 时间分组标签：与上一条间隔超过阈值时显示（阶段8 功能点32）
  timeDivider: { type: String, default: '' }
})
defineEmits(['resend'])

const mine = computed(() => props.message.senderType === '1')
const isSystem = computed(() => props.message.senderType === '3' || props.message.msgType === '4')
const timeText = computed(() => formatTime(props.message.createTime))
</script>

<style scoped>
.msg-divider {
  text-align: center;
  margin: 10px 0 2px;
}

.msg-divider span {
  display: inline-block;
  padding: 2px 10px;
  border-radius: var(--sb-radius-pill);
  background: var(--sb-color-bg-hover);
  color: var(--sb-color-text-secondary);
  font-size: var(--sb-font-size-xs);
}

.msg-system {
  text-align: center;
  margin: 10px 0;
}

.msg-system span {
  display: inline-block;
  max-width: var(--sb-bubble-max-width);
  padding: 4px 10px;
  border-radius: var(--sb-radius-sm);
  background: var(--sb-color-bubble-system);
  color: var(--sb-color-text-secondary);
  font-size: var(--sb-font-size-xs);
  line-height: 1.5;
  white-space: pre-wrap;
}

.msg-row {
  display: flex;
  gap: 8px;
  margin: 12px 0;
  align-items: flex-start;
}

.msg-row--mine {
  flex-direction: row-reverse;
}

.msg-avatar {
  width: 32px;
  height: 32px;
  flex: none;
  border-radius: var(--sb-radius-sm);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: var(--sb-font-size-xs);
  color: var(--sb-color-primary-on-contrast);
  overflow: hidden;
}

.msg-avatar--mine {
  background: var(--sb-color-info);
}

.msg-avatar--peer {
  background: var(--sb-color-primary);
}

.msg-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.msg-content {
  max-width: var(--sb-bubble-max-width);
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.msg-row--mine .msg-content {
  align-items: flex-end;
}

.msg-bubble {
  padding: 8px 12px;
  border-radius: var(--sb-radius-bubble);
  line-height: var(--sb-font-line-height);
  word-break: break-word;
  white-space: pre-wrap;
}

/* 访客：主色气泡；客服：白底描边气泡 */
.msg-bubble--mine {
  background: var(--sb-color-bubble-agent);
  color: var(--sb-color-primary-on-contrast);
  border-top-right-radius: 4px;
}

.msg-bubble--peer {
  background: var(--sb-color-bubble-visitor);
  border: 1px solid var(--sb-color-border);
  color: var(--sb-color-text);
  border-top-left-radius: 4px;
}

.msg-meta {
  display: flex;
  gap: 8px;
  font-size: 11px;
  color: var(--sb-color-text-secondary);
}

.msg-status--sending {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  font-style: normal;
}

.msg-status--failed {
  color: var(--sb-color-warning);
  cursor: pointer;
  font-style: normal;
}
</style>
