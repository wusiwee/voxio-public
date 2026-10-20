package com.voxio.chat.protocol;

import java.util.LinkedHashMap;
import java.util.Map;

import com.alibaba.fastjson2.annotation.JSONField;

import lombok.Getter;
import lombok.Setter;

/**
 * 网关消息信封（WebSocket 文本帧与 TCP 长度前缀帧共用同一 JSON 结构）
 * 
 * @author voxio
 */
@Getter
@Setter
public class ChatEnvelope
{
    /** 协议版本 */
    private Integer v = 1;

    /** 命令字，见 ChatCmd */
    private String cmd;

    /** 客户端自增序号，服务端回包原样带回 */
    private Long seq;

    /** 消息唯一ID */
    private String mid;

    /** 会话ID */
    private Long sid;

    /** 发送方 */
    private Party from;

    /** 接收方，为空表示由网关按会话归属路由 */
    private Party to;

    /** 内容类型：text/image/file/event */
    private String type;

    /** 业务负载 */
    private Map<String, Object> body = new LinkedHashMap<>();

    /** 时间戳（毫秒） */
    private Long ts;

    /**
     * 加密标识（阶段10.2）：为 "sm4-gcm" 时表示 body.content 是 SM4-GCM 密文 packed（base64），
     * 为 null 表示本帧未加密（密钥协商前的 AUTH / KEY_EXCHANGE，以及未启用加密的开发机）。
     * 只加字段不改既有字段与命令语义，老客户端仍可收发未加密帧。
     */
    private String encrypt;

    /** 对称算法标识（SM4-GCM），与 encrypt 同时出现 */
    private String algo;

    /** 帧完整性摘要：HMAC-SM3(controlKey, 规范化帧)，十六进制小写 */
    private String hmac;

    /** 会话密钥版本，便于轮换后定位是哪把密钥解的 */
    private Integer keyVersion;

    /**
     * 负载兜底：网络帧携带 body:null 时避免空指针
     */
    public Map<String, Object> getBody()
    {
        if (body == null)
        {
            body = new LinkedHashMap<>();
        }
        return body;
    }

    @JSONField(serialize = false)
    public String bodyText(String key)
    {
        Object value = body == null ? null : body.get(key);
        return value == null ? null : String.valueOf(value);
    }

    @JSONField(serialize = false)
    public Long bodyLong(String key)
    {
        Object value = body == null ? null : body.get(key);
        if (value == null)
        {
            return null;
        }
        return value instanceof Number ? ((Number) value).longValue() : Long.valueOf(String.valueOf(value));
    }

    @JSONField(serialize = false)
    public Integer bodyInt(String key)
    {
        Long value = bodyLong(key);
        return value == null ? null : value.intValue();
    }

    /**
     * 参与方
     */
    @Getter
    @Setter
    public static class Party
    {
        /** 类型：visitor / agent / system */
        private String t;

        /** 主体ID（字符串承载，避免前端精度丢失） */
        private String id;

        public Party()
        {
        }

        public Party(String t, Object id)
        {
            this.t = t;
            this.id = id == null ? null : String.valueOf(id);
        }
    }
}
