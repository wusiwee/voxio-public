<template>
  <el-dialog v-model="visible" title="留言给客服" width="min(440px, 92vw)" append-to-body>
    <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
      <el-form-item label="联系方式" prop="contact">
        <el-input v-model="form.contact" maxlength="50" placeholder="手机号 / 邮箱，便于客服回复您" />
      </el-form-item>
      <el-form-item label="留言内容" prop="content">
        <el-input
          v-model="form.content"
          type="textarea"
          :rows="4"
          maxlength="2000"
          show-word-limit
          placeholder="请描述您的问题，客服上线后会通过上面的联系方式回复您"
        />
      </el-form-item>
    </el-form>
    <p v-if="tip" class="cs-muted offline__tip">{{ tip }}</p>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submit">提交留言</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { submitOffline } from '@/api/open'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  sessionId: { type: [String, Number], default: '' },
  defaultContent: { type: String, default: '' },
  replyTip: { type: String, default: '' }
})
const emit = defineEmits(['update:modelValue', 'done'])

const formRef = ref(null)
const submitting = ref(false)
const form = reactive({ contact: '', content: '' })

const visible = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value)
})

const tip = computed(() => props.replyTip)

const rules = {
  contact: [
    { required: true, message: '请填写联系方式', trigger: 'blur' },
    { max: 50, message: '联系方式最多 50 字', trigger: 'blur' }
  ],
  content: [
    { required: true, message: '请填写留言内容', trigger: 'blur' },
    { max: 2000, message: '留言最多 2000 字', trigger: 'blur' }
  ]
}

watch(
  () => props.modelValue,
  (value) => {
    if (value && props.defaultContent) {
      form.content = props.defaultContent.slice(0, 2000)
    }
  }
)

async function submit() {
  // validate() 失败会 reject；不接住就是一条未处理异常，界面看起来「点了没反应」。
  // 校验信息本身由 el-form-item 贴在字段下方，这里只补一次滚动+ Toast，确保访客知道为什么没提交（功能点11/12）
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) {
    ElMessage.warning('请先填写联系方式和留言内容')
    return
  }
  submitting.value = true
  try {
    const res = await submitOffline({
      sessionId: props.sessionId || undefined,
      contact: form.contact.trim(),
      content: form.content.trim()
    })
    ElMessage.success(res.data?.tip || '留言已提交')
    visible.value = false
    emit('done', res.data)
  } catch {
    /* 请求层已提示 */
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.offline__tip {
  margin: -6px 0 0;
  font-size: 12px;
}
</style>
