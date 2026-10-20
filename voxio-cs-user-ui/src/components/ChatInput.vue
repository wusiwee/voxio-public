<template>
  <div class="input-bar">
    <div v-if="emojiVisible" class="input-bar__emoji">
      <div v-for="group in EMOJI_GROUPS" :key="group.name" class="input-bar__emoji-group">
        <span class="input-bar__emoji-name cs-muted">{{ group.name }}</span>
        <div class="input-bar__emoji-grid">
          <button
            v-for="(item, index) in group.items"
            :key="`${group.name}-${index}`"
            type="button"
            class="input-bar__emoji-item"
            @click="insertEmoji(item)"
          >
            {{ item }}
          </button>
        </div>
      </div>
    </div>

    <div class="input-bar__main">
      <el-button
        circle
        :disabled="disabled"
        :class="{ 'input-bar__emoji-btn--on': emojiVisible }"
        title="表情"
        @click="emojiVisible = !emojiVisible"
      >🙂</el-button>
      <textarea
        ref="inputRef"
        v-model="text"
        class="input-bar__text"
        rows="1"
        :placeholder="placeholderText"
        :disabled="disabled"
        maxlength="2000"
        @keydown="onKeydown"
        @input="autoResize"
      ></textarea>
      <el-button class="input-bar__send" type="primary" :loading="busy" :disabled="disabled || busy || !text.trim()" @click="submit">
        发送
      </el-button>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { EMOJI_GROUPS } from '@/utils/emoji'

const props = defineProps({
  disabled: { type: Boolean, default: false },
  placeholder: { type: String, default: '请描述您的问题，Enter 发送 / Shift+Enter 换行' },
  // 已发出、还没等到 ACK 的消息条数（阶段12 功能点4）：非 0 时发送按钮转圈并禁用，
  // 挡住「连点两下把同一句话发两遍」；服务端 uk_session_client 是第二道防线
  pending: { type: Number, default: 0 }
})
const emit = defineEmits(['send', 'typing'])

const text = ref('')
const inputRef = ref(null)
const emojiVisible = ref(false)

const busy = computed(() => props.pending > 0)

/**
 * 移动端不启用 Enter 发送（阶段12 功能点25）：手机软键盘的回车键是「换行」语义，
 * 一旦拦截，访客想写第二段就写不进去了，还会误发。判定用视口断点，与样式里的 768px 同一个口径。
 */
const isMobile = () => window.matchMedia('(max-width: 768px)').matches

const placeholderText = computed(() => (isMobile() ? '请描述您的问题' : props.placeholder))

function onKeydown(event) {
  if (event.key !== 'Enter' || isMobile()) return
  if (event.shiftKey) return // 保留浏览器默认换行
  event.preventDefault()
  submit()
}

watch(() => props.disabled, (on) => { if (on) emojiVisible.value = false })

function autoResize(event) {
  resize(event.target)
  if (event.target.value.trim()) emit('typing', true)
}

function resize(el) {
  el.style.height = 'auto'
  el.style.height = Math.min(el.scrollHeight, 120) + 'px'
}

/** 在光标处插入表情而不是追加到末尾：访客常在句子中间加表情 */
function insertEmoji(char) {
  if (props.disabled) return
  const el = inputRef.value
  const current = text.value
  const start = el ? el.selectionStart : current.length
  const end = el ? el.selectionEnd : current.length
  text.value = current.slice(0, start) + char + current.slice(end)
  emit('typing', true)
  nextTick(() => {
    if (!el) return
    el.focus()
    const caret = start + char.length
    el.setSelectionRange(caret, caret)
    resize(el)
  })
}

function submit() {
  if (props.pending > 0) {
    ElMessage.info('上一条正在发送，请稍候')
    return
  }
  const content = text.value.trim()
  if (!content) return
  emit('send', { type: 'text', content })
  text.value = ''
  if (inputRef.value) inputRef.value.style.height = 'auto'
  emit('typing', false)
}

function setText(value) {
  text.value = value || ''
  nextTick(() => inputRef.value && inputRef.value.focus())
}

// blur 给「点消息区收起键盘」用：小屏上键盘挡住一半屏幕，访客看完消息想收掉却没有任何入口（功能点7）
defineExpose({
  focus: () => inputRef.value && inputRef.value.focus(),
  blur: () => inputRef.value && inputRef.value.blur(),
  setText
})
</script>

<style scoped>
.input-bar {
  background: var(--sb-color-bg-card);
}

.input-bar__main {
  display: flex;
  align-items: flex-end;
  gap: 8px;
}

/* 表情面板：与输入区同宽的圆角块，点表情在光标处插入，面板保持打开可连点 */
.input-bar__emoji {
  max-height: 168px;
  overflow-y: auto;
  margin-bottom: 8px;
  padding: 8px 10px;
  border: 1px solid var(--sb-color-border);
  border-radius: var(--sb-radius-lg);
  background: var(--sb-color-bg-page);
}

.input-bar__emoji-group + .input-bar__emoji-group {
  margin-top: 6px;
}

.input-bar__emoji-name {
  font-size: var(--sb-font-size-xs);
}

.input-bar__emoji-grid {
  display: grid;
  grid-template-columns: repeat(10, 1fr);
  gap: 2px;
  margin-top: 4px;
}

.input-bar__emoji-item {
  border: none;
  background: none;
  border-radius: var(--sb-radius-sm);
  padding: 3px 0;
  font-size: 18px;
  line-height: 1.3;
  cursor: pointer;
  font-family: 'Segoe UI Emoji', 'Apple Color Emoji', 'Noto Color Emoji', var(--sb-font-family);
}

.input-bar__emoji-item:hover {
  background: var(--sb-color-bg-hover);
}

.input-bar__emoji-btn--on {
  border-color: var(--sb-color-primary);
  color: var(--sb-color-primary);
}

.input-bar__text {
  flex: 1;
  min-height: var(--sb-control-height);
  max-height: 120px;
  padding: 9px 12px;
  border: 1px solid var(--sb-color-border);
  border-radius: var(--sb-radius-md);
  background: var(--sb-color-bg-page);
  color: var(--sb-color-text);
  resize: none;
  outline: none;
  font: inherit;
  line-height: var(--sb-font-line-height);
}

.input-bar__text:focus {
  border-color: var(--sb-color-primary);
  background: var(--sb-color-bg-card);
}

@media (max-width: 768px) {
  /* 移动端：加大点击区域，输入框随键盘吸底，按钮不显示文字避免挤压 */
  .input-bar__main {
    gap: 6px;
  }

  .input-bar__emoji-grid {
    grid-template-columns: repeat(8, 1fr);
    gap: 4px;
  }

  .input-bar__text {
    min-height: 42px;
    font-size: 16px;
  }

  .input-bar__send {
    padding: 12px 14px;
  }
}
</style>
