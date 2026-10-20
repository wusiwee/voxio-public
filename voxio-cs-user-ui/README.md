# voxio-cs-user-ui · 访客网页端

Voxio 在线客服的访客端：Vue 3 + Vite，无后台登录，打开即是对话框。
消息只支持纯文本与表情。

## 命令

```bash
npm install
npm run dev      # http://localhost:5173
npm run build    # 产物 dist/，默认挂站点根路径
```

`vite.config.js` 顶部的 `APP_CONFIG` 是唯一配置点：

| 键 | 默认 | 说明 |
| --- | --- | --- |
| `apiPrefix` | `/customer` | 后端接口前缀，与 nginx 反代和后端 context-path 一致 |
| `wsPrefix` | `/customer-ws` | 聊天网关前缀（经 nginx），本机直连由 `devWsTarget` 兜底 |
| `devWsTarget` | `ws://localhost:10097/ws` | dev server 把 `/customer-ws` 代理到这里 |
| `basePath` | `/` | 要嵌进第三方站点的子路径时改这里，或 `vite build --base=/support/` |
| `openCacheTtl` | — | 开放接口读取的 sessionStorage 缓存时长 |

静态资源前缀与路由 base 必须同源，所以命令行 `--base=` 会同时改两处。

## 页面与模块

```
src/
├── views/Chat.vue              唯一页面：会话首屏 + 消息流 + 输入区
├── components/
│   ├── ChatInput.vue           文本输入、表情面板、发送按钮
│   ├── MessageItem.vue         消息气泡（访客主色 / 客服白底描边 / 系统居中灰）
│   └── OfflineDialog.vue       离线留言对话框（异步组件，用到才拉）
├── composables/useChatSession.js  网关连接、鉴权、收发、重连补发、排队与已读
├── api/                        open.js（开放接口）+ request.js（令牌与身份补签）
└── utils/                      visitor.js 访客号、localCache.js、format.js、
                                chatCrypto.js + sm.js（国密，默认关闭）
```

## 聊天链路

```
POST /customer/kf/open/session            建会话（同 visitorNo 命中既有活动会话，幂等）
GET  /customer/kf/open/config             欢迎语、排队提示、服务时间等可见参数
GET  /customer/kf/open/status             在线人数与排队数（匿名，不建会话）
POST /customer/kf/open/offline            离线留言（无身份时自动补签一次）
WS   /customer-ws  →  {cmd:"AUTH"}  →  {cmd:"CHAT", type:"text", body:{content, clientMsgId}}
```

- 访客身份是本地生成的 `visitorNo` + 服务端签发的访客令牌，存 sessionStorage；
  公开写接口在没有令牌时会自己补签一次，同一帧内只补一次。
- `clientMsgId` 是消息幂等键，重连与重发都靠它去重；未拿到 `ACK` 的消息在气泡上标失败并可重发。
- 断线自动重连，鉴权成功后用已知最大 `seq` 增量补发，离线期间的消息不丢。
- 结束会话后提示「会话已结束，欢迎再次咨询」，可重新联系人工客服开一条新会话。

## 输入区

表情面板 + 多行文本框 + 发送按钮。表情只用 Unicode 字形，不引图片资源；
输入框在有消息未拿到 `ACK` 时禁用发送并显示 loading，避免重复提交。

## 嵌入自己的站点

访客端是独立站点，两种接法：

1. 独立域名/子域：直接把 `dist/` 挂到根路径，nginx 配好 `/customer` 与 `/customer-ws` 两条反代
   （完整站点配置见 `doc/部署文档.docx`）。
2. iframe 嵌进现有站点：`vite build --base=/support/` 后挂到子路径，
   父页面用 `<iframe src="https://host/support/">` 引入。
   被嵌页面要能正常拿到 sessionStorage 与 WebSocket，第三方 Cookie 限制下建议用第一种。

## 样式 token

`src/styles/tokens.css` 由 `../voxio-ui/brand/tokens.json` 经
`node ../voxio-ui/brand/sync-tokens.mjs` 生成，不要手工改。
