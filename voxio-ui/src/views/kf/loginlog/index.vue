<template>
   <div class="app-container">
      <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch">
         <el-form-item label="关键字" prop="keyword">
            <el-input v-model="queryParams.keyword" placeholder="登录账号 / IP" clearable style="width: 220px" @keyup.enter="handleQuery" />
         </el-form-item>
         <el-form-item label="结果" prop="status">
            <el-select v-model="queryParams.status" placeholder="全部" clearable style="width: 120px" @change="handleQuery">
               <el-option label="成功" value="0" />
               <el-option label="失败" value="1" />
            </el-select>
         </el-form-item>
         <el-form-item label="登录时间">
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
            <span class="kf-sub">登录取消图形验证码后，这份留痕是唯一的爆破追溯线索；记录只读，按保留期 180 天自动清理。</span>
         </el-col>
         <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
      </el-row>

      <el-table v-loading="loading" :data="logList">
         <el-table-column label="日志ID" align="center" prop="logId" width="90" />
         <el-table-column label="登录账号" align="center" prop="agentNo" width="150" show-overflow-tooltip />
         <el-table-column label="结果" align="center" width="90">
            <template #default="scope">
               <el-tag size="small" :type="scope.row.status === '0' ? 'success' : 'danger'">
                  {{ scope.row.status === '0' ? '成功' : '失败' }}
               </el-tag>
            </template>
         </el-table-column>
         <el-table-column label="说明" align="center" prop="msg" min-width="220" show-overflow-tooltip />
         <el-table-column label="IP" align="center" prop="loginIp" width="150" show-overflow-tooltip />
         <el-table-column label="客户端" align="center" prop="loginUa" min-width="220" show-overflow-tooltip>
            <template #default="scope">{{ scope.row.loginUa || '-' }}</template>
         </el-table-column>
         <el-table-column label="登录时间" align="center" width="170">
            <template #default="scope">{{ dateTimeText(scope.row.loginTime) }}</template>
         </el-table-column>

      <!-- 统一空状态（阶段8 功能点22） -->
      <template #empty>
        <empty-state description="暂无登录记录" hint="客服端每次登录的成功与失败都会记在这里" />
      </template>
      </el-table>

      <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />
   </div>
</template>

<script setup name="KfLoginLog">
import { listLoginLog } from "@/api/kf/loginlog"
import { rememberFilters, restoreFilters, forgetFilters } from '@/utils/listState'
import { dateTimeText } from '@/utils/time'

const { proxy } = getCurrentInstance()

const logList = ref([])
const loading = ref(true)
const showSearch = ref(true)
const total = ref(0)
const dateRange = ref([])

const queryParams = reactive({
  pageNum: 1, pageSize: 10, keyword: undefined, status: undefined, beginTime: undefined, endTime: undefined
})

function getList() {
  rememberFilters('kf.loginlog', queryParams, FILTER_FIELDS)
  queryParams.beginTime = dateRange.value && dateRange.value.length ? dateRange.value[0] : undefined
  queryParams.endTime = dateRange.value && dateRange.value.length ? dateRange.value[1] : undefined
  loading.value = true
  listLoginLog(queryParams).then(response => {
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
  forgetFilters('kf.loginlog')
  proxy.resetForm("queryRef")
  dateRange.value = []
  handleQuery()
}

// 筛选条件记住上次的（阶段13 功能点30）
const FILTER_FIELDS = ['keyword', 'status', 'beginTime', 'endTime']
restoreFilters('kf.loginlog', queryParams, FILTER_FIELDS)
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
