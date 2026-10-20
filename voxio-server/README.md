# voxio-server · 服务端与聊天网关

Voxio 在线客服的后端：一个 Spring Boot 进程同时提供 HTTP 接口与 Netty 聊天网关。

## 技术栈

| 用途 | 选型 |
| --- | --- |
| 基础框架 | Spring Boot 3.5.16 + Spring Security |
| 持久层 | MyBatis-Plus 3.5.17（BaseMapper / IService / Lambda 条件构造器） |
| 认证 | JWT，管理端、客服端、访客端各自独立令牌 |
| 缓存与分布式 | Redis + Redisson |
| 连接池与监控 | Druid 1.2.28 |
| 接口文档 | SpringDoc 2.9.0 |
| 定时任务 | Quartz（会话无活动自动结束、日志留存清理） |
| 实时通信 | Netty 4（WebSocket + 自定义 TCP 双通道，同一套 JSON 信封） |
| 国密 | BouncyCastle `bcprov/bcpkix-jdk18on`，SM2/SM3/SM4 唯一第三方实现 |
| 导入导出 | Apache POI + EasyExcel |
| 前端 | Vue 3 + Vite + Element Plus（见同级 `voxio-ui` / `voxio-cs-user-ui`） |

## 模块

```
voxio-server
├── voxio-admin          Web 入口与打包模块（打出的 jar 是唯一投递物：voxio-admin.jar）
├── voxio-chat-netty     Netty 聊天网关（与 admin 同 JVM 启动，端口 10097 / 10098）
├── voxio-desk           客服业务：会话、消息、工单、留言、监控、审计、开放接口、定时任务
├── voxio-framework      安全过滤链、数据源、缓存、切面、拦截器
├── voxio-system         系统域（用户/角色/菜单/部门/岗位/字典/参数/公告/日志）
├── voxio-quartz         定时任务基座
├── voxio-common         通用工具、域模型基类、国密加解密层
└── sql                  voxio_chat.sql —— 唯一建库脚本（建表 + 初始化数据，可重复执行）
```

## 客服业务域

| 子域 | 关键类 | 说明 |
| --- | --- | --- |
| 会话 | `KfSessionServiceImpl` | 状态机（排队 / 接待 / 结束）、自动分配、人工指派、超时结束、序号水位 |
| 消息 | `KfMessageServiceImpl` | 幂等落库、投递状态、已读上报、向前翻页与游标分页 |
| 网关 | `ChatServer` / `ChatConnectionHandler` / `ChatMessageDispatcher` | 鉴权、心跳、限流、路由、收发、补发、指标 |
| 工单 | `KfTicketServiceImpl` | 建单、状态流转、回复留痕、导出、第三方手动推送 |
| 离线留言 | `KfOfflineMessageServiceImpl` | 留言受理、状态流转、一键转工单 |
| 会话监控 | `KfMonitorServiceImpl` | 进行中会话与客服负载的实时视图 |
| 业务参数 | `KfConfigServiceImpl` | `kf_config` 键值；访客可见项走白名单，密钥类参数不下发 |
| 安全策略 | `KfSecurityServiceImpl` | `kf_security_config`：锁定阈值、口令策略、留存期、加密口径 |
| 审计留痕 | `KfAuditAspect` / `KfAuditLogServiceImpl` | 业务写操作留痕，参数结构化打码，只读 |
| 开放接口 | `KfOpen*Controller` | 访客端匿名/令牌接口 |
| 客服接口 | `KfAgent*Controller` | 客服登录、会话列表、历史、接入、结束、工单 |

## 运行

```bash
# 建库（见根 README「建库」一节）
mysql -u root -p --default-character-set=utf8mb4 voxio-public < sql/voxio_chat.sql

# 构建（产物 voxio-admin/target/voxio-admin.jar）
mvn -DskipTests clean package

# 启动（默认 dev profile；服务器上用 prod）
java -jar voxio-admin/target/voxio-admin.jar
java -jar voxio-admin/target/voxio-admin.jar --spring.profiles.active=prod
```

配置分三份，全部明文，没有 `env.properties` 也没有 `.env`：

| 文件 | 内容 |
| --- | --- |
| `voxio-admin/src/main/resources/application.yml` | 与环境无关：端口与上下文、连接池容量、网关参数、令牌有效期、MyBatis 与分页、加密开关 |
| `application-dev.yml` | 开发环境：本机 MySQL / Redis、落盘目录、密钥目录、JWT 密钥、debug 日志、开启 Druid 监控台 |
| `application-prod.yml` | 生产环境：服务器中间件地址与口令、绝对落盘路径、密钥目录、独立 JWT 密钥、info 日志、关闭 Druid 监控台 |

上线到服务器（jar 投递、systemd 托管、nginx 反代与 wss、证书链、备份回滚）见
**[../doc/部署文档.docx](../doc/部署文档.docx)**，站点配置样例在 **[../doc/nginx/voxio.conf](../doc/nginx/voxio.conf)**。

## 接口面

| 前缀 | 面向 | 鉴权 |
| --- | --- | --- |
| `/kf/open/**` | 访客端 | 匿名或访客令牌；`/kf/open/config` 是白名单，密钥类参数永不下发 |
| `/kf/agent/login`、`/kf/agent-app/**` | 客服端 | 客服令牌 |
| `/kf/monitor/**`、`/kf/ticket/**`、`/kf/offline/**`、`/kf/settings/**`、`/kf/auditlog/**`、`/kf/loginlog/**` | 管理后台 | RBAC |
| `ws://:10097/ws`、`tcp://:10098` | 聊天网关 | 令牌 AUTH 帧 |

接口与网关帧的完整说明见根 `README.md` 的「实时消息协议」与「客服端接入」。

## 消息类型

聊天只支持文本与系统事件两类（`msg_type` = `1` 文本 / `4` 系统事件），没有图片与文件消息，
因此也不存在附件落盘、缩略图与签名下载这条链路。网关收到带附件字段的帧会直接回协议错误。
