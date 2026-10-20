<template>
   <div class="app-container">
      <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch">
         <el-form-item label="关键字" prop="keyword">
            <el-input v-model="queryParams.keyword" placeholder="对象标识 / IP / 操作人ID" clearable style="width: 220px" @keyup.enter="handleQuery" />
         </el-form-item>
         <el-form-item label="来源" prop="userType">
            <el-select v-model="queryParams.userType" placeholder="全部" clearable style="width: 130px" @change="handleQuery">
               <el-option label="管理后台" value="admin" />
               <el-option label="客服端" value="agent" />
            </el-select>
         </el-form-item>
         <el-form-item label="操作" prop="operation">
            <el-input v-model="queryParams.operation" placeholder="如 客服建单" clearable style="width: 180px" @keyup.enter="handleQuery" />
         </el-form-item>
         <el-form-item label="操作时间">
            <el-date-picker v-model="dateRange" type="daterange" value-format="YYYY-MM-DD" range-separator="-" @change="handleQuery"
               start-placeholder="开始日期" end-placeholder="结束日期" style="width: 240px" />
         </el-form-item>
         <el-form-item>
            <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
            <el-button icon="Refresh" @click="resetQuery">重置</el-button>
         </el-form-item>
      </el-form>

      <el-row :gutter="10" class="mb8">
         <el-col :span="24">
            <span class="kf-sub">写操作留痕只增不删：应用侧没有删除与修改入口，超出保留期（≥180 天）才由定时任务收敛；请求参数里的口令、手机号等已按字段打码。</span>
         </el-col>
         <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
      </el-row>

      <el-table v-loading="loading" :data="logList">
         <el-table-column label="审计ID" align="center" prop="logId" width="90" />
         <el-table-column label="来源" align="center" width="90">
            <template #default="scope">
               <el-tag size="small" :type="scope.row.userType === 'agent' ? 'warning' : 'info'">
                  {{ userTypeText(scope.row.userType) }}
               </el-tag>
            </template>
         </el-table-column>
         <el-table-column label="操作人" align="center" prop="userId" width="90" />
         <el-table-column label="操作" align="center" prop="operation" min-width="180" show-overflow-tooltip />
         <el-table-column label="对象" align="center" min-width="150" show-overflow-tooltip>
            <template #default="scope">{{ scope.row.targetType || '-' }}{{ scope.row.targetId ? ' #' + scope.row.targetId : '' }}</template>
         </el-table-column>
         <el-table-column label="结果" align="center" min-width="150" show-overflow-tooltip>
            <template #default="scope">
               <el-tag size="small" :type="resultTone(scope.row.result)">
                  {{ scope.row.result || '-' }}
               </el-tag>
            </template>
         </el-table-column>
         <el-table-column label="IP" align="center" prop="ip" width="130" show-overflow-tooltip />
         <el-table-column label="请求参数" align="center" prop="requestParams" min-width="240" show-overflow-tooltip>
            <template #default="scope">{{ scope.row.requestParams || '-' }}</template>
         </el-table-column>
         <el-table-column label="操作时间" align="center" width="170">
            <template #default="scope">{{ dateTimeText(scope.row.createTime) }}</template>
         </el-table-column>

      <!-- 统一空状态（阶段8 功能点22） -->
      <template #empty>
        <empty-state description="暂无审计留痕" hint="客服端与管理后台的写操作都会记在这里" />
      </template>
      </el-table>

      <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />
   </div>
</template>

<script setup name="KfAuditLog">
import { listAuditLog } from "@/api/kf/auditlog"
import { rememberFilters, restoreFilters, forgetFilters } from '@/utils/listState'
import { dateTimeText } from '@/utils/time'

const { proxy } = getCurrentInstance()

const logList = ref([])
const loading = ref(true)
const showSearch = ref(true)
const total = ref(0)
const dateRange = ref([])

const queryParams = reactive({
  pageNum: 1, pageSize: 10, keyword: undefined, userType: undefined, operation: undefined, beginTime: undefined, endTime: undefined
})

function userTypeText(value) {
  return value === 'agent' ? '客服端' : value === 'admin' ? '管理后台' : (value || '-')
}

// HTTP 200 但业务码非 200 也算失败，留痕里要一眼看出来
function resultTone(result) {
  const text = String(result || '')
  return text.startsWith('fail') || (text.includes('code=') && !text.includes('code=200')) ? 'danger' : 'success'
}

function getList() {
  rememberFilters('kf.auditlog', queryParams, FILTER_FIELDS)
  queryParams.beginTime = dateRange.value && dateRange.value.length ? dateRange.value[0] : undefined
  queryParams.endTime = dateRange.value && dateRange.value.length ? dateRange.value[1] : undefined
  loading.value = true
  listAuditLog(queryParams).then(response => {
    logList.value = response.rows || []
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
  forgetFilters('kf.auditlog')
  proxy.resetForm("queryRef")
  dateRange.value = []
  handleQuery()
}

// 筛选条件记住上次的（阶段13 功能点30）
const FILTER_FIELDS = ['keyword', 'userType', 'operation', 'beginTime', 'endTime']
restoreFilters('kf.auditlog', queryParams, FILTER_FIELDS)
if (queryParams.beginTime && !dateRange.value.length) {
  dateRange.value = [queryParams.beginTime, queryParams.endTime]
}
getList()
</script>

<style scoped>
.kf-sub {
  font-size: 12px;
  color: var(--sb-color-text-muted);
  line-height: 32px;
}
</style>
