<template>
   <div class="app-container">
      <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch">
         <el-form-item label="客服账号" prop="agentNo">
            <el-input
               v-model="queryParams.agentNo"
               placeholder="请输入客服登录账号"
               clearable
               style="width: 200px"
               @keyup.enter="handleQuery"
            />
         </el-form-item>
         <el-form-item label="客服昵称" prop="nickName">
            <el-input
               v-model="queryParams.nickName"
               placeholder="请输入客服昵称"
               clearable
               style="width: 200px"
               @keyup.enter="handleQuery"
            />
         </el-form-item>
         <el-form-item label="手机号码" prop="phone">
            <el-input
               v-model="queryParams.phone"
               placeholder="请输入手机号码"
               clearable
               style="width: 200px"
               @keyup.enter="handleQuery"
            />
         </el-form-item>
         <el-form-item label="在线状态" prop="onlineStatus">
            <el-select v-model="queryParams.onlineStatus" placeholder="在线状态" clearable style="width: 200px" @change="handleQuery">
               <el-option
                  v-for="dict in kf_online_status"
                  :key="dict.value"
                  :label="dict.label"
                  :value="dict.value"
               />
            </el-select>
         </el-form-item>
         <el-form-item label="账号状态" prop="status">
            <el-select v-model="queryParams.status" placeholder="账号状态" clearable style="width: 200px" @change="handleQuery">
               <el-option
                  v-for="dict in sys_normal_disable"
                  :key="dict.value"
                  :label="dict.label"
                  :value="dict.value"
               />
            </el-select>
         </el-form-item>
         <el-form-item>
            <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
            <el-button icon="Refresh" @click="resetQuery">重置</el-button>
         </el-form-item>
      </el-form>

      <el-row :gutter="10" class="mb8">
         <el-col :span="1.5">
            <el-button
               type="primary"
               plain
               icon="Plus"
               @click="handleAdd"
               v-hasPermi="['kf:agent:add']"
            >新增</el-button>
         </el-col>
         <el-col :span="1.5">
            <el-button
               type="danger"
               plain
               icon="Delete"
               :disabled="multiple"
               @click="handleDelete"
               v-hasPermi="['kf:agent:remove']"
            >删除</el-button>
         </el-col>
         <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
      </el-row>

      <!-- 阶段12 功能点1：首次进页面是骨架，二次加载只盖一层轻量 loading，不做整屏遮罩 -->
      <table-skeleton v-if="firstLoad" :rows="6" />
      <el-table v-else v-loading="loading" :data="agentList" @selection-change="handleSelectionChange">
         <el-table-column type="selection" width="55" align="center" />
         <el-table-column label="客服ID" align="center" prop="agentId" width="80" />
         <el-table-column label="登录账号" align="center" prop="agentNo" width="120" />
         <el-table-column label="客服昵称" align="center" prop="nickName" width="110" />
         <el-table-column label="手机号码" align="center" prop="phone" width="120" />
         <el-table-column label="在线状态" align="center" prop="onlineStatus" width="100">
            <template #default="scope">
               <dict-tag :options="kf_online_status" :value="scope.row.onlineStatus" />
            </template>
         </el-table-column>
         <el-table-column label="接待/上限" align="center" width="100">
            <template #default="scope">
               <span>{{ scope.row.currentLoad }} / {{ scope.row.acceptMax }}</span>
            </template>
         </el-table-column>
         <el-table-column label="账号状态" align="center" prop="status" width="100">
            <template #default="scope">
               <el-switch
                  v-model="scope.row.status"
                  active-value="0"
                  inactive-value="1"
                  @change="handleStatusChange(scope.row)"
                  :disabled="!checkPermi(['kf:agent:changeStatus'])"
               ></el-switch>
            </template>
         </el-table-column>
         <el-table-column label="最后登录" align="center" prop="loginDate" width="160">
            <template #default="scope">
               <span>{{ parseTime(scope.row.loginDate) }}</span>
            </template>
         </el-table-column>
         <el-table-column label="操作" align="center" width="260" class-name="small-padding fixed-width">
            <template #default="scope">
               <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['kf:agent:edit']">修改</el-button>
               <el-button link type="primary" icon="Key" @click="handleResetPwd(scope.row)" v-hasPermi="['kf:agent:resetPwd']">重置密码</el-button>
               <el-button
                  v-if="scope.row.onlineStatus !== '0'"
                  link
                  type="warning"
                  icon="SwitchButton"
                  @click="handleForceOffline(scope.row)"
                  v-hasPermi="['kf:agent:forceOffline']"
               >强制下线</el-button>
            </template>
         </el-table-column>

      <!-- 统一空状态（阶段8 功能点22） -->
      <template #empty>
        <empty-state description="暂无客服账号" hint="点击右上角“新增”创建第一个客服账号" />
      </template>
      </el-table>

      <pagination
         v-show="total > 0"
         :total="total"
         v-model:page="queryParams.pageNum"
         v-model:limit="queryParams.pageSize"
         @pagination="getList"
      />

      <!-- 添加或修改客服账号 -->
      <el-dialog :title="title" v-model="open" width="620px" append-to-body>
         <el-form ref="agentRef" :model="form" :rules="rules" label-width="100px">
            <el-row>
               <el-col :span="12">
                  <el-form-item label="登录账号" prop="agentNo">
                     <el-input v-model="form.agentNo" placeholder="请输入客服登录账号" :disabled="form.agentId != undefined" />
                  </el-form-item>
               </el-col>
               <el-col :span="12">
                  <el-form-item label="客服昵称" prop="nickName">
                     <el-input v-model="form.nickName" placeholder="访客可见的昵称" />
                  </el-form-item>
               </el-col>
            </el-row>
            <el-row v-if="form.agentId == undefined">
               <el-col :span="24">
                  <el-form-item label="初始密码" prop="password">
                     <el-input v-model="form.password" type="password" :placeholder="KF_PASSWORD_HINT" show-password />
                  </el-form-item>
               </el-col>
            </el-row>
            <el-row>
               <el-col :span="12">
                  <el-form-item label="手机号码" prop="phone">
                     <el-input v-model="form.phone" placeholder="请输入手机号码" maxlength="11" />
                  </el-form-item>
               </el-col>
               <el-col :span="12">
                  <el-form-item label="邮箱" prop="email">
                     <el-input v-model="form.email" placeholder="请输入邮箱" maxlength="50" />
                  </el-form-item>
               </el-col>
            </el-row>
            <el-row>
               <el-col :span="12">
                  <el-form-item label="最大接待数" prop="acceptMax">
                     <el-input-number v-model="form.acceptMax" :min="1" :max="50" controls-position="right" />
                  </el-form-item>
               </el-col>
               <el-col :span="12">
                  <el-form-item label="账号状态">
                     <el-radio-group v-model="form.status">
                        <el-radio
                           v-for="dict in sys_normal_disable"
                           :key="dict.value"
                           :value="dict.value"
                        >{{ dict.label }}</el-radio>
                     </el-radio-group>
                  </el-form-item>
               </el-col>
            </el-row>
            <el-form-item label="个人欢迎语" prop="welcomeText">
               <el-input v-model="form.welcomeText" type="textarea" :rows="2" placeholder="留空则使用全局欢迎语" maxlength="200" show-word-limit />
            </el-form-item>
            <el-form-item label="备注" prop="remark">
               <el-input v-model="form.remark" type="textarea" placeholder="请输入内容" />
            </el-form-item>
         </el-form>
         <template #footer>
            <div class="dialog-footer">
               <el-button type="primary" :loading="writeBusy" :disabled="writeBusy" @click="submitForm">确 定</el-button>
               <el-button @click="cancel">取 消</el-button>
            </div>
         </template>
      </el-dialog>

      <!-- 重置密码 -->
      <el-dialog title="重置客服密码" v-model="pwdOpen" width="420px" append-to-body>
         <el-form ref="pwdRef" :model="pwdForm" :rules="pwdRules" label-width="80px">
            <el-form-item label="客服账号">
               <span>{{ pwdForm.agentNo }}</span>
            </el-form-item>
            <el-form-item label="新密码" prop="password">
               <el-input v-model="pwdForm.password" type="password" :placeholder="KF_PASSWORD_HINT" show-password />
            </el-form-item>
         </el-form>
         <template #footer>
            <div class="dialog-footer">
               <el-button type="primary" :loading="writeBusy" :disabled="writeBusy" @click="submitPwdForm">确 定</el-button>
               <el-button @click="pwdOpen = false">取 消</el-button>
            </div>
         </template>
      </el-dialog>
   </div>
</template>

<script setup name="Agent">
import { listAgent, getAgent, delAgent, addAgent, updateAgent, changeAgentStatus, resetAgentPwd, forceOfflineAgent } from "@/api/kf/agent"
import { writeBusy } from '@/utils/writeBusy'
import { checkPermi } from "@/utils/permission"
import { applyQueryFilters, rememberFilters, restoreFilters, forgetFilters } from '@/utils/listState'
import { useKfPasswordRule, KF_PASSWORD_HINT } from '@/utils/kfPasswordRule'

const { proxy } = getCurrentInstance()
const route = useRoute()
const { kf_online_status, sys_normal_disable } = useDict('kf_online_status', 'sys_normal_disable')

const agentList = ref([])
const open = ref(false)
const pwdOpen = ref(false)
const loading = ref(true)
// 首次加载标记：骨架只在这一帧出现，之后保持 false
const firstLoad = ref(true)
const showSearch = ref(true)
const ids = ref([])
const multiple = ref(true)
const total = ref(0)
const title = ref("")

const data = reactive({
  form: {},
  pwdForm: {},
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    agentNo: undefined,
    nickName: undefined,
    phone: undefined,
    onlineStatus: undefined,
    status: undefined
  },
  rules: {
    agentNo: [
      { required: true, message: "客服登录账号不能为空", trigger: "blur" },
      { minlength: 2, maxlength: 30, message: "长度必须在2到30个字符之间", trigger: "blur" }
    ],
    nickName: [{ required: true, message: "客服昵称不能为空", trigger: "blur" }],
    password: useKfPasswordRule(),
    phone: [{ pattern: /^1[3-9]\d{9}$/, message: "请输入正确的手机号码", trigger: "blur" }],
    acceptMax: [{ required: true, message: "最大接待数不能为空", trigger: "blur" }]
  },
  pwdRules: {
    password: useKfPasswordRule()
  }
})

const { queryParams, form, rules, pwdForm, pwdRules } = toRefs(data)

/** 查询客服账号列表 */
function getList() {
  rememberFilters('kf.agent', queryParams.value, FILTER_FIELDS)
  loading.value = true
  listAgent(queryParams.value).then(response => {
    agentList.value = response.rows
    total.value = response.total
    loading.value = false
    firstLoad.value = false
  })
}

/** 取消按钮 */
function cancel() {
  open.value = false
  reset()
}

/** 表单重置 */
function reset() {
  form.value = {
    agentId: undefined,
    agentNo: undefined,
    password: undefined,
    nickName: undefined,
    phone: undefined,
    email: undefined,
    acceptMax: 5,
    welcomeText: undefined,
    status: "0",
    remark: undefined
  }
  proxy.resetForm("agentRef")
}

/** 搜索按钮操作 */
function handleQuery() {
  queryParams.value.pageNum = 1
  getList()
}

/** 重置按钮操作 */
function resetQuery() {
  forgetFilters('kf.agent')
  proxy.resetForm("queryRef")
  handleQuery()
}

/** 多选框选中数据 */
function handleSelectionChange(selection) {
  ids.value = selection.map(item => item.agentId)
  multiple.value = !selection.length
}

/** 新增按钮操作 */
function handleAdd() {
  reset()
  open.value = true
  title.value = "添加客服账号"
}

/** 修改按钮操作 */
function handleUpdate(row) {
  reset()
  getAgent(row.agentId).then(response => {
    form.value = response.data
    open.value = true
    title.value = "修改客服账号"
  })
}

/** 提交按钮 */
function submitForm() {
  proxy.$refs["agentRef"].validate(valid => {
    if (valid) {
      if (form.value.agentId != undefined) {
        updateAgent(form.value).then(() => {
          proxy.$modal.msgSuccess("修改成功")
          open.value = false
          getList()
        })
      } else {
        addAgent(form.value).then(() => {
          proxy.$modal.msgSuccess("新增成功")
          open.value = false
          getList()
        })
      }
    }
  })
}

/** 账号状态修改 */
function handleStatusChange(row) {
  let text = row.status === "0" ? "启用" : "停用"
  proxy.$modal.confirm('确认要"' + text + '"客服账号"' + row.agentNo + '"吗？停用会同时强制该客服下线。').then(function () {
    return changeAgentStatus({ agentId: row.agentId, status: row.status })
  }).then(() => {
    proxy.$modal.msgSuccess(text + "成功")
  }).catch(function () {
    row.status = row.status === "0" ? "1" : "0"
  })
}

/** 重置密码按钮操作 */
function handleResetPwd(row) {
  pwdForm.value = { agentId: row.agentId, agentNo: row.agentNo, password: undefined }
  pwdOpen.value = true
}

/** 提交新密码 */
function submitPwdForm() {
  proxy.$refs["pwdRef"].validate(valid => {
    if (valid) {
      resetAgentPwd({ agentId: pwdForm.value.agentId, password: pwdForm.value.password }).then(() => {
        proxy.$modal.msgSuccess("重置成功，该客服需重新登录")
        pwdOpen.value = false
        getList()
      })
    }
  })
}

/** 强制下线 */
function handleForceOffline(row) {
  proxy.$modal.confirm('确认强制客服"' + row.agentNo + '"下线吗？其接待中的会话归属保持不变。').then(function () {
    return forceOfflineAgent(row.agentId)
  }).then(() => {
    proxy.$modal.msgSuccess("已下线")
    getList()
  }).catch(() => {})
}

/** 删除按钮操作 */
function handleDelete(row) {
  const agentIds = row.agentId || ids.value
  proxy.$modal.confirmDanger('删除后该客服账号立即无法登录；历史会话与工单记录仍然保留。只是暂时不接线的，请改用「停用」。', { title: '删除客服', okText: '删除' }).then(function () {
    return delAgent(agentIds)
  }).then(() => {
    getList()
    proxy.$modal.msgSuccess("删除成功")
  }).catch(() => {})
}

// 带 onlineStatus / status 跳过来时（例如只看忙碌客服），落地就是筛好的那一组
const FILTER_FIELDS = ['agentNo', 'nickName', 'phone', 'onlineStatus', 'status']
restoreFilters('kf.agent', queryParams.value, FILTER_FIELDS)
applyQueryFilters(queryParams.value, route.query)

getList()
</script>
