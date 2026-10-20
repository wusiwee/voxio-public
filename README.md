<p align="center">
  <img src="voxio-ui/brand/logo-horizontal.svg" alt="Voxio" width="240"/>
</p>

# Voxio 在线客服系统

单商户自部署的在线客服系统：访客在自己的网站里发起咨询，客服在工作台接待，管理员在后台处理工单与留言。
一个 Spring Boot 后端 + 一个 Netty 聊天网关 + 两个 Web 前端，全部组件可以跑在客户自己的一台服务器上，数据不出内网。

明确不做的事情：多租户 SaaS、商户入驻与套餐计费、平台总控、复杂权限引擎、微服务与容器编排、分库分表、
全渠道接入、AI 机器人。所有设计与实现都围绕「一家企业自己用起来」这条主线，刻意保持可读懂、可二开。

## 目录

- [特性一览](#特性一览)
- [架构](#架构)
- [功能模块](#功能模块)
- [数据模型](#数据模型)
- [实时消息协议](#实时消息协议)
- [快速开始](#快速开始)
- [配置说明](#配置说明)
- [界面预览](#界面预览)
- [客服端接入](#客服端接入)
- [可选的国密加密](#可选的国密加密)
- [目录结构](#目录结构)
- [部署](#部署)
- [二次开发指引](#二次开发指引)

---

## 特性一览

| 能力 | 说明 |
| --- | --- |
| 实时文本聊天 | Netty 4 独立网关，WebSocket（浏览器）与 TCP（原生客户端）双通道共用一套 JSON 信封；已读回执、typing 提示、断线重连与增量补发 |
| 排队与分配 | 无空闲客服时进队列并实时播报排位；`kf.assign.enabled` 控制自动分配，策略为「空闲优先」，主管可在后台人工指派 |
| 会话超时 | Quartz 定时任务按 `kf.session.timeout.minutes` 自动结束无活动会话 |
| 离线留言 | 无客服在线时访客留下问题与联系方式，后台处理并可一键转为工单 |
| 工单 | 建单、状态流转（待处理 → 处理中 → 已解决 / 已关闭）、回复与流转留痕、Excel 导出、可选的第三方手动推送 |
| 会话监控 | 实时查看进行中的会话、消息与客服负载，支持人工分配 |
| 账号与权限 | 若依 RBAC（用户/角色/菜单）+ 独立的客服账号体系（`kf_agent`），两套令牌互不通用 |
| 安全留痕 | 客服登录日志、业务审计日志（只读、防删、可配留存期）、登录失败锁定、口令复杂度策略 |
| 可选国密 | SM2 / SM3 / SM4-GCM 全套实现内置，默认关闭，一行配置开启 |
| 移动端适配 | 访客端与管理后台均适配窄屏，访客端可嵌入第三方站点子路径 |

## 架构

```
                 访客（浏览器 / 移动端页面）
                          │  https://your-domain.com/
                          ▼
┌───────────────────────────────────────────────────────────────────────┐
│                              nginx                                    │
│   /            访客端静态站点          /admin/      管理后台静态站点     │
│   /customer/   反向代理 → 10096       /customer-ws  升级转发 → 10097/ws  │
└───────────────┬───────────────────────────────────────┬───────────────┘
                │ HTTP（统一 /customer 前缀）            │ WebSocket
                ▼                                       ▼
┌───────────────────────────────┐    ┌───────────────────────────────┐
│  voxio-admin（Spring Boot）   │◄───│  voxio-chat-netty（Netty 4.x）│
│  会话 / 消息 / 工单 / 留言    │ 服务│  鉴权·心跳·限流·收发·补发     │
│  监控 / 配置 / 审计 / 导出    │ 调用│  同 JVM 启动，不写业务 SQL    │
│  HTTP 10096  上下文 /customer │    │  WS 10097 · TCP 10098         │
└───────────┬───────────────────┘    ───────────────────────────────
            │
     ┌──────┴──────┐
     ▼             ▼
┌─────────┐  ┌─────────┐
│ MySQL 8 │  │ Redis   │        客服端：HTTP 接口 + WS 长连接（可自建工作台）
│ 单业务库 │  │ 令牌/缓存│
└─────────┘  └─────────┘
```

**关键设计取舍**

- **聊天链路独立于 HTTP**：消息收发走 Netty 网关的长连接，不占用 Tomcat 线程；网关只做鉴权、限流、路由与投递，
  业务落库通过服务调用回到 `voxio-desk`，不直接写 SQL。
- **`/customer` 前缀贯穿全链路**：它既是 nginx 的代理前缀，也是后端的 `server.servlet.context-path`，
  nginx 不剥前缀、不 rewrite，前后端路径一一对应，换端口只改一处。
- **消息幂等**：`(session_id, seq)` 唯一键 + 客户端 `clientMsgId` 去重，重连补发与重复提交都不会产生脏消息。
- **落库异步但按会话保序**：先回 ACK 再写库，写队列满了退回同步写（宁可慢，不丢消息），失败按 200/400/800ms 退避重试。

**端口**

| 端口 | 用途 |
| --- | --- |
| `10096` | 后端 HTTP，上下文 `/customer` |
| `10097` | 聊天网关 WebSocket，握手路径 `/ws`（nginx 侧映射为 `/customer-ws`） |
| `10098` | 聊天网关 TCP（原生客户端长连接，建议只放行内网） |
| `80` / `443` | nginx 统一入口 |

## 功能模块

### 服务端（Maven 多模块）

| 模块 | 职责 |
| --- | --- |
| `voxio-admin` | Web 入口与打包模块，配置文件与 logback 在此；产出的 jar 是唯一投递物 |
| `voxio-chat-netty` | Netty 聊天网关：编解码、鉴权、限流、连接注册、消息分发、投递与补发、指标上报 |
| `voxio-desk` | 客服业务域：会话、消息、工单、离线留言、会话监控、业务参数、审计与开放接口 |
| `voxio-framework` | 框架基建：安全过滤链、数据源、缓存、切面、拦截器 |
| `voxio-system` | 系统域（若依）：用户、角色、菜单、部门、岗位、字典、参数、公告、登录与操作日志 |
| `voxio-quartz` | 定时任务基座；客服域的三个 Job 在 `voxio-desk` 里注册 |
| `voxio-common` | 通用工具、域模型基类、国密加解密层 |

### 客服业务域接口面

| 前缀 | 面向 | 鉴权 |
| --- | --- | --- |
| `POST /kf/open/session` · `GET /kf/open/session/messages` · `POST /kf/open/session/end` | 访客会话 | 访客令牌 |
| `GET /kf/open/config` · `GET /kf/open/status` · `GET /kf/open/security` | 访客入口探测 | 匿名（配置走白名单，密钥类参数不下发） |
| `POST /kf/open/offline` | 离线留言 | 访客令牌（无身份时自动补签一次） |
| `POST /kf/agent/login` · `/kf/agent-app/**` | 客服端 | 客服令牌 |
| `/kf/monitor/**` | 会话监控与人工分配 | 后台 RBAC |
| `/kf/ticket/**` | 工单查询、建单、流转、回复、导出、推送 | 后台 RBAC |
| `/kf/offline/**` | 离线留言处理 | 后台 RBAC |
| `/kf/settings/business` | 业务设置（分配策略、服务时间、工单推送） | 后台 RBAC |
| `/kf/auditlog/**` · `/kf/loginlog/**` | 审计与登录日志（只读） | 后台 RBAC |

### 管理后台（`voxio-ui`）

Vue 3 + Vite + Element Plus，构建产物挂 `/admin/`。侧栏由后端 `getRouters` 按 `sys_menu` 动态下发。

```
客服管理 ── 客服账号
会话管理 ── 会话监控 · 离线留言
工单管理 ── 工单处理
系统设置 ── 客服参数 · 业务设置 · 日志留痕（客服登录日志 / 业务审计日志）
```

登录后的落地页是「会话监控」。

### 访客网页端（`voxio-cs-user-ui`）

Vue 3 + Vite，无登录，打开即对话框。核心是一个 `useChatSession` 组合式函数：负责建会话、
令牌续用、WS 连接与重连、消息按 `seq` 升序合并、离线补发、排队位次、已读上报与发送失败重发。
表情只用 Unicode 字形，不引图片资源。

## 数据模型

单库 `voxio-public`，40 张表：若依系统表 18 张（`sys_*`）、Quartz 11 张（`QRTZ_*`）、客服业务表 11 张（`kf_*`）。

| 表 | 职责 | 关键点 |
| --- | --- | --- |
| `kf_agent` | 客服账号 | 独立于 `sys_user`；在线态、并发上限、负载计数、口令哈希与算法标记 |
| `kf_visitor` | 访客 | 访客号、昵称、联系方式、来源页 |
| `kf_session` | 会话 | 状态机（排队 / 接待 / 结束）、队列与分配时间、首响时间、未读计数、`last_seq` 序号水位 |
| `kf_message` | 消息 | `(session_id, seq)` 与 `(session_id, client_msg_id)` 双唯一键；投递状态；加密标记与算法/密钥版本随行 |
| `kf_ticket` | 工单 | 分类、优先级、状态、来源、处理客服、第三方推送留痕 |
| `kf_ticket_reply` | 工单回复与流转 | 记录 `from_status → to_status`，流转即留痕 |
| `kf_offline_message` | 离线留言 | 处理状态，可关联转出的工单 |
| `kf_config` | 业务参数 | `uk_config_key` 幂等 upsert；访客可见项走白名单 |
| `kf_security_config` | 安全策略参数 | 锁定阈值、口令策略、留存期、加密算法口径；不下发给客户端 |
| `kf_agent_login_log` | 客服登录日志 | 成功与失败都落，按留存期自动清理 |
| `kf_audit_log` | 业务审计留痕 | 只读；表结构上不提供删除通道 |

初始化数据含：管理员 `admin`、三个示例客服 `kefu01~03`、17 项业务参数、14 项安全策略、11 个业务字典、
以及完整菜单与按钮权限。

## 实时消息协议

WS 与 TCP 共用同一 JSON 信封（`ChatEnvelope`）：`v / cmd / seq / mid / sid / from / to / type / body / ts`，
开启加密时另有 `encrypt / algo / hmac / keyVersion`。

| 命令字 | 方向 | 含义 |
| --- | --- | --- |
| `AUTH` / `AUTH_OK` | 客户端 → 网关 → 客户端 | 令牌鉴权；`AUTH_OK` 下发加密参数与超时 |
| `KEY_EXCHANGE` | 客户端 → 网关 | 上送 SM2 加密的会话密钥（开启加密时） |
| `CHAT` / `ACK` | 双向 | 消息投递与落库回执（回执带 `seq` 与 `msgId`） |
| `READ` / `READ_NOTIFY` | 双向 | 已读上报与对端已读通知 |
| `SYNC` | 客户端 → 网关 | 断线重连后按已知最大 `seq` 增量补发 |
| `SESSION_NEW` / `SESSION_QUEUED` / `SESSION_ASSIGNED` / `SESSION_END` | 网关 → 客户端 | 会话生命周期事件（进线、排位、接入、结束） |
| `AGENT_STATUS` | 客服端 → 网关 | 上线 / 忙碌 / 离线 |
| `TYPING` | 双向 | 正在输入 |
| `PING` / `PONG` | 双向 | 心跳，读空闲 90s 断开 |
| `KICKED` / `ERROR` | 网关 → 客户端 | 同身份重复连接被踢、错误码 |

限流在网关侧按连接生效：访客 20 条/分钟、客服 60 条/分钟；单帧上限 64KB，单条文本上限 2000 字。

## 快速开始

### 环境

JDK 17+ · Maven 3.6+ · Node 18+ · MySQL 8+ · Redis 5+ · nginx 1.20+（生产入口）

### 1. 建库

```bash
mysql -u root -p --default-character-set=utf8mb4 \
      -e "create database if not exists \`voxio-public\` default charset utf8mb4 collate utf8mb4_general_ci"

mysql -u root -p --default-character-set=utf8mb4 voxio-public < voxio-server/sql/voxio_chat.sql
```

脚本可重复执行（表 `drop if exists`、菜单字典先删后插、参数幂等 upsert、加列加索引走 `information_schema` 守卫），
升级即重跑。**必须带 `--default-character-set=utf8mb4`**，否则中文与表情会存成 `???`。

### 2. 构建

```bash
cd voxio-server && mvn -DskipTests clean package          # → voxio-admin/target/voxio-admin.jar
cd ../voxio-ui        && npm install && npm run build:prod # → dist/
cd ../voxio-cs-user-ui && npm install && npm run build      # → dist/
```

### 3. 启动

```bash
# 后端（同 JVM 内一并拉起 Netty 网关）
java -jar voxio-server/voxio-admin/target/voxio-admin.jar

# 两个前端（开发模式）
cd voxio-ui         && npm run dev     # http://localhost/admin/
cd voxio-cs-user-ui && npm run dev     # http://localhost:5173
```

| 入口 | 地址 | 默认账号 |
| --- | --- | --- |
| 管理后台 | http://localhost/admin/ | `admin` / `admin123` |
| 访客网页端 | http://localhost:5173 | 无需账号，自动分配访客号 |
| 后端 HTTP | http://localhost:10096/customer | — |
| 聊天网关 | ws://localhost:10097/ws · tcp://127.0.0.1:10098 | 客服令牌鉴权 |

## 配置说明

配置只有三份，全部明文，**不使用 `.env` 或外置配置文件**：

| 文件 | 放什么 |
| --- | --- |
| `application.yml` | 两个环境完全一致的内容：端口与上下文、连接池、网关参数、日志级别、加密开关 |
| `application-dev.yml` | 开发环境：本机 MySQL / Redis 地址与口令、上传落盘目录、密钥目录、日志级别 |
| `application-prod.yml` | 生产环境：服务器上的 MySQL / Redis 地址与口令、上传落盘目录、密钥目录、日志级别 |

jar 默认激活 `dev`，部署到服务器时显式切换：

```bash
java -jar voxio-admin.jar --spring.profiles.active=prod
```

两份 profile 的差异项一览（这也是「dev 与 prod 必须分开」的原因）：

| 项 | dev | prod |
| --- | --- | --- |
| MySQL 地址 / 库名 | `127.0.0.1:3306/voxio-public` | 服务器内网地址 |
| Redis 地址 / 库索引 | `127.0.0.1:6379/0` | 服务器内网地址 |
| 上传落盘 `voxio.profile` | `${user.home}/voxio/uploadPath` | `/home/voxio/voxio-server/uploadPath` |
| 国密密钥目录 | `${user.home}/voxio/keys` | `/home/voxio/keys` |
| 日志级别 | `com.voxio: debug` | `com.voxio: info` |
| Druid 监控台 | 开启 | 建议关闭或限内网 |

改配置 = 改对应 profile 文件 → 重新打包 jar。参数不随进程外注入，所以「线上跑的是哪套参数」只看 jar 本身。

## 界面预览

| 访客端 · 提交离线留言 | 访客端 · 与客服实时收发 |
| --- | --- |
| ![留言](doc/images/02-visitor-offline-dialog.png) | ![聊天](doc/images/04-visitor-chat-two-way.png) |

| 管理后台 · 登录后进入会话监控 | 会话详情 |
| --- | --- |
| ![侧栏](doc/images/07-admin-after-login.png) | ![监控](doc/images/08-admin-monitor.png) |

| 离线留言处理 | 工单 |
| --- | --- |
| ![留言列表](doc/images/09-admin-offline-message.png) | ![工单](doc/images/12-admin-ticket-created.png) |

## 客服端接入

客服侧的会话列表、历史消息、一键接入、结束会话、工单等 HTTP 接口，与访客端共用的网关 WS 协议，
都是完整开放的一套契约 —— 你可以基于它实现 Web 工作台、企业微信侧边栏或任意原生客户端：

```
POST /customer/kf/agent/login                 {agentNo, password}          → token
WS   ws://host:10097/ws  →  {cmd:"AUTH",         body:{token}}
                           {cmd:"AGENT_STATUS",  body:{onlineStatus:"1"}}
                           {cmd:"CHAT", sid, type:"text", body:{content, clientMsgId}}
```

| 用途 | 接口 |
| --- | --- |
| 我的会话列表 | `GET /kf/agent-app/sessions` |
| 会话历史消息 | `GET /kf/agent-app/sessions/{id}/messages` |
| 一键接入排队访客 | `POST /kf/agent-app/sessions/{id}/accept` |
| 结束会话 | `POST /kf/agent-app/sessions/{id}/end` |
| 在线客服与负载 | `GET /kf/agent-app/agents/online` |
| 建单 / 流转 / 回复 | `POST /kf/agent-app/ticket`、`PUT /kf/agent-app/ticket/{id}/status`、`POST /kf/agent-app/ticket/{id}/reply` |

帧结构见 `voxio-chat-netty/.../protocol/ChatEnvelope.java`，命令字见同目录 `ChatCmd.java`。

## 可选的国密加密

SM2 / SM3 / SM4-GCM 的实现完整保留，由一个开关控制，默认关闭以便二次开发时直接读库：

```yaml
voxio:
  crypto:
    enabled: false   # false 时聊天正文与业务字段明文落库
    enforce: false   # true 时忽略 enabled，用于「加密常开」的交付口径
```

同一个开关覆盖三处，不会出现「聊天密文但手机号明文」这种半开状态：聊天正文落库、网关每一帧的
加密与 HMAC-SM3 完整性、业务字段（手机号 / 邮箱 / 联系方式 / 工单与留言正文）。

开启步骤：

1. `enabled` 与 `enforce` 同时改 `true`，重新打包 jar。
2. 确认密钥目录里已有 `master-sm4.json` 与 `sm2-server.json`（首次启动自动生成），
   且启动日志没有「已回退到 classpath:keys/」这条 WARN。换目录等于换钥匙，已入库的密文会解不开。
3. 存量明文补迁：`java -jar voxio-admin.jar --kf.migrate=legacy-plaintext`
   （先写加密备份再逐列改写，可重复执行，失败按列回滚）。
4. 前端要同步：`voxio-ui` 与 `voxio-cs-user-ui` 的 `utils/chatCrypto.js`、Java 侧 `ChatCryptoService`
   是同一套契约（算法标识、`sm4-gcm$版本$base64` 载荷形态、SM3-HKDF 派生盐），只改一端会握手失败。

密钥目录不可写时（只读挂载、无可写 HOME 等），会回退读取随仓库发布的
`voxio-admin/src/main/resources/keys/` 开发密钥，保证首次启动不报错。
**那对密钥是公开可预测的，仅够跑通流程；对外提供服务前必须换成自己的密钥。**

口令哈希（`kf_agent.password` 的 `sm3$轮数$盐$摘要`）不受该开关影响 —— 它是认证凭据而非业务数据。

## 目录结构

```
voxio-public/
├── voxio-server/            Spring Boot 后端 + Netty 网关
│   ├── voxio-admin/         Web 入口与打包模块（application*.yml、logback.xml）
│   ├── voxio-chat-netty/    Netty 4.x 聊天网关
│   ├── voxio-desk/          客服业务域
│   ├── voxio-framework/     框架基建
│   ├── voxio-system/        若依系统域
│   ├── voxio-quartz/        定时任务
│   ├── voxio-common/        通用工具与国密层
│   └── sql/                 voxio_chat.sql —— 唯一建库脚本
├── voxio-ui/                管理后台（Vue3 + Vite + Element Plus）
│   └── brand/               设计 token 与品牌资产来源 + 分发脚本
├── voxio-cs-user-ui/        访客网页端（Vue3 + Vite）
├── doc/                     文档
│   ├── images/              界面截图
│   ├── nginx/voxio.conf     站点配置样例（可直接复制到 /etc/nginx/conf.d/）
│   └── 部署文档.docx         生产部署手册（jar + dist + nginx）
└── README.md
```

## 部署

生产部署只针对编译产物：后端 `voxio-admin.jar`、两个前端的 `dist/`、一份 nginx 配置。

完整步骤（含 nginx 站点配置、HTTPS 证书、WebSocket 升级转发、上传目录与密钥目录的落位、
启停与排障）见 **[doc/部署文档.docx](doc/部署文档.docx)**，可直接使用的站点配置在
**[doc/nginx/voxio.conf](doc/nginx/voxio.conf)**。

要点提示：

- **HTTPS 是加密链路的前提**。前端按 `location.protocol` 决定 `ws://` 还是 `wss://`，只上 HTTP 时聊天走明文。
  证书必须是 fullchain（leaf + intermediate 顺序不能反）。
- nginx 的 `/customer-ws` 必须带 `Upgrade` / `Connection` 头透传，否则 WebSocket 握手失败。
- 上传落盘目录要与 nginx 的 `location /customer/profile/` 的 alias 指向同一处。
- 数据库与密钥目录要**同频备份**：库备份了、密钥没备份，密文行等于永久丢失。

## 二次开发指引

| 想做的事 | 从哪里入手 |
| --- | --- |
| 加一个客服端界面 | 见[客服端接入](#客服端接入)，接口与协议已完整开放 |
| 改业务字段加密范围 | `voxio-desk` 实体字段挂 `SmFieldCipherTypeHandler`，Map 型视图走 `KfCipherRowReader` |
| 加访客端可见参数 | `kf_config` 增项 + `IKfConfigService` 的白名单，注意密钥类参数不得进白名单 |
| 改网关协议 | `ChatEnvelope` / `ChatCmd` / `ChatMessageDispatcher`，三端契约需同步 |
| 加定时任务 | `voxio-desk/.../job/` 下按现有三个 Job 的写法注册 |
| 换品牌色与标记 | `voxio-ui/brand/tokens.json` 是唯一来源，改完跑 `node voxio-ui/brand/sync-tokens.mjs` |

## 默认账号与出厂值

| 项 | 值 |
| --- | --- |
| 管理员 | `admin` / `admin123` |
| 客服 | `kefu01` / `kefu02` / `kefu03`，口令 `admin123`（`kefu03` 出厂停用） |
| 主管角色 | `role_id = 3`，授予客服业务菜单 |
| MySQL / Redis | `127.0.0.1`，口令 `wusiwee123`，库名 `voxio-public` |

> 以上都是**开发用的出厂默认值**。对外提供服务前，请务必改掉全部默认口令、
> 替换 `token.secret`，并换成自己的国密密钥。

## 许可

见各子工程 `package.json` / `pom.xml` 与所依赖的若依（RuoYi）、Element Plus、Netty、
BouncyCastle 等上游许可证。
