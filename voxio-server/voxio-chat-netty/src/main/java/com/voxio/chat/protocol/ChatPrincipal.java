package com.voxio.chat.protocol;

import lombok.Getter;

/**
 * 网关连接主体（鉴权后绑定到 Channel）
 * 
 * @author voxio
 */
@Getter
public class ChatPrincipal
{
    /** 主体类型：客服 */
    public static final String TYPE_AGENT = "agent";

    /** 主体类型：访客 */
    public static final String TYPE_VISITOR = "visitor";

    /** 主体类型：系统 */
    public static final String TYPE_SYSTEM = "system";

    /** 主体类型 */
    private final String type;

    /** 主体ID */
    private final Long id;

    /** 展示名称 */
    private final String name;

    /** 访客绑定的会话ID */
    private final Long sessionId;

    /** 原始令牌，用于心跳时二次校验 */
    private final String token;

    public ChatPrincipal(String type, Long id, String name, Long sessionId, String token)
    {
        this.type = type;
        this.id = id;
        this.name = name;
        this.sessionId = sessionId;
        this.token = token;
    }

    /**
     * 注册表键
     * 
     * @return agent:100 / visitor:1001
     */
    public String key()
    {
        return type + ":" + id;
    }

    public static String key(String type, Long id)
    {
        return type + ":" + id;
    }

    public boolean isAgent()
    {
        return TYPE_AGENT.equals(type);
    }

    public boolean isVisitor()
    {
        return TYPE_VISITOR.equals(type);
    }
}
