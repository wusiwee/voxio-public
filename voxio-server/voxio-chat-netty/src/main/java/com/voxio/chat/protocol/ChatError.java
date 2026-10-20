package com.voxio.chat.protocol;

/**
 * 网关错误码（三端共用的一份契约，与前端错误码常量逐字对齐）
 * 
 * @author voxio
 */
public class ChatError
{
    /** 鉴权失败 */
    public static final int AUTH_FAILED = 4001;

    /** 令牌过期 */
    public static final int TOKEN_EXPIRED = 4002;

    /** 对端不在线 */
    public static final int PEER_OFFLINE = 4003;

    /** 会话不存在或已结束 */
    public static final int SESSION_INVALID = 4004;

    /** 触发限流 */
    public static final int RATE_LIMITED = 4005;

    /** 消息体超限 */
    public static final int MESSAGE_TOO_LARGE = 4006;

    /** 协议错误 */
    public static final int PROTOCOL_ERROR = 4007;

    /** 重复绑定 */
    public static final int DUPLICATE_CONNECTION = 4008;

    /** 无权操作该会话 */
    public static final int NO_PERMISSION = 4009;

    /** 会话密钥尚未协商，无法收发加密消息 */
    public static final int KEY_REQUIRED = 4010;

    /** 解密或完整性校验失败（密文被篡改、密钥版本不符、HMAC 不匹配） */
    public static final int CRYPTO_FAILED = 4011;

    private ChatError()
    {
    }
}
