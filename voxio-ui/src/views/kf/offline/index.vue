<template>
   <div class="app-container">
      <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch">
         <el-form-item label="处理状态" prop="status">
            <el-select v-model="queryParams.status" placeholder="全部" clearable style="width: 130px" @change="handleQuery">
               <el-option v-for="dict in kf_offline_status" :key="dict.value" :label="dict.label" :value="dict.value" />
            </el-select>
         </el-form-item>
         <el-form-item label="处理客服" prop="handlerAgentId">
            <el-select v-model="queryParams.handlerAgentId" placeholder="全部" clearable style="width: 140px" @change="handleQuery">
               <el-option v-for="item in agentOptions" :key="item.agentId" :label="item.agentNick" :value="item.agentId" />
            </el-select>
         </el-form-item>
         <el-form-item label="关键字" prop="keyword">
            <el-input v-model="queryParams.keyword" placeholder="访客昵称 / 访客编号" clearable style="width: 220px" @keyup.enter="handleQuery" />
         </el-form-item>
         <el-form-item label="提交时间">
            <el-date-picker v-model="dateRange" type="daterange" value-format="YYYY-MM-DD" range-separator="-" @change="handleQuery"
               start-placeholder="开始日期" end-placeholder="结束日期" style="width: 240px" />
         </el-form-item>
         <el-form-item>
            <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
            <el-button icon="Refresh" @click="resetQuery">重置</el-button>
         </el-form-item>
      </el-form>

      <el-row :gutter="10" class="mb8">
         <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
      </el-row>

      <el-table v-loading="loading" :data="offlineList">
         <el-table-column label="留言ID" align="center" prop="msgId" width="90" />
         <el-table-column label="访客" align="center" width="150" show-overflow-tooltip>
            <template #default="scope">
               <div>{{ scope.row.visitorNick || '匿名访客' }}</div>
               <div class="kf-sub">{{ scope.row.visitorNo }}</div>
            </template>
         </el-table-column>
         <el-table-column label="联系方式" align="center" prop="contact" width="150" show-overflow-tooltip />
         <el-table-column label="留言内容" align="center" prop="content" min-width="240" show-overflow-tooltip />
         <el-table-column label="关联会话" align="center" width="140" show-overflow-tooltip>
            <template #default="scope">{{ scope.row.sessionNo || '-' }}</template>
         </el-table-column>
         <el-table-column label="状态" align="center" width="110">
            <template #default="scope">
               <dict-tag :options="kf_offline_status" :value="scope.row.status" />
            </template>
         </el-table-column>
         <el-table-column label="转入工单" align="center" width="150" show-overflow-tooltip>
            <template #default="scope">
               <!-- 工单页的真实路由是 /ticket/work（菜单挂在「工单处理」目录下），原先写 /kf/ticket 是个打不开的地址 -->
               <router-link v-if="scope.row.ticketNo" :to="'/ticket/work?ticketNo=' + scope.row.ticketNo" class="kf-link">
                  {{ scope.row.ticketNo }}
               </router-link>
               <span v-else>-</span>
            </template>
         </el-table-column>
         <el-table-column label="处理客服" align="center" width="110">
            <template #default="scope">{{ scope.row.handlerNick || '-' }}</template>
         </el-table-column>
         <el-table-column label="提交时间" align="center" width="150">
            <template #default="scope">{{ timeText(scope.row.createTime) }}</template>
         </el-table-column>
         <el-table-column label="操作" align="center" width="250" class-name="small-padding fixed-width">
            <template #default="scope">
               <el-button link type="primary" icon="View" @click="handleDetail(scope.row)" v-hasPermi="['kf:offline:query']">详情</el-button>
               <el-button link type="primary" icon="Tickets" @click="handleToTicket(scope.row)"
                  v-if="scope.row.status === '0'" v-hasPermi="['kf:offline:toticket']">转工单</el-button>
               <el-button link type="primary" icon="CircleClose" @click="handleClose(scope.row)"
                  v-if="scope.row.status === '0'" v-hasPermi="['kf:offline:close']">关闭</el-button>
            </template>
         </el-table-column>

      <!-- 统一空状态（阶段8 功能点22） -->
      <template #empty>
        <empty-state description="暂无离线留言" hint="非服务时间的访客留言会汇总到这里" />
      </template>
      </el-table>

      <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

      <el-dialog title="留言详情" v-model="detailOpen" width="640px" append-to-body>
         <el-descriptions :column="2" border>
            <el-descriptions-item label="留言ID">{{ form.msgId }}</el-descriptions-item>
            <el-descriptions-item label="状态">
               <dict-tag :options="kf_offline_status" :value="form.status" />
            </el-descriptions-item>
            <el-descriptions-item label="访客">{{ form.visitorNick || '匿名访客' }}</el-descriptions-item>
            <el-descriptions-item label="访客编号">{{ form.visitorNo }}</el-descriptions-item>
            <el-descriptions-item label="联系方式">{{ form.contact || '-' }}</el-descriptions-item>
            <el-descriptions-item label="关联会话">{{ form.sessionNo || '-' }}</el-descriptions-item>
            <el-descriptions-item label="转入工单">{{ form.ticketNo || '-' }}</el-descriptions-item>
            <el-descriptions-item label="处理客服">{{ form.handlerNick || '-' }}</el-descriptions-item>
            <el-descriptions-item label="提交时间">{{ dateTimeText(form.createTime) }}</el-descriptions-item>
            <el-descriptions-item label="更新时间">{{ dateTimeText(form.updateTime) }}</el-descriptions-item>
            <el-descriptions-item label="留言内容" :span="2">
               <div style="white-space: pre-wrap">{{ form.content }}</div>
            </el-descriptions-item>
         </el-descriptions>
         <template #footer>
            <div class="dialog-footer">
               <el-button @click="detailOpen = false">关 闭</el-button>
            </div>
         </template>
      </el-dialog>

      <el-dialog title="留言转工单" v-model="ticketOpen" width="560px" append-to-body>
         <el-form label-width="90px">
            <el-form-item label="留言内容">
               <div class="kf-brief">{{ ticketForm.content }}</div>
            </el-form-item>
            <el-form-item label="工单标题">
               <el-input v-model="ticketForm.title" placeholder="留空则按留言内容自动生成" maxlength="100" show-word-limit />
            </el-form-item>
            <el-form-item label="指派客服">
               <el-select v-model="ticketForm.handlerAgentId" placeholder="暂不指派" clearable style="width: 100%">
                  <el-option v-for="item in agentOptions" :key="item.agentId" :label="item.agentNick" :value="item.agentId" />
               </el-select>
            </el-form-item>
         </el-form>
         <template #footer>
            <div class="dialog-footer">
               <el-button type="primary" @click="submitToTicket">确 定</el-button>
               <el-button @click="ticketOpen = false">取 消</el-button>
            </div>
         </template>
      </el-dialog>
   </div>
</template>

<script setup name="KfOffline">
import { listOffline, getOffline, offlineToTicket, closeOffline } from "@/api/kf/offline"
import { listMonitorAgents } from "@/api/kf/monitor"
import { checkPermi } from "@/utils/permission"
import { timeText, dateTimeText } from '@/utils/time'
import { applyQueryFilters, rememberFilters, restoreFilters, forgetFilters } from '@/utils/listState'

const { proxy } = getCurrentInstance()
const route = useRoute()
const { kf_offline_status } = proxy.useDict('kf_offline_status')

const offlineList = ref([])
const loading = ref(true)
const showSearch = ref(true)
const total = ref(0)
const dateRange = ref([])
const agentOptions = ref([])
const detailOpen = ref(false)
const ticketOpen = ref(false)
const form = ref({})
const ticketForm = ref({})

const queryParams = reactive({
  pageNum: 1, pageSize: 10, status: undefined, handlerAgentId: undefined,
  keyword: undefined, beginTime: undefined, endTime: undefined
})

function getList() {
  rememberFilters('kf.offline', queryParams, FILTER_FIELDS)
  queryParams.beginTime = dateRange.value && dateRange.value.length ? dateRange.value[0] : undefined
  queryParams.endTime = dateRange.value && dateRange.value.length ? dateRange.value[1] : undefined
  loading.value = true
  listOffline(queryParams).then(response => {
    offlineList.value = response.rows || []
    total.value = response.total || 0
  }).finally(() => {
    loading.value = false
  })
}

function handleQuery() {
  queryParams.pageNum = 1
  getList()
}

function resetQuery() {
  forgetFilters('kf.offline')
  proxy.resetForm("queryRef")
  dateRange.value = []
  handleQuery()
}

function handleDetail(row) {
  getOffline(row.msgId).then(response => {
    form.value = response.data || {}
    detailOpen.value = true
  })
}

function handleToTicket(row) {
  ticketForm.value = { msgId: row.msgId, content: row.content, title: undefined, handlerAgentId: undefined }
  ticketOpen.value = true
}

function submitToTicket() {
  offlineToTicket(ticketForm.value.msgId, {
    title: ticketForm.value.title, handlerAgentId: ticketForm.value.handlerAgentId
  }).then(response => {
    proxy.$modal.msgSuccess("已转工单 " + (response.data ? response.data.ticketNo : ""))
    ticketOpen.value = false
    getList()
  })
}

function handleClose(row) {
  proxy.$modal.confirmDanger('关闭后这条留言不再转工单跟进，内容与访客联系方式仍可在列表里查到。', { title: '关闭留言', okText: '关闭留言' }).then(function () {
    return closeOffline(row.msgId)
  }).then(() => {
    getList()
    proxy.$modal.msgSuccess("已关闭")
  }).catch(() => {})
}

if (checkPermi(['kf:monitor:query'])) {
  listMonitorAgents({}).then(response => {
    agentOptions.value = response.data || []
  })
}

// 带 status=0 跳过来时，落到列表就是那几条没处理的留言
const FILTER_FIELDS = ['status', 'handlerAgentId', 'keyword', 'beginTime', 'endTime']
restoreFilters('kf.offline', queryParams, FILTER_FIELDS)
if (queryParams.beginTime && !dateRange.value.length) {
  dateRange.value = [queryParams.beginTime, queryParams.endTime]
}
applyQueryFilters(queryParams, route.query, { handlerAgentId: Number })

getList()
</script>

<style scoped>
.kf-sub {
  font-size: 12px;
  color: var(--el-text-color-placeholder);
}
.kf-link {
  color: var(--el-color-primary);
}
.kf-brief {
  max-height: 90px;
  overflow-y: auto;
  white-space: pre-wrap;
  font-size: 13px;
  color: var(--el-text-color-regular);
}
</style>
