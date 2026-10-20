<template>
   <div class="app-container">
      <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch">
         <el-form-item label="参数名称" prop="configName">
            <el-input v-model="queryParams.configName" placeholder="请输入参数名称" clearable style="width: 200px" @keyup.enter="handleQuery" />
         </el-form-item>
         <el-form-item label="参数键名" prop="configKey">
            <el-input v-model="queryParams.configKey" placeholder="请输入参数键名" clearable style="width: 220px" @keyup.enter="handleQuery" />
         </el-form-item>
         <el-form-item label="系统内置" prop="configType">
            <el-select v-model="queryParams.configType" placeholder="全部" clearable style="width: 140px" @change="handleQuery">
               <el-option label="是" value="0" />
               <el-option label="否" value="1" />
            </el-select>
         </el-form-item>
         <el-form-item>
            <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
            <el-button icon="Refresh" @click="resetQuery">重置</el-button>
         </el-form-item>
      </el-form>

      <el-row :gutter="10" class="mb8">
         <el-col :span="1.5">
            <el-button type="primary" plain icon="Plus" @click="handleAdd" v-hasPermi="['kf:config:add']">新增</el-button>
         </el-col>
         <el-col :span="1.5">
            <el-button type="warning" plain icon="Refresh" @click="handleRefreshCache" v-hasPermi="['kf:config:remove']">刷新缓存</el-button>
         </el-col>
         <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
      </el-row>

      <el-table v-loading="loading" :data="configList">
         <el-table-column label="ID" align="center" prop="configId" width="70" />
         <el-table-column label="参数名称" align="center" prop="configName" width="200" show-overflow-tooltip />
         <el-table-column label="参数键名" align="center" prop="configKey" width="230" show-overflow-tooltip />
         <el-table-column label="参数键值" align="center" prop="configValue" show-overflow-tooltip />
         <el-table-column label="内置" align="center" prop="configType" width="80">
            <template #default="scope">
               <el-tag :type="scope.row.configType === '0' ? 'primary' : 'info'">{{ scope.row.configType === '0' ? '是' : '否' }}</el-tag>
            </template>
         </el-table-column>
         <el-table-column label="备注" align="center" prop="remark" width="220" show-overflow-tooltip />
         <el-table-column label="操作" align="center" width="160" class-name="small-padding fixed-width">
            <template #default="scope">
               <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['kf:config:edit']">修改</el-button>
               <el-button link type="primary" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['kf:config:remove']">删除</el-button>
            </template>
         </el-table-column>

      <!-- 统一空状态（阶段8 功能点22） -->
      <template #empty>
        <empty-state description="暂无客服参数" hint="参数用于配置欢迎语、排队上限等运营项" />
      </template>
      </el-table>

      <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

      <el-dialog :title="title" v-model="open" width="620px" append-to-body>
         <el-form ref="configRef" :model="form" :rules="rules" label-width="90px">
            <el-row>
               <el-col :span="12">
                  <el-form-item label="参数名称" prop="configName">
                     <el-input v-model="form.configName" placeholder="请输入参数名称" maxlength="100" />
                  </el-form-item>
               </el-col>
               <el-col :span="12">
                  <el-form-item label="参数键名" prop="configKey">
                     <el-input v-model="form.configKey" placeholder="如 kf.welcome.text" maxlength="100" :disabled="form.configId != undefined && form.configType === '0'" />
                  </el-form-item>
               </el-col>
               <el-col :span="24">
                  <el-form-item label="参数键值" prop="configValue">
                     <el-input v-model="form.configValue" type="textarea" :rows="3" placeholder="请输入参数键值" maxlength="2000" show-word-limit />
                  </el-form-item>
               </el-col>
               <el-col :span="12">
                  <el-form-item label="系统内置">
                     <el-radio-group v-model="form.configType">
                        <el-radio value="0">是</el-radio>
                        <el-radio value="1">否</el-radio>
                     </el-radio-group>
                  </el-form-item>
               </el-col>
               <el-col :span="24">
                  <el-form-item label="备注" prop="remark">
                     <el-input v-model="form.remark" type="textarea" placeholder="说明该参数影响的业务行为" />
                  </el-form-item>
               </el-col>
            </el-row>
         </el-form>
         <template #footer>
            <div class="dialog-footer">
               <el-button type="primary" @click="submitForm">确 定</el-button>
               <el-button @click="cancel">取 消</el-button>
            </div>
         </template>
      </el-dialog>
   </div>
</template>

<script setup name="KfConfig">
import { listKfConfig, getKfConfig, delKfConfig, addKfConfig, updateKfConfig, refreshKfConfigCache } from "@/api/kf/config"
import { rememberFilters, restoreFilters, forgetFilters } from '@/utils/listState'

const { proxy } = getCurrentInstance()

const configList = ref([])
const open = ref(false)
const loading = ref(true)
const showSearch = ref(true)
const total = ref(0)
const title = ref("")

const data = reactive({
  form: {},
  queryParams: { pageNum: 1, pageSize: 10, configName: undefined, configKey: undefined, configType: undefined },
  rules: {
    configName: [{ required: true, message: "参数名称不能为空", trigger: "blur" }],
    configKey: [{ required: true, message: "参数键名不能为空", trigger: "blur" }],
    configValue: [{ required: true, message: "参数键值不能为空", trigger: "blur" }]
  }
})
const { queryParams, form, rules } = toRefs(data)

function getList() {
  rememberFilters('kf.config', queryParams.value, FILTER_FIELDS)
  loading.value = true
  listKfConfig(queryParams.value).then(response => {
    configList.value = response.rows
    total.value = response.total
    loading.value = false
  })
}

function cancel() {
  open.value = false
  reset()
}

function reset() {
  form.value = { configId: undefined, configName: undefined, configKey: undefined, configValue: undefined, configType: "1", remark: undefined }
  proxy.resetForm("configRef")
}

function handleQuery() {
  queryParams.value.pageNum = 1
  getList()
}

function resetQuery() {
  forgetFilters('kf.config')
  proxy.resetForm("queryRef")
  handleQuery()
}

function handleAdd() {
  reset()
  open.value = true
  title.value = "添加客服参数"
}

function handleUpdate(row) {
  reset()
  getKfConfig(row.configId).then(response => {
    form.value = response.data
    open.value = true
    title.value = "修改客服参数"
  })
}

function submitForm() {
  proxy.$refs["configRef"].validate(valid => {
    if (valid) {
      if (form.value.configId != undefined) {
        updateKfConfig(form.value).then(() => {
          proxy.$modal.msgSuccess("修改成功")
          open.value = false
          getList()
        })
      } else {
        addKfConfig(form.value).then(() => {
          proxy.$modal.msgSuccess("新增成功")
          open.value = false
          getList()
        })
      }
    }
  })
}

function handleDelete(row) {
  proxy.$modal.confirmDanger('删除后该参数恢复代码里的默认值，正在读它的功能会立刻改变行为。', { title: '删除参数', okText: '删除' }).then(function () {
    return delKfConfig(row.configId)
  }).then(() => {
    getList()
    proxy.$modal.msgSuccess("删除成功")
  }).catch(() => {})
}

function handleRefreshCache() {
  refreshKfConfigCache().then(() => {
    proxy.$modal.msgSuccess("刷新成功")
  })
}

// 筛选条件与时间范围记住上次的（功能点30）：从详情或别的页回来不必再筛一遍
const FILTER_FIELDS = ['configName', 'configKey', 'configType']
restoreFilters('kf.config', queryParams.value, FILTER_FIELDS)
getList()
</script>
