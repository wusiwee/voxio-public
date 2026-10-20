package com.voxio.chat.protocol;

/**
 * 网关协议命令字（三端共用的一份契约，与前端 protocol 常量逐字对齐）
 * 
 * @author voxio
 */
public class ChatCmd
{
    /** 客户端 -> 服务端：鉴权 */
    public static final String AUTH = "AUTH";

    /** 服务端 -> 客户端：鉴权成功 */
    public static final String AUTH_OK = "AUTH_OK";

    /** 客户端 -> 服务端：心跳 */
    public static final String PING = "PING";

    /** 服务端 -> 客户端：心跳响应 */
    public static final String PONG = "PONG";

    /** 双向：聊天消息 */
    public static final String CHAT = "CHAT";

    /** 服务端 -> 客户端：投递回执 */
    public static final String ACK = "ACK";

    /** 客户端 -> 服务端：已读上报 */
    public static final String READ = "READ";

    /** 服务端 -> 客户端：已读通知 */
    public static final String READ_NOTIFY = "READ_NOTIFY";

    /** 客户端 -> 服务端：增量拉取 */
    public static final String SYNC = "SYNC";

    /** 服务端 -> 客服：新会话分配 */
    public static final String SESSION_NEW = "SESSION_NEW";

    /** 服务端 -> 访客：已分配客服 */
    public static final String SESSION_ASSIGNED = "SESSION_ASSIGNED";

    /** 服务端 -> 访客：排队位置变更 */
    public static final String SESSION_QUEUED = "SESSION_QUEUED";

    /** 服务端 -> 双方：会话结束 */
    public static final String SESSION_END = "SESSION_END";

    /** 客户端 -> 服务端：会话密钥协商（SM2 加密的 SM4 会话密钥） */
    public static final String KEY_EXCHANGE = "KEY_EXCHANGE";

    /** 双向：客服在线状态变更 */
    public static final String AGENT_STATUS = "AGENT_STATUS";

    /** 客户端 -> 服务端 -> 客户端：输入中提示，不落库 */
    public static final String TYPING = "TYPING";

    /** 服务端 -> 客户端：同账号重复连接被踢 */
    public static final String KICKED = "KICKED";

    /** 服务端 -> 客户端：错误 */
    public static final String ERROR = "ERROR";

    private ChatCmd()
    {
    }
}
