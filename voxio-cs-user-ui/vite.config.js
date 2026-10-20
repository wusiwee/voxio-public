import { defineConfig } from 'vite'
import path from 'path'
import vue from '@vitejs/plugin-vue'

// 访客端构建明文配置（项目规范：配置文件明文，不用 .env 承载核心配置）
const APP_CONFIG = {
  // 后端接口统一前缀（开发走 vite 代理，生产由 nginx 反代到后端）
  apiPrefix: '/customer',
  // Java 后端开发地址（后端已带 servlet context path /customer，代理不改写路径）
  devApiTarget: 'http://localhost:10096',
  // Netty 聊天网关同源 WS 前缀（浏览器只连同源前缀，开发走 vite 代理，生产走 nginx）
  wsPrefix: '/customer-ws',
  // Netty 聊天网关开发地址（仅开发代理用，浏览器不直连）
  devWsTarget: 'ws://localhost:10097/ws',
  // 页面标题
  title: 'Voxio 在线客服',
  // 应用部署 base 路径：独立站点用根路径；
  // 要嵌到第三方站点的子路径下时改这里，或用命令行 --base= 覆盖
  basePath: '/',
  // dev server 端口
  port: 5173,
  // 打包压缩：gzip / brotli / gzip,brotli，留空不压缩
  compress: 'gzip',
  // 访客端开放接口的浏览器侧缓存时长（毫秒），0 表示该档不缓存（阶段11 功能点21）
  // 访客标签页一关就作废（sessionStorage），所以这里的时长只影响「同一次停留内的重复请求」
  openCacheTtl: {
    status: 30000
  }
}

// `vite build --base=/support/` 可临时换前缀：静态资源前缀与路由 base 必须同源，
// 只改一处会让 index.html 取不到资源，或路由跳出前缀后刷新 404
const cliBase = (process.argv.find(a => a.startsWith('--base=')) || '').slice(7)
const basePath = cliBase || APP_CONFIG.basePath

// 以 define 注入 import.meta.env.VITE_APP_*，保持既有调用点不变
const appDefine = {
  'import.meta.env.VITE_APP_BASE_API': JSON.stringify(APP_CONFIG.apiPrefix),
  'import.meta.env.VITE_APP_WS_PREFIX': JSON.stringify(APP_CONFIG.wsPrefix),
  'import.meta.env.VITE_APP_BASE_PATH': JSON.stringify(basePath),
  'import.meta.env.VITE_APP_TITLE': JSON.stringify(APP_CONFIG.title),
  'import.meta.env.VITE_APP_OPEN_CACHE_TTL': JSON.stringify(APP_CONFIG.openCacheTtl)
}

// 访客在线网页端构建配置：核心配置见上方明文 APP_CONFIG
export default defineConfig(() => {
  // 从 ws://localhost:10097/ws 解析出代理目标与网关路径
  const wsTarget = new URL(APP_CONFIG.devWsTarget)
  return {
    base: basePath,
    plugins: [vue()],
    define: appDefine,
    resolve: {
      alias: {
        '@': path.resolve(__dirname, './src')
      }
    },
    server: {
      port: APP_CONFIG.port,
      host: true,
      open: true,
      proxy: {
        // 注意：/customer-ws 必须排在 /customer 之前（vite 代理按声明顺序前缀匹配）
        // Netty 聊天网关 WS：同源前缀改写为网关 /ws 路径
        [APP_CONFIG.wsPrefix]: {
          target: wsTarget.origin,
          changeOrigin: true,
          ws: true,
          rewrite: (p) => p.replace(new RegExp('^' + APP_CONFIG.wsPrefix), wsTarget.pathname)
        },
        // 业务接口：后端自带 /customer context path，无需路径改写
        [APP_CONFIG.apiPrefix]: {
          target: APP_CONFIG.devApiTarget,
          changeOrigin: true
        }
      }
    },
    build: {
      outDir: 'dist',
      assetsDir: 'assets',
      chunkSizeWarningLimit: 1500,
      rollupOptions: {
        output: {
          // 阶段11 功能点25：库与业务分块。访客端可能嵌进第三方站点，业务代码改动频繁，
          // 而 vue / element-plus / sm-crypto 基本不动；分块后发版只需重下业务那一小块。
          manualChunks(id) {
            if (!id.includes('node_modules')) {
              return
            }
            if (/[\\/]node_modules[\\/](element-plus|@element-plus)[\\/]/.test(id)) {
              return 'vendor-element-plus'
            }
            if (/[\\/]node_modules[\\/]sm-crypto[\\/]/.test(id)) {
              return 'vendor-sm'
            }
            if (/[\\/]node_modules[\\/](vue|@vue|vue-router)[\\/]/.test(id)) {
              return 'vendor-vue'
            }
            return 'vendor'
          }
        }
      }
    }
  }
})
