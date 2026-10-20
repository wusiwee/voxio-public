# voxio-ui · 管理后台

Voxio 在线客服的管理端：Vue 3 + Vite + Element Plus + Pinia，构建产物挂在站点 `/admin/` 子路径。

## 命令

```bash
npm install
npm run dev          # http://localhost/admin/  （dev server 与生产同 base，写错本地就暴露）
npm run build:prod   # 产物 dist/
```

接口前缀固定 `/customer`，写在 `vite.config.js` 的 `APP_CONFIG.apiPrefix`，
代码里不出现主机名与端口；换环境只改 nginx 反代。

## 页面

| 一级 | 二级 | 路由 |
| --- | --- | --- |
| 客服管理 | 客服账号 | `/kf/agent` |
| 会话管理 | 会话监控、离线留言 | `/conversation/monitor`、`/conversation/offline` |
| 工单管理 | 工单处理 | `/ticket/work` |
| 系统设置 | 客服参数、业务设置、日志留痕（客服登录日志 + 业务审计日志） | `/system/kfConfig`、`/system/kfSettings`、`/system/log/*` |

侧栏由后端 `getRouters` 按 `sys_menu` 动态下发，**菜单的增删改在 SQL 里**
（`voxio-server/sql/voxio_chat.sql` 的 `kf_03_menu` / `kf_05_menu_config` / `kf_10_admin` /
`kf_11_menu_ia` 段），不在前端路由表里。

登录成功后的落地页是「会话监控」，不是总览。

## 目录

`src/views/kf/` 与 `src/api/kf/` 一一对应，各 8 个模块：
`agent`（客服账号）、`monitor`（会话监控）、`offline`（离线留言）、`ticket`（工单处理）、
`config`（客服参数）、`settings`（业务设置）、`loginlog`（客服登录日志）、`auditlog`（业务审计日志）。

## 设计 token

颜色、圆角、字体、动效的唯一来源是 `brand/tokens.json`，经
`node brand/sync-tokens.mjs` 分发成 `tokens.css` / `tokens.scss` 与两端产物。
手工改生成文件会在 `node brand/sync-tokens.mjs --check` 时报漂移。
品牌规范与标记用法见 `brand/brand-guide.md`。

## 目录

```
src/
├── api/          请求收口（kf/ 下 8 个模块 + system/ + monitor/ + tool/）
├── views/        页面（kf/ 客服业务、system/ 若依系统、monitor/ 缓存与定时任务）
├── layout/       框架布局与侧栏
├── components/   通用组件（表格骨架、空态、字典标签、RightToolbar 等）
├── store/        Pinia
├── router/       常量路由（动态路由来自后端）
└── assets/styles 生成的 token 与全局样式
```
