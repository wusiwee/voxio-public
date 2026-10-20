import { createApp } from 'vue'
import { ElButton, ElDialog, ElForm, ElFormItem, ElIcon, ElInput } from 'element-plus'
// 阶段11 功能点15/25：访客端只用到这 6 个组件，改成按需注册而不是 app.use(ElementPlus)。
// 整包注册会把 element-plus 全部组件都拉进入口 chunk（访客端入口原本 1.1MB），
// element-plus 的 JS 侧没有全量注册之外的副作用，所以只要不再 app.use 整包，未引用的组件就能被 Tree-shaking 掉；
// CSS 必须逐个引 style/css 入口：它会带上 base 变量与该组件的依赖（overlay、badge 等），
// 漏了就是「功能正常但没样式」。
import 'element-plus/es/components/button/style/css'
import 'element-plus/es/components/dialog/style/css'
import 'element-plus/es/components/form/style/css'
import 'element-plus/es/components/form-item/style/css'
import 'element-plus/es/components/icon/style/css'
import 'element-plus/es/components/input/style/css'
// 这两个是函数式调用（ElMessage / ElMessageBox），模板里搜不到，得单独带样式
import 'element-plus/es/components/message/style/css'
import 'element-plus/es/components/message-box/style/css'

import App from './App.vue'
import router from './router'
import './styles/tokens.css' // 设计 token（voxio-ui/brand/tokens.json 生成，与管理后台同源）
import './styles/base.css'
// 阶段12 交互层：动效/骨架/断网提示条。刻意用纯 CSS 而不是 el-skeleton/el-empty，
// 那两个组件会打破上面刚收窄的按需注册清单，把访客端入口 JS 拽回阶段11 压下去的体积。
import './styles/motion.css'

const app = createApp(App)

// 图标全部是各组件自己 import 的，这里不再 app.component 全局注册，也不再 import *
for (const component of [ElButton, ElDialog, ElForm, ElFormItem, ElIcon, ElInput]) {
  app.component(component.name, component)
}

app.use(router)
app.mount('#app')
