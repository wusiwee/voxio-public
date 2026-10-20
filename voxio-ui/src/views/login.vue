<template>
  <div class="login">
    <!-- 左侧品牌区 -->
    <div class="login-brand">
      <div class="login-brand-inner">
        <img class="login-brand-logo" src="@/assets/logo/logo-horizontal-white.svg" alt="VOXIO" />
        <h1 class="login-brand-title">Voxio 在线客服系统</h1>
        <p class="login-brand-slogan">让每一次咨询都有回应</p>
        <ul class="login-brand-points">
          <li>访客咨询、客服接待、工单跟进一处完成</li>
          <li>实时消息与会话排队，接待状态一目了然</li>
          <li>离线留言兜底，非服务时间也不丢消息</li>
        </ul>
      </div>
      <span class="login-deco login-deco-1"></span>
      <span class="login-deco login-deco-2"></span>
      <span class="login-deco login-deco-3"></span>
    </div>

    <!-- 右侧表单卡片 -->
    <div class="login-panel">
      <el-form ref="loginRef" :model="loginForm" :rules="loginRules" class="login-form">
        <h3 class="title">{{ title }}</h3>
        <el-form-item prop="username">
          <el-input
            v-model="loginForm.username"
            type="text"
            size="large"
            auto-complete="off"
            placeholder="账号"
          >
            <template #prefix><svg-icon icon-class="user" class="el-input__icon input-icon" /></template>
          </el-input>
        </el-form-item>
        <el-form-item prop="password">
          <el-input
            v-model="loginForm.password"
            type="password"
            size="large"
            auto-complete="off"
            placeholder="密码"
            @keyup.enter="handleLogin"
          >
            <template #prefix><svg-icon icon-class="password" class="el-input__icon input-icon" /></template>
          </el-input>
        </el-form-item>
        <!-- 阶段10.4：登录不再要图形验证码，人机校验由「连续失败锁定 + 口令复杂度 + 登录日志 + 会话空闲超时」承担 -->
        <el-checkbox v-model="loginForm.rememberMe" style="margin:20px 0px 25px 0px;">记住密码</el-checkbox>
        <el-form-item style="width:100%;">
          <el-button
            :loading="loading"
            size="large"
            type="primary"
            style="width:100%;"
            @click.prevent="handleLogin"
          >
            <span v-if="!loading">登 录</span>
            <span v-else>登 录 中...</span>
          </el-button>
          <div style="float: right;" v-if="register">
            <router-link class="link-type" :to="'/register'">立即注册</router-link>
          </div>
        </el-form-item>
      </el-form>
      <div class="el-login-footer">
        <span>{{ footerContent }}</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import Cookies from "js-cookie"
import { encrypt, decrypt } from "@/utils/jsencrypt"
import useUserStore from '@/store/modules/user'
import defaultSettings from '@/settings'
import { HOME_PATH } from '@/router'

const title = import.meta.env.VITE_APP_TITLE
const footerContent = defaultSettings.footerContent
const userStore = useUserStore()
const route = useRoute()
const router = useRouter()
const { proxy } = getCurrentInstance()

const loginForm = ref({
  username: "",
  password: "",
  rememberMe: false
})

const loginRules = {
  username: [{ required: true, trigger: "blur", message: "请输入您的账号" }],
  password: [{ required: true, trigger: "blur", message: "请输入您的密码" }]
}

const loading = ref(false)
// 注册开关
const register = ref(false)
const redirect = ref(undefined)

watch(route, (newRoute) => {
    redirect.value = newRoute.query && newRoute.query.redirect
}, { immediate: true })

function handleLogin() {
  proxy.$refs.loginRef.validate(valid => {
    if (valid) {
      loading.value = true
      // 勾选了需要记住密码设置在 cookie 中设置记住用户名和密码
      if (loginForm.value.rememberMe) {
        Cookies.set("username", loginForm.value.username, { expires: 30 })
        Cookies.set("password", encrypt(loginForm.value.password), { expires: 30 })
        Cookies.set("rememberMe", loginForm.value.rememberMe, { expires: 30 })
      } else {
        // 否则移除
        Cookies.remove("username")
        Cookies.remove("password")
        Cookies.remove("rememberMe")
      }
      // 调用action的登录方法
      userStore.login(loginForm.value).then(() => {
        const query = route.query
        const otherQueryParams = Object.keys(query).reduce((acc, cur) => {
          if (cur !== "redirect") {
            acc[cur] = query[cur]
          }
          return acc
        }, {})
        // 登录后落在会话监控；从别处被踢回登录页时仍回原来那一页
        router.push({ path: redirect.value || HOME_PATH, query: otherQueryParams })
      }).catch(() => {
        loading.value = false
      })
    }
  })
}

function getCookie() {
  const username = Cookies.get("username")
  const password = Cookies.get("password")
  const rememberMe = Cookies.get("rememberMe")
  loginForm.value = {
    username: username === undefined ? loginForm.value.username : username,
    password: password === undefined ? loginForm.value.password : decrypt(password),
    rememberMe: rememberMe === undefined ? false : Boolean(rememberMe)
  }
}

getCookie()
</script>

<style lang='scss' scoped>
.login {
  display: flex;
  height: 100%;
  background: var(--sb-color-bg-page);
}

/* ---------------- 左侧品牌区 ---------------- */
.login-brand {
  position: relative;
  flex: 1 1 52%;
  min-width: 0;
  overflow: hidden;
  background: var(--sb-color-primary);
  color: var(--sb-color-primary-on-contrast);
  display: flex;
  align-items: center;
}

.login-brand-inner {
  position: relative;
  z-index: 1;
  padding: 0 8%;
  max-width: 560px;
}

.login-brand-logo {
  /* 品牌区底色就是主色，所以这里必须用反白横版；160px 落在规范给的登录页 120-160 区间 */
  width: 160px;
  height: auto;
}

.login-brand-title {
  margin: 20px 0 8px;
  font-size: var(--sb-font-size-2xl);
  font-weight: 600;
  letter-spacing: 0.5px;
}

.login-brand-slogan {
  margin: 0;
  font-size: var(--sb-font-size-lg);
  opacity: 0.9;
}

.login-brand-points {
  margin: 28px 0 0;
  padding: 0;
  list-style: none;
  font-size: var(--sb-font-size-sm);
  line-height: 2.1;
  opacity: 0.82;

  li::before {
    content: "";
    display: inline-block;
    width: 6px;
    height: 6px;
    margin-right: 10px;
    border-radius: var(--sb-radius-pill);
    background: currentColor;
    opacity: 0.7;
    vertical-align: middle;
  }
}

/* 简洁几何装饰：纯色圆形/圆角方块，无渐变堆叠 */
.login-deco {
  position: absolute;
  display: block;
  border: 2px solid rgba(255, 255, 255, 0.22);
  border-radius: var(--sb-radius-pill);
}

.login-deco-1 {
  width: 260px;
  height: 260px;
  right: -70px;
  top: -70px;
}

.login-deco-2 {
  width: 160px;
  height: 160px;
  right: 12%;
  bottom: -56px;
}

.login-deco-3 {
  width: 72px;
  height: 72px;
  right: 22%;
  top: 24%;
  border-radius: var(--sb-radius-lg);
  background: rgba(255, 255, 255, 0.12);
  border: none;
}

/* ---------------- 右侧表单卡片 ---------------- */
.login-panel {
  flex: 0 0 46%;
  min-width: 480px;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
}

.title {
  margin: 0 auto 28px;
  text-align: center;
  color: var(--sb-color-text);
  font-size: var(--sb-font-size-xl);
  font-weight: 600;
}

.login-form {
  width: 400px;
  padding: 36px 32px 18px;
  background: var(--sb-color-bg-card);
  border: 1px solid var(--sb-color-border);
  border-radius: var(--sb-radius-lg);
  box-shadow: var(--sb-shadow-md);

  .el-input {
    height: var(--sb-control-height);

    input {
      height: var(--sb-control-height);
    }
  }

  .input-icon {
    height: 39px;
    width: 14px;
    margin-left: 0px;
  }
}

.el-login-footer {
  position: absolute;
  bottom: 16px;
  left: 0;
  right: 0;
  text-align: center;
  color: var(--sb-color-text-muted);
  font-size: var(--sb-font-size-xs);
  letter-spacing: 1px;
}

/* 窄屏（含移动端）：只保留表单卡片 */
@media (max-width: 992px) {
  .login {
    background: var(--sb-color-bg-page);
  }

  .login-brand {
    display: none;
  }

  .login-panel {
    flex: 1 1 auto;
    min-width: 0;
    width: 100%;
    padding: 24px;
  }

  .login-form {
    width: 100%;
    max-width: 400px;
    border: none;
    box-shadow: var(--sb-shadow-sm);
  }

  .el-login-footer {
    position: static;
    padding: 12px 0 0;
  }
}

html.dark {
  .login {
    background: var(--el-bg-color);
  }

  .login-form {
    background: var(--el-bg-color-overlay);
    border-color: var(--el-border-color);
  }
}
</style>
