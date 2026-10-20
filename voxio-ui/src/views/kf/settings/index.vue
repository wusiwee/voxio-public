<template>
   <div class="app-container" v-loading="loading">
      <el-alert type="info" :closable="false" show-icon class="kf-alert">
         <template #title>
            单商户业务设置：仅收录会直接影响访客与客服行为的参数。密钥、限流、分配策略等运维参数请在「参数配置」中维护。
         </template>
      </el-alert>

      <el-form ref="settingRef" :model="form" :rules="rules" label-width="150px" style="max-width: 900px">
         <el-card shadow="never">
            <template #header>访客文案</template>
            <el-form-item label="进入欢迎语" prop="welcomeText">
               <el-input v-model="form.welcomeText" type="textarea" :rows="2" maxlength="500" show-word-limit
                  placeholder="访客建立会话时下发的默认欢迎语" />
            </el-form-item>
            <el-form-item label="排队提示语" prop="queueTip">
               <el-input v-model="form.queueTip" type="textarea" :rows="2" maxlength="500" show-word-limit
                  placeholder="支持占位符 {position}，会被替换为实时排队位置" />
            </el-form-item>
            <el-form-item label="无客服在线提示" prop="offlineTip">
               <el-input v-model="form.offlineTip" type="textarea" :rows="2" maxlength="500" show-word-limit />
            </el-form-item>
            <el-form-item label="留言成功回执" prop="replyTip">
               <el-input v-model="form.replyTip" type="textarea" :rows="2" maxlength="500" show-word-limit />
            </el-form-item>
         </el-card>

         <el-card shadow="never" class="kf-card">
            <template #header>留言与服务时间</template>
            <el-form-item label="允许离线留言">
               <el-switch v-model="form.offlineEnabled" active-text="开启" inactive-text="关闭" />
               <span class="kf-tip">关闭后访客只能提交工单</span>
            </el-form-item>
            <el-form-item label="限制服务时间">
               <el-switch v-model="form.serviceEnabled" active-text="开启" inactive-text="关闭" />
               <span class="kf-tip">开启后非服务时间段访客只能留言</span>
            </el-form-item>
            <el-form-item label="服务时间段" prop="serviceTime">
               <el-input v-model="form.serviceTime" placeholder="HH:mm-HH:mm，如 09:00-18:00，支持跨零点 20:00-02:00" style="width: 280px" />
            </el-form-item>
         </el-card>

         <el-card shadow="never" class="kf-card">
            <template #header>会话分配</template>
            <el-form-item label="自动分配客服">
               <el-switch v-model="form.assignEnabled" active-text="开启" inactive-text="关闭" />
               <span class="kf-tip">关闭时新会话只进排队，需在「会话管理 → 会话监控」由主管点「分配」指定客服</span>
            </el-form-item>
            <el-form-item label="单客服最大并发" prop="maxPerAgent">
               <el-input-number v-model="form.maxPerAgent" :min="1" :max="50" controls-position="right" />
               <span class="kf-tip">客服个人上限（accept_max）与该值取较小者</span>
            </el-form-item>
            <el-form-item label="无活动自动结束" prop="timeoutMinutes">
               <el-input-number v-model="form.timeoutMinutes" :min="0" :max="1440" controls-position="right" />
               <span class="kf-tip">单位分钟，0 表示不自动结束</span>
            </el-form-item>
         </el-card>

         <el-card shadow="never" class="kf-card">
            <template #header>工单第三方推送</template>
            <el-form-item label="开启手动推送">
               <el-switch v-model="form.pushEnabled" active-text="开启" inactive-text="关闭" />
               <span class="kf-tip">关闭时工单详情的「同步」按钮提示未启用，不发起任何外呼</span>
            </el-form-item>
            <el-form-item label="推送地址" prop="pushUrl">
               <el-input v-model="form.pushUrl" placeholder="完整 http(s) 地址，本系统按 JSON POST 当前工单" maxlength="300" />
               <span class="kf-tip">不做字段映射，字段固定为工单号/标题/内容/分类/优先级/状态/联系方式/会话与访客 ID/时间与操作人</span>
            </el-form-item>
            <el-form-item label="鉴权头" prop="pushHeaders">
               <el-input v-model="form.pushHeaders" placeholder="形如 Authorization=Bearer xxx，多个用英文分号分隔" maxlength="300" />
               <span class="kf-tip">只在本参数里维护，不随开放接口下发；content-length/host/connection/upgrade 等保留头不允许填写</span>
            </el-form-item>
            <el-form-item label="推送超时" prop="pushTimeoutSeconds">
               <el-input-number v-model="form.pushTimeoutSeconds" :min="1" :max="30" controls-position="right" />
               <span class="kf-tip">单位秒，超时即记为推送失败，系统不做自动重试，需要人工再点一次「同步」</span>
            </el-form-item>
         </el-card>

         <div class="kf-actions">
            <el-button type="primary" @click="submitForm" v-hasPermi="['kf:settings:edit']">保 存</el-button>
            <el-button @click="loadBusiness">重 置</el-button>
            <span v-if="dirty" class="kf-dirty">有未保存的修改</span>         </div>
      </el-form>
   </div>
</template>

<script setup name="KfSettings">
import { getBusiness, updateBusiness } from "@/api/kf/settings"

const { proxy } = getCurrentInstance()

const loading = ref(false)
const KEYS = {
  welcomeText: "kf.welcome.text",
  queueTip: "kf.queue.tip",
  offlineTip: "kf.agent.offline.tip",
  replyTip: "kf.offline.reply.tip",
  offlineEnabled: "kf.offline.enabled",
  serviceEnabled: "kf.service.enabled",
  serviceTime: "kf.service.time",
  assignEnabled: "kf.assign.enabled",
  maxPerAgent: "kf.assign.maxPerAgent",
  timeoutMinutes: "kf.session.timeout.minutes",
  pushEnabled: "kf.ticket.push.enabled",
  pushUrl: "kf.ticket.push.url",
  pushHeaders: "kf.ticket.push.headers",
  pushTimeoutSeconds: "kf.ticket.push.timeoutSeconds"
}
const BOOL_FIELDS = ["offlineEnabled", "serviceEnabled", "assignEnabled", "pushEnabled"]
const NUMBER_FIELDS = ["maxPerAgent", "timeoutMinutes", "pushTimeoutSeconds"]

const source = ref({})
const form = reactive({
  welcomeText: "", queueTip: "", offlineTip: "", replyTip: "", offlineEnabled: false,
  serviceEnabled: false, serviceTime: "", assignEnabled: false, maxPerAgent: 5,
  timeoutMinutes: 30,
  pushEnabled: false, pushUrl: "", pushHeaders: "", pushTimeoutSeconds: 5
})
// 改了没保存就切走，原先是静默丢弃：配置页字段多，滚到底才发现白改是最常见的一次返工
const loadedSnapshot = ref("")
const dirty = computed(() => JSON.stringify(form) !== loadedSnapshot.value)

onBeforeRouteLeave(async () => {
  if (!dirty.value) {
    return true
  }
  try {
    await proxy.$modal.confirm('业务设置还没保存，离开后这些改动会丢失。')
    return true
  } catch {
    return false
  }
})

const rules = {
  welcomeText: [{ max: 500, message: "最多 500 个字符", trigger: "blur" }],
  queueTip: [{ max: 500, message: "最多 500 个字符", trigger: "blur" }],
  offlineTip: [{ max: 500, message: "最多 500 个字符", trigger: "blur" }],
  replyTip: [{ max: 500, message: "最多 500 个字符", trigger: "blur" }],
  serviceTime: [{ pattern: /^(\d{1,2}:\d{2}-\d{1,2}:\d{2})?$/, message: "格式应为 HH:mm-HH:mm", trigger: "blur" }],
  maxPerAgent: [{ required: true, message: "请填写单客服最大并发会话数", trigger: "blur" }],
  timeoutMinutes: [{ required: true, message: "请填写自动结束分钟数", trigger: "blur" }],
  pushUrl: [{ max: 300, message: "最多 300 个字符", trigger: "blur" }],
  pushHeaders: [{ max: 300, message: "最多 300 个字符", trigger: "blur" }],
  pushTimeoutSeconds: [{ required: true, message: "请填写推送超时秒数", trigger: "blur" }]
}

function loadBusiness() {
  loading.value = true
  getBusiness().then(response => {
    const data = response.data || {}
    source.value = { ...data }
    Object.keys(KEYS).forEach(field => {
      const value = data[KEYS[field]]
      if (BOOL_FIELDS.includes(field)) {
        form[field] = value === "true"
      } else if (NUMBER_FIELDS.includes(field)) {
        form[field] = value === undefined || value === "" ? undefined : Number(value)
      } else {
        form[field] = value === undefined ? "" : value
      }
    })
    loadedSnapshot.value = JSON.stringify(form)
  }).finally(() => {
    loading.value = false
  })
}

function submitForm() {
  proxy.$refs["settingRef"].validate(valid => {
    if (!valid) {
      return
    }
    const payload = {}
    Object.keys(KEYS).forEach(field => {
      if (BOOL_FIELDS.includes(field)) {
        payload[KEYS[field]] = form[field] ? "true" : "false"
      } else if (NUMBER_FIELDS.includes(field)) {
        payload[KEYS[field]] = String(form[field])
      } else {
        payload[KEYS[field]] = form[field] === undefined ? "" : form[field]
      }
    })
    updateBusiness(payload).then(() => {
      proxy.$modal.msgSuccess("保存成功，配置已立即生效")
      loadBusiness()
    })
  })
}

loadBusiness()
</script>

<style scoped>
.kf-alert {
  margin-bottom: 12px;
}
.kf-card {
  margin-top: 12px;
}
.kf-tip {
  margin-left: 12px;
  font-size: 12px;
  color: var(--el-text-color-placeholder);
}
.kf-dirty {
  margin-left: 12px;
  font-size: 12px;
  color: var(--sb-color-warning);
}
.kf-actions {
  margin-top: 16px;
}
</style>
