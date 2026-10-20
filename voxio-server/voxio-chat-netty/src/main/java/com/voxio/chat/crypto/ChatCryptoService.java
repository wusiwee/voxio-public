package com.voxio.chat.crypto;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import com.voxio.chat.protocol.ChatCmd;
import com.voxio.chat.protocol.ChatEnvelope;
import com.voxio.common.utils.StringUtils;
import com.voxio.common.utils.crypto.Sm2Utils;
import com.voxio.common.utils.crypto.Sm4Utils;
import com.voxio.common.utils.crypto.SmCodec;
import com.voxio.common.utils.crypto.SmKeyStore;
import com.voxio.kf.service.IKfSecurityService;
import com.voxio.kf.service.IKfSessionKeyService;

import io.netty.channel.Channel;
import io.netty.util.AttributeKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 网关聊天加密层：会话密钥协商、上行解密与下行加密
 *
 * 只做「body.content 的 SM4-GCM 加解密 + 帧 HMAC」，消息语义、幂等与落库仍由 kf 业务服务完成，
 * 网关不写 SQL、不承载业务规则（总控约束）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatCryptoService
{
    /** 帧上的加密标识值，与落库前缀同源（见 SmCodec.STORE_PREFIX） */
    public static final String ENCRYPT_FLAG = "sm4-gcm";

    /** SM4 密钥的 hex 长度：16 字节 → 32 字符 */
    private static final int KEY_HEX_LENGTH = SmCodec.SM4_KEY_BYTES * 2;

    private static final AttributeKey<ChatCryptoContext> CTX = AttributeKey.valueOf("kf.crypto");

    private final SmKeyStore keyStore;

    private final IKfSecurityService securityService;

    private final IKfSessionKeyService sessionKeyService;

    /**
     * 鉴权成功后挂上加密上下文：连接级 HMAC 密钥由令牌派生，之后所有帧都带 hmac
     *
     * @param channel 连接
     * @param token 鉴权令牌
     */
    public void attach(Channel channel, String token)
    {
        ChatCryptoContext context = new ChatCryptoContext();
        context.hmacKeyHex = securityService.isEnabled() ? ChatFrameMac.controlKeyHex(token) : null;
        channel.attr(CTX).set(context);
    }

    public ChatCryptoContext context(Channel channel)
    {
        return channel == null ? null : channel.attr(CTX).get();
    }

    public boolean isEnabled()
    {
        return securityService.isEnabled();
    }

    /** 对称算法标识，随 AUTH_OK 告知客户端 */
    public String algo()
    {
        return securityService.getAlgo();
    }

    /** 服务端主密钥版本 */
    public int serverKeyVersion()
    {
        return securityService.getKeyVersion();
    }

    /** 服务端 SM2 公钥，客户端用它加密会话密钥 */
    public String serverPublicKeyHex()
    {
        return securityService.getServerPublicKeyHex();
    }

    /** 该连接是否已为某会话持有密钥 */
    public boolean hasKey(Channel channel, Long sessionId)
    {
        ChatCryptoContext ctx = context(channel);
        return ctx != null && sessionId != null && ctx.sessionKeys.containsKey(sessionId);
    }

    /**
     * 处理 KEY_EXCHANGE：首写者建立会话密钥，后来者由服务端用其临时公钥回传同一把
     *
     * @param sessionId 会话ID
     * @param cipherHex SM2 加密的会话密钥 hex（C1C3C2）
     * @param clientPublicKeyHex 客户端临时 SM2 公钥
     * @return 协商结果，密钥材料不合法时返回 null
     */
    public Negotiation negotiate(Long sessionId, String cipherHex, String clientPublicKeyHex)
    {
        if (StringUtils.isEmpty(cipherHex) || StringUtils.isEmpty(clientPublicKeyHex))
        {
            return null;
        }
        String proposed;
        try
        {
            proposed = new String(Sm2Utils.decryptHex(keyStore.getServerPrivateKeyHex(), cipherHex),
                    StandardCharsets.UTF_8);
        }
        catch (Exception e)
        {
            log.warn("[会话密钥] SM2 解封失败：{}", e.getMessage());
            return null;
        }
        if (proposed == null || proposed.length() != KEY_HEX_LENGTH)
        {
            log.warn("[会话密钥] 解封结果不是 {} 位 hex 的 SM4 密钥，已拒绝", KEY_HEX_LENGTH);
            return null;
        }
        String effective = sessionKeyService.bindSessionKey(sessionId, proposed, securityService.getKeyVersion());
        if (effective == null)
        {
            return null;
        }
        // 会话已有密钥（对端先协商）：本次提议作废，改用既有密钥并加密回传给本端
        String keyCipher = effective.equals(proposed) ? null
                : Sm2Utils.encryptHex(clientPublicKeyHex, SmCodec.utf8(effective));
        return new Negotiation(effective, keyCipher, sessionKeyService.getSessionKeyVersion(sessionId));
    }

    /** 会话密钥登记到连接，之后该会话的消息帧才允许收发 */
    public void establish(Channel channel, Long sessionId, String keyHex)
    {
        ChatCryptoContext ctx = context(channel);
        if (ctx != null && sessionId != null && StringUtils.isNotEmpty(keyHex))
        {
            ctx.sessionKeys.put(sessionId, keyHex);
        }
    }

    /** 连接关闭时随 Channel 属性一起回收；会话结束可显式清掉 */
    public void release(Channel channel, Long sessionId)
    {
        ChatCryptoContext ctx = context(channel);
        if (ctx != null && sessionId != null)
        {
            ctx.sessionKeys.remove(sessionId);
        }
    }

    /**
     * 上行还原：先验帧 hmac，再解 body.content
     *
     * @return 处理结果，非 OK 时调用方按错误码回帧
     */
    public Inbound unwrapInbound(Channel channel, ChatEnvelope envelope)
    {
        ChatCryptoContext ctx = context(channel);
        if (ctx == null || !securityService.isEnabled())
        {
            return Inbound.OK;
        }
        if (ctx.hmacKeyHex != null && !ChatFrameMac.verify(ctx.hmacKeyHex, envelope))
        {
            log.warn("[帧完整性] 连接 {} 帧 hmac 校验失败 cmd={}", channel.id().asShortText(), envelope.getCmd());
            return Inbound.HMAC_FAILED;
        }
        if (!ENCRYPT_FLAG.equals(envelope.getEncrypt()))
        {
            return ChatCmd.CHAT.equals(envelope.getCmd()) ? Inbound.PLAINTEXT_REJECTED : Inbound.OK;
        }
        String keyHex = keyOf(ctx, envelope.getSid());
        String content = envelope.bodyText("content");
        if (keyHex == null)
        {
            return Inbound.KEY_MISSING;
        }
        if (StringUtils.isEmpty(content))
        {
            return Inbound.OK;
        }
        try
        {
            envelope.getBody().put("content", Sm4Utils.decryptGcm(keyHex, content));
            return Inbound.OK;
        }
        catch (Exception e)
        {
            log.warn("[消息解密] 会话 {} 正文解密失败：{}", envelope.getSid(), e.getMessage());
            return Inbound.DECRYPT_FAILED;
        }
    }

    /**
     * 下行封装：该会话在本连接已有密钥时把正文换成 SM4-GCM 密文，最后补帧 hmac
     *
     * @return 是否完成了正文加密（false 表示未协商密钥，帧按明文控制帧发出或已被调用方拦下）
     */
    public boolean wrapOutbound(Channel channel, ChatEnvelope envelope)
    {
        ChatCryptoContext ctx = context(channel);
        if (ctx == null || !securityService.isEnabled())
        {
            return false;
        }
        String keyHex = keyOf(ctx, envelope.getSid());
        String content = envelope.bodyText("content");
        if (keyHex != null && StringUtils.isNotEmpty(content) && !ENCRYPT_FLAG.equals(envelope.getEncrypt()))
        {
            envelope.getBody().put("content", Sm4Utils.encryptGcm(keyHex, content));
            envelope.setEncrypt(ENCRYPT_FLAG);
            envelope.setAlgo(securityService.getAlgo());
            envelope.setKeyVersion(sessionKeyService.getSessionKeyVersion(envelope.getSid()));
        }
        if (ctx.hmacKeyHex != null)
        {
            envelope.setHmac(ChatFrameMac.sign(ctx.hmacKeyHex, envelope));
        }
        return ENCRYPT_FLAG.equals(envelope.getEncrypt());
    }

    /** 上行帧处理结果 */
    public enum Inbound
    {
        OK, HMAC_FAILED, KEY_MISSING, DECRYPT_FAILED, PLAINTEXT_REJECTED
    }

    /** 协商结果 */
    public static final class Negotiation
    {
        /** 会话最终生效的密钥（仅在本连接上下文内使用，不回写日志） */
        private final String keyHex;

        /** 需要回传给客户端的密钥密文（沿用既有密钥时非空） */
        private final String keyCipher;

        private final int keyVersion;

        Negotiation(String keyHex, String keyCipher, int keyVersion)
        {
            this.keyHex = keyHex;
            this.keyCipher = keyCipher;
            this.keyVersion = keyVersion;
        }

        public String keyHex()
        {
            return keyHex;
        }

        public String keyCipher()
        {
            return keyCipher;
        }

        public int keyVersion()
        {
            return keyVersion;
        }
    }

    /** ConcurrentHashMap 不接受 null 键：控制帧通常不带 sid，这里统一返回 null 表示「无会话密钥」 */
    private static String keyOf(ChatCryptoContext ctx, Long sessionId)
    {
        return sessionId == null ? null : ctx.sessionKeys.get(sessionId);
    }

    /** 单连接的加密上下文 */
    public static final class ChatCryptoContext
    {
        private String hmacKeyHex;

        private final Map<Long, String> sessionKeys = new ConcurrentHashMap<>();
    }
}
