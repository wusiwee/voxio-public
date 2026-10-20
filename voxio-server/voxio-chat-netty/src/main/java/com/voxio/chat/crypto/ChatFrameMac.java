package com.voxio.chat.crypto;

import com.voxio.chat.protocol.ChatEnvelope;
import com.voxio.common.utils.crypto.SmFrameMac;

/**
 * 帧完整性的网关侧适配：把信封字段摊平成 {@link SmFrameMac} 的规范化输入
 *
 * 规范化规则与密钥派生都在 common 里，三端共用同一份定义，这里只做协议对象到摘要输入的映射。
 */
public final class ChatFrameMac
{
    private ChatFrameMac()
    {
    }

    public static String controlKeyHex(String token)
    {
        return SmFrameMac.controlKeyHex(token);
    }

    public static String canonical(ChatEnvelope envelope)
    {
        return SmFrameMac.canonical(envelope.getCmd(), envelope.getSeq(), envelope.getSid(), envelope.getTs(),
                envelope.getType(), envelope.getMid(), envelope.getEncrypt(), envelope.getAlgo(),
                envelope.getKeyVersion(), envelope.getBody());
    }

    public static String sign(String keyHex, ChatEnvelope envelope)
    {
        return SmFrameMac.sign(keyHex, canonical(envelope));
    }

    public static boolean verify(String keyHex, ChatEnvelope envelope)
    {
        return SmFrameMac.verify(keyHex, canonical(envelope), envelope.getHmac());
    }
}
