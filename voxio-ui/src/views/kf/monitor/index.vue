<template>
   <div class="app-container">
      <el-tabs v-model="activeTab" @tab-change="handleTabChange">
         <el-tab-pane label="会话监控" name="session" />
         <el-tab-pane label="客服状态" name="agent" />
      </el-tabs>

      <template v-if="activeTab === 'session'">
         <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch">
            <el-form-item label="会话状态" prop="status">
               <el-select v-model="queryParams.status" style="width: 140px" @change="handleQuery">
                  <el-option label="进行中" value="active" />
                  <el-option label="全部" value="all" />
                  <el-option v-for="dict in kf_session_status" :key="dict.value" :label="dict.label" :value="dict.value" />
               </el-select>
            </el-form-item>
            <el-form-item label="接待客服" prop="agentId">
               <el-select v-model="queryParams.agentId" placeholder="全部" clearable style="width: 150px" @change="handleQuery">
                  <el-option v-for="item in agentOptions" :key="item.agentId" :label="item.agentNick" :value="item.agentId" />
               </el-select>
            </el-form-item>
            <el-form-item label="访客ID" prop="visitorId">
               <el-input v-model="queryParams.visitorId" placeholder="访客ID" clearable style="width: 120px" @keyup.enter="handleQuery" />
            </el-form-item>
            <el-form-item label="关键字" prop="keyword">
               <el-input v-model="queryParams.keyword" placeholder="会话号 / 访客昵称 / 客服昵称" clearable style="width: 220px" @keyup.enter="handleQuery" />
            </el-form-item>
            <el-form-item label="创建时间">
               <el-date-picker v-model="dateRange" type="daterange" value-format="YYYY-MM-DD" range-separator="-" @change="handleQuery"
                  start-placeholder="开始日期" end-placeholder="结束日期" style="width: 240px" />
            </el-form-item>
            <el-form-item>
               <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
               <el-button icon="Refresh" @click="resetQuery">重置</el-button>
            </el-form-item>
         </el-form>

         <el-row :gutter="10" class="mb8">
            <!-- 功能点21：监控页要回答的是「此刻什么样」，只给一个手动刷新按钮等于让管理员自己数秒 -->
            <el-col :span="1.5">
               <el-tooltip content="每 10 秒自动拉一次进行中会话；页面切到后台时暂停，切回来立即补一次" placement="top">
                  <span class="kf-auto">
                     <el-switch v-model="autoRefresh" size="small" />
                     <span class="kf-auto-text">自动刷新</span>
                  </span>
               </el-tooltip>
            </el-col>
            <right-toolbar v-model:showSearch="showSearch" @queryTable="getSessionList"></right-toolbar>
         </el-row>

         <!-- 阶段12 功能点1：首次进页面是骨架，二次加载只盖一层轻量 loading，不做整屏遮罩 -->
         <table-skeleton v-if="firstLoad" :rows="6" />
         <el-table v-else v-loading="loading" :data="sessionList">
            <el-table-column label="会话号" align="center" prop="sessionNo" width="190" show-overflow-tooltip />
            <el-table-column label="状态" align="center" prop="status" width="90">
               <template #default="scope">
                  <dict-tag :options="kf_session_status" :value="scope.row.status" />
               </template>
            </el-table-column>
            <el-table-column label="访客" align="center" width="150" show-overflow-tooltip>
               <template #default="scope">
                  <div>{{ scope.row.visitorNick || '匿名访客' }}</div>
                  <div class="kf-sub">{{ scope.row.visitorNo }}</div>
               </template>
            </el-table-column>
            <el-table-column label="接待客服" align="center" width="120">
               <template #default="scope">
                  <span v-if="scope.row.agentNick">{{ scope.row.agentNick }}</span>
                  <el-tag v-else type="info" size="small">未分配</el-tag>
               </template>
            </el-table-column>
            <el-table-column label="消息" align="center" prop="messageCount" width="70" />
            <el-table-column label="最后消息" align="center" prop="lastContent" show-overflow-tooltip min-width="180" />
            <el-table-column label="排队/分配" align="center" width="150">
               <template #default="scope">
                  <div class="kf-sub">{{ timeText(scope.row.queueTime) }}</div>
                  <div class="kf-sub">{{ timeText(scope.row.assignTime) }}</div>
               </template>
            </el-table-column>
            <el-table-column label="最后活动" align="center" width="140">
               <template #default="scope">{{ timeText(scope.row.lastMsgTime) }}</template>
            </el-table-column>
            <el-table-column label="未读" align="center" width="70">
               <template #default="scope">{{ scope.row.unreadAgent || 0 }}</template>
            </el-table-column>
            <el-table-column label="操作" align="center" width="150" class-name="small-padding fixed-width">
               <template #default="scope">
                  <el-button link type="primary" icon="View" @click="handleDetail(scope.row)" v-hasPermi="['kf:monitor:detail']">详情</el-button>
                  <el-button v-if="scope.row.status === '0'" link type="primary" icon="User"
                             @click="handleAssign(scope.row)" v-hasPermi="['kf:monitor:assign']">分配</el-button>
               </template>
            </el-table-column>

            <!-- 统一空状态（阶段8 功能点22） -->
            <template #empty>
              <empty-state description="当前没有进行中的会话" hint="访客发起咨询后会实时出现在这里" />
            </template>
         </el-table>

         <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getSessionList" />
      </template>

      <template v-if="activeTab === 'agent'">
         <el-form :model="agentParams" ref="agentQueryRef" :inline="true">
            <el-form-item label="在线状态" prop="onlineStatus">
               <el-select v-model="agentParams.onlineStatus" placeholder="全部" clearable style="width: 140px">
                  <el-option v-for="dict in kf_online_status" :key="dict.value" :label="dict.label" :value="dict.value" />
               </el-select>
            </el-form-item>
            <el-form-item label="关键字" prop="keyword">
               <el-input v-model="agentParams.keyword" placeholder="客服昵称 / 登录账号" clearable style="width: 200px" @keyup.enter="getAgentList" />
            </el-form-item>
            <el-form-item>
               <el-button type="primary" icon="Search" @click="getAgentList">搜索</el-button>
               <el-button icon="Refresh" @click="resetAgentQuery">重置</el-button>
            </el-form-item>
         </el-form>

         <el-table v-loading="agentLoading" :data="agentList">
            <el-table-column label="客服账号" align="center" prop="agentNo" width="140" />
            <el-table-column label="昵称" align="center" prop="agentNick" width="140" />
            <el-table-column label="在线状态" align="center" width="110">
               <template #default="scope">
                  <dict-tag :options="kf_online_status" :value="scope.row.onlineStatus" />
               </template>
            </el-table-column>
            <el-table-column label="接待中" align="center" prop="servingCount" width="90" />
            <el-table-column label="负载 / 上限" align="center" width="110">
               <template #default="scope">{{ scope.row.currentLoad }} / {{ scope.row.acceptMax }}</template>
            </el-table-column>
            <el-table-column label="有未读会话" align="center" prop="unreadSessionCount" width="110" />
            <el-table-column label="账号状态" align="center" width="100">
               <template #default="scope">
                  <el-tag :type="scope.row.status === '0' ? 'success' : 'danger'">{{ scope.row.status === '0' ? '正常' : '停用' }}</el-tag>
               </template>
            </el-table-column>
            <el-table-column label="最后登录" align="center" width="160">
               <template #default="scope">{{ timeText(scope.row.lastLoginTime) }}</template>
            </el-table-column>
         </el-table>
      </template>

      <el-drawer v-model="detailOpen" title="会话详情（只读）" size="55%">
        <!-- 详情内容区骨架（阶段12 功能点2）：下面的真实区块本就带 v-if，加载期间只会是这一块在占位 -->
        <table-skeleton v-if="detailLoading" :rows="5" label="详情加载中" />
         <el-descriptions :column="2" border v-if="detail.sessionId">
            <el-descriptions-item label="会话号">{{ detail.sessionNo }}</el-descriptions-item>
            <el-descriptions-item label="状态">
               <dict-tag :options="kf_session_status" :value="detail.status" />
            </el-descriptions-item>
            <el-descriptions-item label="访客">{{ detail.visitorNick || '匿名访客' }}（{{ detail.visitorNo }}）</el-descriptions-item>
            <el-descriptions-item label="接待客服">{{ detail.agentNick || '未分配' }}</el-descriptions-item>
            <el-descriptions-item label="联系方式">{{ contactText }}</el-descriptions-item>
            <el-descriptions-item label="来源页面">{{ detail.sourcePage || '-' }}</el-descriptions-item>
            <el-descriptions-item label="进入排队">{{ dateTimeText(detail.queueTime) }}</el-descriptions-item>
            <el-descriptions-item label="分配客服">{{ dateTimeText(detail.assignTime) }}</el-descriptions-item>
            <el-descriptions-item label="客服首响">{{ dateTimeText(detail.firstRespTime) }}</el-descriptions-item>
            <el-descriptions-item label="结束时间">{{ dateTimeText(detail.endTime) }}</el-descriptions-item>
            <el-descriptions-item label="结束方">
               <dict-tag :options="kf_session_end_by" :value="detail.endBy" v-if="detail.endBy" />
               <span v-else>-</span>
            </el-descriptions-item>
            <el-descriptions-item label="访客IP">{{ detail.visitorIp || '-' }}</el-descriptions-item>
            <el-descriptions-item label="消息总数">{{ detail.messageCount || 0 }}</el-descriptions-item>
            <el-descriptions-item label="结束原因" :span="2">{{ detail.closeReason || '-' }}</el-descriptions-item>
            <el-descriptions-item label="浏览器信息" :span="2">{{ detail.userAgent || '-' }}</el-descriptions-item>
         </el-descriptions>

         <el-card shadow="never" class="kf-detail-block">
            <template #header>消息明细（最近 {{ (detail.messages || []).length }} 条）</template>
            <div class="kf-msg-list">
               <div v-for="item in detail.messages" :key="item.msgId" class="kf-msg" :class="'kf-msg-' + item.senderType">
                  <div class="kf-msg-head">
                     <span class="kf-msg-name">{{ item.senderName }}</span>
                     <span class="kf-sub">{{ dateTimeText(item.createTime) }}</span>
                  </div>
                  <div class="kf-msg-body">
                     <span>{{ item.content || '—' }}</span>
                  </div>               </div>
               <el-empty v-if="!detail.messages || !detail.messages.length" description="暂无消息" />
            </div>
         </el-card>
      </el-drawer>

      <!-- 人工分配：仅排队中的会话可分配，接待中的会话不改派 -->
      <el-dialog v-model="assignOpen" title="分配客服" width="420px" append-to-body>
         <el-form label-width="90px">
            <el-form-item label="会话号">{{ assignRow.sessionNo }}</el-form-item>
            <el-form-item label="访客">{{ assignRow.visitorNick || '匿名访客' }}</el-form-item>
            <el-form-item label="进入排队">{{ dateTimeText(assignRow.queueTime) }}</el-form-item>
            <el-form-item label="分配给">
               <el-select v-model="assignAgentId" placeholder="请选择在线客服" style="width: 100%">
                  <el-option v-for="item in assignCandidates" :key="item.agentId"
                             :label="`${item.agentNick}（${item.agentNo}） 接待 ${item.servingCount}/${item.acceptMax}`"
                             :value="item.agentId" />
               </el-select>
               <div class="kf-sub" v-if="!assignCandidates.length">当前没有在线或忙碌的客服，请稍后再试</div>
            </el-form-item>
         </el-form>
         <template #footer>
            <el-button @click="assignOpen = false">取 消</el-button>
            <el-button type="primary" :loading="assignLoading" @click="submitAssign">确定分配</el-button>
         </template>
      </el-dialog>
   </div>
</template>

<script setup name="KfMonitor">
import { listMonitorSessions, listMonitorAgents, getMonitorSession, assignMonitorSession } from "@/api/kf/monitor"
import { checkPermi } from "@/utils/permission"
import { timeText, dateTimeText } from '@/utils/time'
import { applyQueryFilters, rememberFilters, restoreFilters, forgetFilters } from '@/utils/listState'

const { proxy } = getCurrentInstance()
const route = useRoute()
const { kf_session_status, kf_online_status, kf_session_end_by } =
  proxy.useDict('kf_session_status', 'kf_online_status', 'kf_session_end_by')

const activeTab = ref("session")
const showSearch = ref(true)
const loading = ref(false)
// 首次加载标记：骨架只在这一帧出现，之后保持 false
const firstLoad = ref(true)
const sessionList = ref([])
const total = ref(0)
const dateRange = ref([])
const agentOptions = ref([])

const agentLoading = ref(false)
const agentList = ref([])
const agentParams = reactive({ onlineStatus: undefined, keyword: undefined })

const detailOpen = ref(false)
// 详情抽屉的加载中状态：驱动骨架与「操作按钮禁用」两件事
const detailLoading = ref(false)
const detail = ref({})

const assignOpen = ref(false)
const assignLoading = ref(false)
const assignRow = ref({})
const assignAgentId = ref(undefined)

// 人工分配候选：账号正常 + 在线或忙碌 + 接待数未达个人上限
const assignCandidates = computed(() => agentOptions.value.filter(item =>
  item.status === '0' && (item.onlineStatus === '1' || item.onlineStatus === '2')
  && Number(item.servingCount || 0) < Number(item.acceptMax || 0)))

const queryParams = reactive({
  pageNum: 1, pageSize: 10, status: "active", agentId: undefined, visitorId: undefined,
  keyword: undefined, beginTime: undefined, endTime: undefined
})

const contactText = computed(() => {
  const phone = detail.value.contactPhone || ""
  const email = detail.value.contactEmail || ""
  return [phone, email].filter(item => item).join(" / ") || "-"
})

function buildTimeParams() {
  queryParams.beginTime = dateRange.value && dateRange.value.length ? dateRange.value[0] : undefined
  queryParams.endTime = dateRange.value && dateRange.value.length ? dateRange.value[1] : undefined
}

// 自动刷新：开关状态记住上次的（关掉的人不该每次进来再关一遍）
const autoRefresh = ref(localStorage.getItem('sb.monitor.auto') !== '0')
let refreshTimer = null

function startAutoRefresh() {
  stopAutoRefresh()
  if (!autoRefresh.value) {
    return
  }
  refreshTimer = setInterval(() => {
    // 页面在后台就别打接口，省下的就是别人的请求配额
    if (document.hidden) {
      return
    }
    getSessionList(true)
  }, 10000)
}

function stopAutoRefresh() {
  if (refreshTimer) {
    clearInterval(refreshTimer)
    refreshTimer = null
  }
}

function onVisibilityChange() {
  if (!document.hidden && autoRefresh.value) {
    getSessionList(true)
  }
}

watch(autoRefresh, value => {
  localStorage.setItem('sb.monitor.auto', value ? '1' : '0')
  value ? startAutoRefresh() : stopAutoRefresh()
})

onMounted(() => {
  startAutoRefresh()
  document.addEventListener('visibilitychange', onVisibilityChange)
})

onUnmounted(() => {
  stopAutoRefresh()
  document.removeEventListener('visibilitychange', onVisibilityChange)
})

function getSessionList(silent) {
  buildTimeParams()
  rememberFilters('kf.monitor', queryParams, FILTER_FIELDS)
  if (!silent) {
    loading.value = true
  }
  listMonitorSessions(queryParams).then(response => {
    sessionList.value = response.rows || []
    total.value = response.total || 0
  }).finally(() => {
    loading.value = false
    firstLoad.value = false
  })
}

function getAgentList() {
  agentLoading.value = true
  listMonitorAgents(agentParams).then(response => {
    agentList.value = response.data || []
  }).finally(() => {
    agentLoading.value = false
  })
}

function loadAgentOptions() {
  if (!checkPermi(['kf:monitor:query'])) {
    return
  }
  listMonitorAgents({}).then(response => {
    agentOptions.value = response.data || []
  })
}

function handleTabChange(name) {
  if (name === "agent") {
    getAgentList()
  } else {
    getSessionList()
  }
}

function handleQuery() {
  queryParams.pageNum = 1
  getSessionList()
}

function resetQuery() {
  proxy.resetForm("queryRef")
  dateRange.value = []
  queryParams.status = "active"
  forgetFilters('kf.monitor')
  handleQuery()
}

function resetAgentQuery() {
  proxy.resetForm("agentQueryRef")
  getAgentList()
}

function handleDetail(row) {
  // 抽屉先开、内容区先给骨架：公网数据库一次往返几十毫秒，不该让人对着「点了没反应」干等（阶段12 功能点2）
  detail.value = {}
  detailLoading.value = true
  detailOpen.value = true
  getMonitorSession(row.sessionId).then(response => {
    detail.value = response.data || {}
  }).finally(() => {
    detailLoading.value = false
  })
}

function handleAssign(row) {
  assignRow.value = row
  assignAgentId.value = undefined
  // 排队状态变化很快，打开弹窗时按当前负载重新拉一次候选
  listMonitorAgents({}).then(response => {
    agentOptions.value = response.data || []
    if (!assignCandidates.value.length) {
      proxy.$modal.msgWarning("当前没有可接单的在线客服")
    }
  })
  assignOpen.value = true
}

function submitAssign() {
  if (!assignAgentId.value) {
    proxy.$modal.msgWarning("请选择要分配的客服")
    return
  }
  assignLoading.value = true
  assignMonitorSession(assignRow.value.sessionId, assignAgentId.value).then(() => {
    proxy.$modal.msgSuccess("已分配")
    assignOpen.value = false
    getSessionList()
  }).finally(() => {
    assignLoading.value = false
  })
}

// 别人带着筛选条件跳过来（工单详情看那条会话、锁屏解锁回跳都算），落地就该是筛好的那一组，不用管理员再挑一遍
const FILTER_FIELDS = ['status', 'agentId', 'visitorId', 'keyword', 'beginTime', 'endTime']
restoreFilters('kf.monitor', queryParams, FILTER_FIELDS, ['status'])
if (queryParams.beginTime && !dateRange.value.length) {
  dateRange.value = [queryParams.beginTime, queryParams.endTime]
}
applyQueryFilters(queryParams, route.query, { agentId: Number, visitorId: Number })
if (route.query.beginTime) {
  dateRange.value = [route.query.beginTime, route.query.endTime || route.query.beginTime]
}

getSessionList()
loadAgentOptions()
</script>

<style scoped>
.kf-sub {
  font-size: 12px;
  color: var(--el-text-color-placeholder);
}
.kf-auto {
  display: inline-flex;
  align-items: center;
  height: 32px;
  padding: 0 4px;
}
.kf-auto-text {
  margin-left: 6px;
  font-size: 12px;
  color: var(--el-text-color-regular);
}
.kf-detail-block {
  margin-bottom: 12px;
}
.kf-msg-list {
  max-height: 46vh;
  overflow-y: auto;
}
.kf-msg {
  padding: 6px 8px;
  border-radius: 4px;
  margin-bottom: 6px;
  background: var(--el-fill-color-light);
}
.kf-msg-2 {
  background: var(--el-color-primary-light-9);
}
.kf-msg-3 {
  background: var(--el-fill-color);
  color: var(--el-text-color-secondary);
}
.kf-msg-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 2px;
}
.kf-msg-name {
  font-size: 12px;
  font-weight: 600;
}
</style>
