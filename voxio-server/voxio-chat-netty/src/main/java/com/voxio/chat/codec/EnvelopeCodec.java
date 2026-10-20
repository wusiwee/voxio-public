package com.voxio.chat.codec;

import com.alibaba.fastjson2.JSON;
import com.voxio.chat.protocol.ChatEnvelope;

/**
 * 信封 JSON 编解码（TCP 与 WebSocket 共用）
 * 
 * @author voxio
 */
public final class EnvelopeCodec
{
    private EnvelopeCodec()
    {
    }

    public static byte[] toBytes(ChatEnvelope envelope)
    {
        return JSON.toJSONBytes(envelope);
    }

    public static String toJson(ChatEnvelope envelope)
    {
        return JSON.toJSONString(envelope);
    }

    /**
     * 解析信封，非法 JSON 返回空
     * 
     * @param text JSON 文本
     * @return 信封
     */
    public static ChatEnvelope parse(String text)
    {
        try
        {
            return JSON.parseObject(text, ChatEnvelope.class);
        }
        catch (Exception e)
        {
            return null;
        }
    }
}
