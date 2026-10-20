package com.voxio.kf.event;

import java.util.LinkedHashMap;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;

/**
 * 网关下行事件（kf 业务层产生，由 Netty 网关投递）
 * 
 * @author voxio
 */
@Getter
@Setter
public class KfChatEvent
{
    /** 事件命令，与网关协议 cmd 保持一致 */
    private String cmd;

    /** 会话ID，可为空 */
    private Long sessionId;

    /** 事件负载 */
    private Map<String, Object> payload = new LinkedHashMap<>();

    public KfChatEvent(String cmd, Long sessionId)
    {
        this.cmd = cmd;
        this.sessionId = sessionId;
    }

    public KfChatEvent with(String key, Object value)
    {
        this.payload.put(key, value);
        return this;
    }
}
