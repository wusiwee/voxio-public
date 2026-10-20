import { defineConfig, loadEnv } from 'vite'
import path from 'path'
import createVitePlugins from './vite/plugins'

// 前端构建明文配置（项目规范：配置文件明文，不用 .env 承载核心配置）
const APP_CONFIG = {
  // 后端接口统一前缀（开发走 vite 代理，生产由 nginx 反代到后端）
  apiPrefix: '/customer',
  // Java 后端开发地址（后端已带 servlet context path /customer，代理不改写路径）
  devApiTarget: 'http://localhost:10096',
  // Netty 聊天网关同源 WS 前缀（开发走 vite 代理，生产由 nginx 转发）
  wsPrefix: '/customer-ws',
  // Netty 聊天网关开发地址（仅开发代理用，浏览器不直连）
  devWsTarget: 'ws://localhost:10097/ws',
  // 页面标题
  title: 'Voxio 管理后台',
  // 应用部署 base 路径：管理后台挂在站点 /admin/ 子路径（访客端占根路径）
  // dev server 也用同一个 base，开发与生产的 URL 一致，base 写错在本地就能暴露
  basePath: '/admin/',
  // dev server 端口
  port: 80,
  // 打包压缩：gzip / brotli / gzip,brotli，留空不压缩
  compress: 'gzip'
}

// 以 define 注入 import.meta.env.VITE_APP_*，保持既有调用点不变
const appDefine = {
  'import.meta.env.VITE_APP_BASE_API': JSON.stringify(APP_CONFIG.apiPrefix),
  'import.meta.env.VITE_APP_TITLE': JSON.stringify(APP_CONFIG.title)
}

// https://vitejs.dev/config/
export default defineConfig(({ mode, command }) => {
  // .env 已删除：loadEnv 仅兜底收集环境变量，压缩开关由上方明文配置透传给插件
  const env = { ...loadEnv(mode, process.cwd()), VITE_BUILD_COMPRESS: APP_CONFIG.compress }
  // 从 ws://localhost:10097/ws 解析出代理目标与网关路径
  const wsTarget = new URL(APP_CONFIG.devWsTarget)
  const isBuild = command === 'build'
  return {
    base: APP_CONFIG.basePath,
    plugins: createVitePlugins(env, isBuild),
    define: appDefine,
    resolve: {
      // https://cn.vitejs.dev/config/#resolve-alias
      alias: {
        // 设置路径
        '~': path.resolve(__dirname, './'),
        // 设置别名
        '@': path.resolve(__dirname, './src')
      },
      // https://cn.vitejs.dev/config/#resolve-extensions
      extensions: ['.mjs', '.js', '.ts', '.jsx', '.tsx', '.json', '.vue']
    },
    // 打包配置
    build: {
      // https://vite.dev/config/build-options.html
      sourcemap: isBuild ? false : 'inline',
      outDir: 'dist',
      assetsDir: 'assets',
      chunkSizeWarningLimit: 2000,
      rollupOptions: {
        output: {
          chunkFileNames: 'static/js/[name]-[hash].js',
          entryFileNames: 'static/js/[name]-[hash].js',
          assetFileNames: 'static/[ext]/[name]-[hash].[ext]',
          // 阶段11 功能点15/19：第三方库单独成块。
          // 入口 chunk 原先把 element-plus 整个塞进去（1.7MB），改一行代码就要重新下载；
          // 拆出来之后业务 chunk 变小，库 chunk 名字带内容哈希，发版后浏览器仍能命中缓存（配合 nginx 的 immutable）。
          // 只做「按库分块」，不做 element-plus 按需引入：全站几十处模板直接依赖全局注册的组件与
          // ElMessage/ElLoading 的样式副作用，改成 unplugin 自动引入的回归面太大，收益不如缓存来得稳。
          //
          // 阶段16 上线后只保留「echarts / 其余」两块，不再按 vue / element-plus 细分：
          // 三个平级 vendor 块之间必然互指 —— element-plus 要 vue 与 @vueuse/shared，@vueuse/core 要
          // @vueuse/shared（Rollup 把它归进了 element-plus 块），vue-cropper / vue-quill 又要 vue。
          // ES 模块图有环时「哪个 chunk 先求值」取决于浏览器拿模块的顺序，同一份产物可能本机正常、
          // 线上一进来就 Cannot access 'xx' before initialization，页面永远停在加载动画。
          // 缓存目标不受影响：业务代码仍然全在 index chunk，改业务只重下 index。
          manualChunks(id) {
            if (!id.includes('node_modules')) {
              return
            }
            // echarts/zrender 只被业务懒加载引用，且只单向依赖其它库，单独成块不会成环
            if (/[\\/]node_modules[\\/](echarts|zrender)[\\/]/.test(id)) {
              return 'vendor-echarts'
            }
            return 'vendor'
          }
        }
      }
    },
    // vite 相关配置
    server: {
      port: APP_CONFIG.port,
      host: true,
      open: true,
      proxy: {
        // https://cn.vitejs.dev/config/#server-proxy
        // 注意：/customer-ws 必须排在 /customer 之前（vite 代理按声明顺序前缀匹配）
        [APP_CONFIG.wsPrefix]: {
          target: wsTarget.origin,
          changeOrigin: true,
          ws: true,
          rewrite: (p) => p.replace(new RegExp('^' + APP_CONFIG.wsPrefix), wsTarget.pathname)
        },
        // 后端自带 /customer context path，无需路径改写
        [APP_CONFIG.apiPrefix]: {
          target: APP_CONFIG.devApiTarget,
          changeOrigin: true
        }
      }
    },
    css: {
      postcss: {
        plugins: [
          {
            postcssPlugin: 'internal:charset-removal',
            AtRule: {
              charset: (atRule) => {
                if (atRule.name === 'charset') {
                  atRule.remove()
                }
              }
            }
          }
        ]
      }
    }
  }
})
