<template>
   <div class="app-container">
      <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch">
         <el-form-item label="关键字" prop="title">
            <el-input v-model="queryParams.title" placeholder="标题 / 工单号 / 访客" clearable style="width: 230px" @keyup.enter="handleQuery" />
         </el-form-item>
         <el-form-item label="状态" prop="status">
            <el-select v-model="queryParams.status" placeholder="全部" clearable style="width: 120px" @change="handleQuery">
               <el-option v-for="dict in kf_ticket_status" :key="dict.value" :label="dict.label" :value="dict.value" />
            </el-select>
         </el-form-item>
         <el-form-item label="分类" prop="category">
            <el-select v-model="queryParams.category" placeholder="全部" clearable style="width: 130px" @change="handleQuery">
               <el-option v-for="dict in kf_ticket_category" :key="dict.value" :label="dict.label" :value="dict.value" />
            </el-select>
         </el-form-item>
         <el-form-item label="优先级" prop="priority">
            <el-select v-model="queryParams.priority" placeholder="全部" clearable style="width: 110px" @change="handleQuery">
               <el-option v-for="dict in kf_ticket_priority" :key="dict.value" :label="dict.label" :value="dict.value" />
            </el-select>
         </el-form-item>
         <el-form-item label="来源" prop="source">
            <el-select v-model="queryParams.source" placeholder="全部" clearable style="width: 140px" @change="handleQuery">
               <el-option v-for="dict in kf_ticket_source" :key="dict.value" :label="dict.label" :value="dict.value" />
            </el-select>
         </el-form-item>
         <el-form-item label="处理客服" prop="handlerAgentId">
            <el-select v-model="queryParams.handlerAgentId" placeholder="全部" clearable style="width: 140px" @change="handleQuery">
               <el-option v-for="item in agentOptions" :key="item.agentId" :label="item.agentNick" :value="item.agentId" />
            </el-select>
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
         <el-col :span="1.5">
            <el-button type="primary" plain icon="Plus" @click="handleAdd" v-hasPermi="['kf:ticket:add']">新建工单</el-button>
         </el-col>
         <!-- 批量流转（功能点19）：主管早上把昨夜新增的一批工单统一推到「处理中」，原先要一条条开三次弹窗 -->
         <el-col :span="1.5" v-hasPermi="['kf:ticket:status']">
            <el-dropdown :disabled="!ids.length" @command="handleBatchStatus">
               <el-button type="success" plain :disabled="!ids.length">
                  批量流转<el-icon class="el-icon--right"><arrow-down /></el-icon>
               </el-button>
               <template #dropdown>
                  <el-dropdown-menu>
                     <el-dropdown-item command="1">流转为处理中</el-dropdown-item>
                     <el-dropdown-item command="2">流转为已解决</el-dropdown-item>
                     <el-dropdown-item command="3">流转为已关闭</el-dropdown-item>
                  </el-dropdown-menu>
               </template>
            </el-dropdown>
         </el-col>
         <el-col :span="1.5">
            <!-- 文案不写「当前页」：导出改走服务端之后，出去的是当前筛选条件的全部结果，不是这一页 -->
            <el-tooltip content="按当前筛选条件导出全部工单（服务端出表并写审计日志）" placement="top">
               <el-button type="warning" plain icon="Download" @click="handleExport">导出</el-button>
            </el-tooltip>
         </el-col>
         <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
      </el-row>
      <div class="kf-batch-hint" v-if="ids.length">已选 {{ ids.length }} 条</div>

      <!-- 阶段12 功能点1：首次进页面是骨架，二次加载只盖一层轻量 loading，不做整屏遮罩 -->
      <table-skeleton v-if="firstLoad" :rows="6" />
      <el-table v-else v-loading="loading" :data="ticketList" @selection-change="handleSelectionChange">
         <el-table-column type="selection" width="50" align="center" />
         <el-table-column label="工单号" align="center" prop="ticketNo" width="180" show-overflow-tooltip />
         <el-table-column label="标题" align="center" prop="title" min-width="200" show-overflow-tooltip />
         <el-table-column label="分类" align="center" width="110">
            <template #default="scope">
               <dict-tag :options="kf_ticket_category" :value="scope.row.category" />
            </template>
         </el-table-column>
         <el-table-column label="优先级" align="center" width="90">
            <template #default="scope">
               <el-tag :type="priorityTag(scope.row.priority)" size="small">
                  <dict-tag :options="kf_ticket_priority" :value="scope.row.priority" />
               </el-tag>
            </template>
         </el-table-column>
         <el-table-column label="状态" align="center" width="150">
            <template #default="scope">
               <dict-tag :options="kf_ticket_status" :value="scope.row.status" />
               <!-- 行内一步流转：只给当前状态合法的目标，没有下一步的（已关闭）就不显示入口 -->
               <el-dropdown v-if="nextOf(scope.row.status).length && checkPermi(['kf:ticket:status'])"
                            @command="target => quickStatus(scope.row, target)">
                  <el-button link type="primary" icon="Sort" class="kf-quick-status">流转</el-button>
                  <template #dropdown>
                     <el-dropdown-menu>
                        <el-dropdown-item v-for="item in nextOf(scope.row.status)" :key="item.value" :command="item.value">
                           {{ item.label }}
                        </el-dropdown-item>
                     </el-dropdown-menu>
                  </template>
               </el-dropdown>
            </template>
         </el-table-column>
         <el-table-column label="来源" align="center" width="120">
            <template #default="scope">
               <dict-tag :options="kf_ticket_source" :value="scope.row.source" />
            </template>
         </el-table-column>
         <el-table-column label="访客" align="center" width="130" show-overflow-tooltip>
            <template #default="scope">{{ scope.row.visitorNick || '-' }}</template>
         </el-table-column>
         <el-table-column label="处理客服" align="center" width="110">
            <template #default="scope">{{ scope.row.handlerNick || '未指派' }}</template>
         </el-table-column>
         <el-table-column label="创建时间" align="center" width="150">
            <template #default="scope">{{ timeText(scope.row.createTime) }}</template>
         </el-table-column>
         <el-table-column label="操作" align="center" width="230" class-name="small-padding fixed-width">
            <template #default="scope">
               <el-button link type="primary" icon="View" @click="handleDetail(scope.row)" v-hasPermi="['kf:ticket:query']">详情</el-button>
               <el-button link type="primary" icon="Sort" @click="handleStatus(scope.row)" v-hasPermi="['kf:ticket:status']">流转</el-button>
               <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['kf:ticket:edit']">修改</el-button>
               <el-button link type="primary" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['kf:ticket:remove']">删除</el-button>
            </template>
         </el-table-column>

      <!-- 统一空状态（阶段8 功能点22） -->
      <template #empty>
        <empty-state description="暂无工单" hint="会话与离线留言都可一键转为工单" />
      </template>
      </el-table>

      <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

      <!-- 新建 / 修改工单 -->
      <el-dialog :title="title" v-model="open" width="680px" append-to-body>
         <el-form ref="ticketRef" :model="form" :rules="rules" label-width="90px">
            <el-form-item label="工单标题" prop="title">
               <el-input v-model="form.title" placeholder="请输入工单标题" maxlength="100" show-word-limit />
            </el-form-item>
            <el-form-item label="工单内容" prop="content" v-if="!form.ticketId || form.content !== undefined">
               <el-input v-model="form.content" type="textarea" :rows="4" placeholder="请输入问题描述" maxlength="2000" show-word-limit />
            </el-form-item>
            <el-row>
               <el-col :span="12">
                  <el-form-item label="分类" prop="category">
                     <el-select v-model="form.category" placeholder="请选择分类" style="width: 100%">
                        <el-option v-for="dict in kf_ticket_category" :key="dict.value" :label="dict.label" :value="dict.value" />
                     </el-select>
                  </el-form-item>
               </el-col>
               <el-col :span="12">
                  <el-form-item label="优先级" prop="priority">
                     <el-select v-model="form.priority" placeholder="请选择优先级" style="width: 100%">
                        <el-option v-for="dict in kf_ticket_priority" :key="dict.value" :label="dict.label" :value="dict.value" />
                     </el-select>
                  </el-form-item>
               </el-col>
            </el-row>
            <el-row>
               <el-col :span="12">
                  <el-form-item label="处理客服" prop="handlerAgentId">
                     <el-select v-model="form.handlerAgentId" placeholder="暂不指派" clearable style="width: 100%">
                        <el-option v-for="item in agentOptions" :key="item.agentId" :label="item.agentNick" :value="item.agentId" />
                     </el-select>
                  </el-form-item>
               </el-col>
               <el-col :span="12">
                  <el-form-item label="联系方式" prop="contact">
                     <el-input v-model="form.contact" placeholder="手机 / 邮箱 / 会话号" maxlength="100" />
                  </el-form-item>
               </el-col>
            </el-row>
            <el-form-item label="关联会话" prop="sessionId" v-if="!form.ticketId">
               <el-input v-model="form.sessionId" placeholder="会话ID，可为空；填写后自动带出访客并标记来源" />
            </el-form-item>
         </el-form>
         <template #footer>
            <div class="dialog-footer">
               <el-button type="primary" :loading="writeBusy" :disabled="writeBusy" @click="submitForm">确 定</el-button>
               <el-button @click="cancel">取 消</el-button>
            </div>
         </template>
      </el-dialog>

      <!-- 状态流转 -->
      <el-dialog title="工单状态流转" v-model="statusOpen" width="520px" append-to-body>
         <el-form label-width="90px">
            <el-form-item label="工单">
               <span>{{ statusForm.ticketNo }} · {{ statusForm.title }}</span>
            </el-form-item>
            <el-form-item label="当前状态">
               <dict-tag :options="kf_ticket_status" :value="statusForm.status" />
            </el-form-item>
            <el-form-item label="目标状态">
               <el-select v-model="statusForm.targetStatus" placeholder="请选择目标状态" style="width: 100%">
                  <el-option v-for="item in nextStatusOptions" :key="item.value" :label="item.label" :value="item.value" />
               </el-select>
            </el-form-item>
            <el-form-item label="流转说明">
               <el-input v-model="statusForm.note" type="textarea" :rows="3" placeholder="处理过程或结论，将写入流转记录" maxlength="500" show-word-limit />
            </el-form-item>
         </el-form>
         <template #footer>
            <div class="dialog-footer">
               <el-button type="primary" :loading="writeBusy" :disabled="writeBusy" @click="submitStatus">确 定</el-button>
               <el-button @click="statusOpen = false">取 消</el-button>
            </div>
         </template>
      </el-dialog>

      <!-- 工单详情 -->
      <el-drawer v-model="detailOpen" title="工单详情" size="55%">
        <!-- 详情内容区骨架（阶段12 功能点2）：下面的真实区块本就带 v-if，加载期间只会是这一块在占位 -->
        <table-skeleton v-if="detailLoading" :rows="5" label="详情加载中" />
         <el-descriptions :column="2" border v-if="detail.ticket">
            <el-descriptions-item label="工单号">{{ detail.ticket.ticketNo }}</el-descriptions-item>
            <el-descriptions-item label="状态">
               <dict-tag :options="kf_ticket_status" :value="detail.ticket.status" />
            </el-descriptions-item>
            <el-descriptions-item label="标题" :span="2">{{ detail.ticket.title }}</el-descriptions-item>
            <el-descriptions-item label="内容" :span="2">
               <div style="white-space: pre-wrap">{{ detail.ticket.content }}</div>
            </el-descriptions-item>
            <el-descriptions-item label="分类">
               <dict-tag :options="kf_ticket_category" :value="detail.ticket.category" />
            </el-descriptions-item>
            <el-descriptions-item label="优先级">
               <dict-tag :options="kf_ticket_priority" :value="detail.ticket.priority" />
            </el-descriptions-item>
            <el-descriptions-item label="来源">
               <dict-tag :options="kf_ticket_source" :value="detail.ticket.source" />
            </el-descriptions-item>
            <el-descriptions-item label="处理客服">{{ handlerLabel(detail.ticket.handlerAgentId) }}</el-descriptions-item>
            <el-descriptions-item label="联系方式">{{ detail.ticket.contact || '-' }}</el-descriptions-item>
            <el-descriptions-item label="访客ID">{{ detail.ticket.visitorId || '-' }}</el-descriptions-item>
            <el-descriptions-item label="创建时间">{{ dateTimeText(detail.ticket.createTime) }}</el-descriptions-item>
            <el-descriptions-item label="解决时间">{{ dateTimeText(detail.ticket.finishTime) }}</el-descriptions-item>
            <el-descriptions-item label="关闭时间">{{ dateTimeText(detail.ticket.closeTime) }}</el-descriptions-item>
            <el-descriptions-item label="会话ID">{{ detail.ticket.sessionId || '-' }}</el-descriptions-item>
         </el-descriptions>

         <el-card shadow="never" class="kf-block" v-if="detail.ticket">
            <template #header>
               <div class="kf-push-head">
                  <span>第三方推送</span>
                  <el-button type="primary" link icon="Refresh" @click="handlePush"
                     v-hasPermi="['kf:ticket:edit']">同步</el-button>
               </div>
            </template>
            <el-descriptions :column="1" border size="small">
               <el-descriptions-item label="推送状态">
                  <el-tag size="small" :type="pushTagType(detail.ticket.pushStatus)">{{ pushStatusText(detail.ticket.pushStatus) }}</el-tag>
               </el-descriptions-item>
               <el-descriptions-item label="最近推送时间">{{ dateTimeText(detail.ticket.pushTime) || '-' }}</el-descriptions-item>
               <el-descriptions-item label="推送结果">{{ detail.ticket.pushResponse || '-' }}</el-descriptions-item>
            </el-descriptions>
            <div class="kf-sub" style="margin-top: 6px">
               推送地址、鉴权头与开关在「客服设置 → 工单第三方推送」维护；单次手动同步，不自动重试。
            </div>
         </el-card>

         <el-card shadow="never" class="kf-block">
            <template #header>流转与回复记录</template>
            <el-timeline>
               <el-timeline-item v-for="item in detail.replies" :key="item.replyId"
                  :timestamp="dateTimeText(item.createTime)" placement="top"
                  :type="item.replyType === '1' ? 'primary' : (item.replyType === '2' ? 'warning' : 'info')">
                  <div class="kf-reply-head">
                     <el-tag size="small" :type="item.replyType === '1' ? 'success' : 'info'">{{ replyTypeText(item.replyType) }}</el-tag>
                     <span class="kf-sub">{{ item.operatorName }}</span>
                     <span class="kf-sub" v-if="item.toStatus">
                        {{ statusText(item.fromStatus) }} → {{ statusText(item.toStatus) }}
                     </span>
                  </div>
                  <div class="kf-reply-body">{{ item.content }}</div>
               </el-timeline-item>
            </el-timeline>
            <el-empty v-if="!detail.replies || !detail.replies.length" description="暂无记录" />
         </el-card>

         <el-card shadow="never" class="kf-block" v-hasPermi="['kf:ticket:edit']">
            <template #header>追加回复</template>
            <el-form label-width="80px">
               <el-form-item label="回复类型">
                  <el-radio-group v-model="replyForm.replyType">
                     <el-radio value="1">对外回复</el-radio>
                     <el-radio value="3">内部备注</el-radio>
                  </el-radio-group>
               </el-form-item>
               <el-form-item label="内容">
                  <el-input v-model="replyForm.content" type="textarea" :rows="3" maxlength="2000" show-word-limit
                     placeholder="请填写回复内容" />
               </el-form-item>
               <el-form-item>
                  <el-button type="primary" :loading="writeBusy" :disabled="writeBusy || detailLoading" @click="submitReply">提交回复</el-button>
               </el-form-item>
            </el-form>
         </el-card>

         <el-card shadow="never" class="kf-block" v-if="detail.session">
            <template #header>关联会话（只读）</template>
            <el-descriptions :column="2" border size="small">
               <el-descriptions-item label="会话号">{{ detail.session.sessionNo }}</el-descriptions-item>
               <el-descriptions-item label="状态">
                  <dict-tag :options="kf_session_status" :value="detail.session.status" />
               </el-descriptions-item>
               <el-descriptions-item label="访客">{{ detail.session.visitorNick || '-' }}</el-descriptions-item>
               <el-descriptions-item label="接待客服">{{ detail.session.agentNick || '未分配' }}</el-descriptions-item>
               <el-descriptions-item label="消息数">{{ (detail.session.messages || []).length }}</el-descriptions-item>
            </el-descriptions>
            <div class="kf-msg-list">
               <div v-for="item in detail.session.messages" :key="item.msgId" class="kf-msg">
                  <span class="kf-sub">{{ dateTimeText(item.createTime) }} · {{ item.senderName }}：</span>{{ item.content }}
               </div>
            </div>
         </el-card>
      </el-drawer>
   </div>
</template>

<script setup name="KfTicket">
import { listTicket, getTicket, addTicket, updateTicket, changeTicketStatus, addTicketReply, delTicket, pushTicket } from "@/api/kf/ticket"
import { writeBusy } from '@/utils/writeBusy'
import { applyQueryFilters, rememberFilters, restoreFilters, forgetFilters } from '@/utils/listState'
import { listMonitorAgents } from "@/api/kf/monitor"
import { checkPermi } from "@/utils/permission"
import { timeText, dateTimeText } from '@/utils/time'

const { proxy } = getCurrentInstance()
const route = useRoute()
const { kf_ticket_status, kf_ticket_category, kf_ticket_priority, kf_ticket_source, kf_session_status } =
  proxy.useDict('kf_ticket_status', 'kf_ticket_category', 'kf_ticket_priority', 'kf_ticket_source', 'kf_session_status')

const TRANSITIONS = { "0": ["1", "3"], "1": ["2", "3"], "2": ["3"], "3": [] }

const ticketList = ref([])
const open = ref(false)
const statusOpen = ref(false)
const detailOpen = ref(false)
// 详情抽屉的加载中状态：驱动骨架与「操作按钮禁用」两件事
const detailLoading = ref(false)
const loading = ref(true)
// 首次加载标记：骨架只在这一帧出现，之后保持 false
const firstLoad = ref(true)
const showSearch = ref(true)
const total = ref(0)
const title = ref("")
const ids = ref([])
const selectedRows = ref([])
const dateRange = ref([])
const agentOptions = ref([])
const form = ref({})
const statusForm = ref({})
const replyForm = reactive({ content: "", replyType: "1" })
const detail = ref({})

const queryParams = reactive({
  pageNum: 1, pageSize: 10, title: undefined, status: undefined, category: undefined,
  priority: undefined, source: undefined, handlerAgentId: undefined, visitorId: undefined,
  sessionId: undefined, beginTime: undefined, endTime: undefined
})

const rules = {
  title: [{ required: true, message: "工单标题不能为空", trigger: "blur" },
    { max: 100, message: "工单标题最多 100 个字符", trigger: "blur" }],
  content: [{ required: true, message: "工单内容不能为空", trigger: "blur" }]
}

// 筛选记忆与 URL 落地覆盖的字段（页码不记）
const FILTER_FIELDS = ['title', 'status', 'category', 'priority', 'source', 'handlerAgentId', 'visitorId', 'sessionId']
const FILTER_CAST = { handlerAgentId: Number, visitorId: Number, sessionId: Number }
// 落地顺序固定为「先恢复记忆、再落 URL 参数」，URL 才是最终优先级：
// 离线留言点工单号（传的是 ticketNo，后端关键字入参叫 title，标题/工单号/访客三合一模糊匹配，这里翻译一下）、
// 工单详情点关联会话，带的条件不能被本地旧快照盖掉
restoreFilters('kf.ticket', queryParams, FILTER_FIELDS)
applyQueryFilters(queryParams, { ...route.query, title: route.query.title || route.query.ticketNo }, FILTER_CAST)
if (route.query.beginTime) {
  dateRange.value = [route.query.beginTime, route.query.endTime || route.query.beginTime]
}

const nextStatusOptions = computed(() => {
  const allowed = TRANSITIONS[statusForm.value.status] || []
  return (kf_ticket_status.value || []).filter(dict => allowed.includes(dict.value))
})

/** 行内下拉用的「这一步能去哪」，与流转弹窗同一张状态机表，不另立规则。 */
function nextOf(status) {
  const allowed = TRANSITIONS[status] || []
  return (kf_ticket_status.value || []).filter(dict => allowed.includes(dict.value))
}

function priorityTag(priority) {
  return priority === "3" ? "danger" : (priority === "2" ? "warning" : "info")
}

function handlerLabel(agentId) {
  if (!agentId) {
    return "未指派"
  }
  const hit = agentOptions.value.find(item => item.agentId === agentId)
  return hit ? hit.agentNick : "客服ID " + agentId
}

function dictLabel(options, value) {
  const hit = (options || []).find(item => item.value === value)
  return hit ? hit.label : (value || "-")
}

function statusText(value) {
  return value ? dictLabel(kf_ticket_status.value, value) : ""
}

function replyTypeText(type) {
  return type === "1" ? "对外回复" : (type === "2" ? "状态流转" : "内部备注")
}

function pushStatusText(value) {
  return value === "1" ? "已推送" : (value === "2" ? "推送失败" : "未推送")
}

function pushTagType(value) {
  return value === "1" ? "success" : (value === "2" ? "danger" : "info")
}

// 单次手动同步：结果由后端写回工单，这里只负责提示与刷新详情
function handlePush() {
  const ticketId = detail.value.ticket && detail.value.ticket.ticketId
  if (!ticketId) {
    return
  }
  pushTicket(ticketId).then(response => {
    const data = response.data || {}
    if (data.pushStatus === "1") {
      proxy.$modal.msgSuccess("已同步：" + (data.message || "HTTP 成功"))
    } else {
      proxy.$modal.msgError("推送失败：" + (data.message || "请检查推送地址与鉴权头"))
    }
    loadDetail(ticketId)
  })
}

function getList() {
  queryParams.beginTime = dateRange.value && dateRange.value.length ? dateRange.value[0] : undefined
  queryParams.endTime = dateRange.value && dateRange.value.length ? dateRange.value[1] : undefined
  rememberFilters('kf.ticket', queryParams, FILTER_FIELDS)
  loading.value = true
  listTicket(queryParams).then(response => {
    ticketList.value = response.rows || []
    total.value = response.total || 0
  }).finally(() => {
    loading.value = false
    firstLoad.value = false
  })
}

function handleQuery() {
  queryParams.pageNum = 1
  getList()
}

function resetQuery() {
  proxy.resetForm("queryRef")
  dateRange.value = []
  // 清掉本地记忆，否则「重置」之后刷新页面又回到旧筛选
  forgetFilters('kf.ticket')
  handleQuery()
}

function handleSelectionChange(selection) {
  ids.value = selection.map(item => item.ticketId)
  selectedRows.value = selection
}

/**
 * 行内直接流转到某个目标状态（功能点19）：绝大多数流转就是「待处理→处理中」这一步，
 * 原先每次都要开弹窗、选目标、点确定，三击变一击；需要写流转说明时仍走「流转」弹窗。
 */
function quickStatus(row, target) {
  changeTicketStatus(row.ticketId, target).then(() => {
    proxy.$modal.msgSuccess(`已流转为${dictLabel(kf_ticket_status.value, target)}`)
    getList()
  })
}

/** 批量流转：后端没有批量端点，这里按已授权的单条接口逐条调用，结果一次说清。 */
function handleBatchStatus(target) {
  const rows = (selectedRows.value || []).filter(row => (TRANSITIONS[row.status] || []).includes(target))
  if (!rows.length) {
    proxy.$modal.msgError(`选中的工单里没有可流转为「${dictLabel(kf_ticket_status.value, target)}」的`)
    return
  }
  proxy.$modal.confirm(`确认把选中的 ${rows.length} 条工单流转为「${dictLabel(kf_ticket_status.value, target)}」？`).then(() => {
    return Promise.all(rows.map(row => changeTicketStatus(row.ticketId, target).catch(e => e)))
  }).then(results => {
    const failed = results.filter(item => item instanceof Error)
    if (failed.length) {
      proxy.$modal.msgWarning(`${results.length - failed.length} 条成功，${failed.length} 条失败（状态已变或无权处理）`)
    } else {
      proxy.$modal.msgSuccess(`${results.length} 条已流转`)
    }
    getList()
  }).catch(() => {})
}

/**
 * 导出走服务端：一次导全部筛选结果（前端 CSV 只能导当前页），
 * 且每次导出都带 @Log(EXPORT) 留痕——谁把含联系方式的业务数据带走了，操作日志与业务审计各有一条。
 */
function handleExport() {
  proxy.download('kf/ticket/export', {
    title: queryParams.title, status: queryParams.status, category: queryParams.category,
    priority: queryParams.priority, source: queryParams.source, handlerAgentId: queryParams.handlerAgentId,
    visitorId: queryParams.visitorId, sessionId: queryParams.sessionId,
    beginTime: queryParams.beginTime, endTime: queryParams.endTime
  }, `工单_${new Date().getTime()}.xlsx`)
}

function resetForm() {
  form.value = { ticketId: undefined, title: undefined, content: undefined, category: "other",
    priority: "2", handlerAgentId: undefined, contact: undefined, sessionId: undefined }
  proxy.resetForm("ticketRef")
}

function cancel() {
  open.value = false
  resetForm()
}

function handleAdd() {
  resetForm()
  open.value = true
  title.value = "新建工单"
}

function handleUpdate(row) {
  resetForm()
  getTicket(row.ticketId).then(response => {
    form.value = { ...response.ticket }
    open.value = true
    title.value = "修改工单"
  })
}

function submitForm() {
  proxy.$refs["ticketRef"].validate(valid => {
    if (!valid) {
      return
    }
    if (form.value.ticketId) {
      updateTicket(form.value).then(() => {
        proxy.$modal.msgSuccess("修改成功")
        open.value = false
        getList()
      })
    } else {
      addTicket(form.value).then(() => {
        proxy.$modal.msgSuccess("新增成功")
        open.value = false
        getList()
      })
    }
  })
}

function handleStatus(row) {
  statusForm.value = { ticketId: row.ticketId, ticketNo: row.ticketNo, title: row.title,
    status: row.status, targetStatus: (TRANSITIONS[row.status] || [])[0], note: undefined }
  statusOpen.value = true
}

function submitStatus() {
  if (!statusForm.value.targetStatus) {
    proxy.$modal.msgError("请选择目标状态")
    return
  }
  changeTicketStatus(statusForm.value.ticketId, statusForm.value.targetStatus, statusForm.value.note).then(() => {
    proxy.$modal.msgSuccess("流转成功")
    statusOpen.value = false
    getList()
    if (detailOpen.value) {
      loadDetail(statusForm.value.ticketId)
    }
  })
}

function loadDetail(ticketId) {
  detailLoading.value = true
  getTicket(ticketId).then(response => {
    detail.value = response
  }).finally(() => {
    detailLoading.value = false
  })
}

function handleDetail(row) {
  replyForm.content = ""
  replyForm.replyType = "1"
  loadDetail(row.ticketId)
  detailOpen.value = true
}

function submitReply() {
  if (!replyForm.content || !replyForm.content.trim()) {
    proxy.$modal.msgError("回复内容不能为空")
    return
  }
  addTicketReply(detail.value.ticket.ticketId, replyForm.content, replyForm.replyType).then(() => {
    proxy.$modal.msgSuccess("回复成功")
    replyForm.content = ""
    loadDetail(detail.value.ticket.ticketId)
    getList()
  })
}

function handleDelete(row) {
  proxy.$modal.confirmDanger('只有已解决或已关闭的工单能删。删除后这条工单及其回复记录不再可查，关联的会话记录仍然保留。', { title: '删除工单', okText: '删除' }).then(function () {
    return delTicket(row.ticketId)
  }).then(() => {
    getList()
    proxy.$modal.msgSuccess("删除成功")
  }).catch(() => {})
}

if (checkPermi(['kf:monitor:query'])) {
  listMonitorAgents({}).then(response => {
    agentOptions.value = response.data || []
  })
}

getList()
</script>

<style scoped>
.kf-sub {
  font-size: 12px;
  color: var(--el-text-color-placeholder);
}
.kf-batch-hint {
  margin: -4px 0 8px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.kf-quick-status {
  margin-left: 6px;
}
.kf-block {
  margin-top: 12px;
}
.kf-push-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.kf-reply-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 2px;
}
.kf-reply-body {
  white-space: pre-wrap;
}
.kf-msg-list {
  margin-top: 8px;
  max-height: 30vh;
  overflow-y: auto;
}
.kf-msg {
  font-size: 13px;
  padding: 3px 0;
  border-bottom: 1px dashed var(--el-border-color-lighter);
}
</style>
