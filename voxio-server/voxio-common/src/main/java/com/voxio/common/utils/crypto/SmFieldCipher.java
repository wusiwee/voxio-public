package com.voxio.common.utils.crypto;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.voxio.common.utils.StringUtils;

/**
 * 业务字段加解密（阶段10.3）
 *
 * 手机号、邮箱、联系方式、工单/留言正文这类个人信息按等保要求密文落库：
 * 用 SM4-GCM，密钥是 jar 外主密钥经 SM3-HKDF 派生的「field」子密钥，与聊天线路上的一次一密会话密钥、
 * 消息落库子密钥都不同串（一把泄露不等于全部失守）。列值形态与消息一致：{@code sm4-gcm${keyVersion}$base64(iv‖ct‖tag)}，版本号直接是十进制整数、不带 v。
 *
 * 读写一律走这里，不在业务代码里手搓加解密：{@link SmFieldCipherTypeHandler} 负责实体字段，
 * Map 型视图由 kf 侧的读取器负责，从而不会出现「某张列表漏了解密而把密文显示给用户」。
 *
 * @author voxio
 */
@Component
public class SmFieldCipher
{
    private static final Logger log = LoggerFactory.getLogger(SmFieldCipher.class);

    /** 业务字段子密钥用途标签 */
    public static final String PURPOSE_FIELD = "field";

    private final SmKeyStore keyStore;

    /**
     * 落库加密总开关，与聊天线路上的是同一个参数（{@code voxio.crypto.enabled}），
     * 这样「默认明文、方便二次开发」只需要改一处配置。
     * 关掉后新写入的行是可直读的明文；读取侧本来就能识别明文（{@link SmCodec#storedPayload} 解不出前缀就原样返回），
     * 所以之后再打开开关也不会读坏历史数据，只是新行才回到密文。
     */
    @Value("${voxio.crypto.enabled:true}")
    private boolean enabled;

    /** 派生一次复用；主密钥不变则子密钥不变 */
    private volatile String cachedKeyHex;

    private static SmFieldCipher instance;

    public SmFieldCipher(SmKeyStore keyStore)
    {
        this.keyStore = keyStore;
        instance = this;
    }

    /** 供 MyBatis 类型处理器取用（TypeHandler 由 MyBatis 实例化，不经 Spring） */
    public static SmFieldCipher get()
    {
        if (instance == null)
        {
            throw new IllegalStateException("加密组件尚未初始化，拒绝以明文写入业务字段");
        }
        return instance;
    }

    /** 空值与已加密的值原样返回，避免二次加密（更新时常见「读旧值再写回」） */
    public String encrypt(String plain)
    {
        if (StringUtils.isEmpty(plain) || SmCodec.isStoredCipher(plain) || !enabled)
        {
            return plain;
        }
        return SmCodec.wrapStored(Sm4Utils.encryptGcm(keyHex(), plain), keyStore.getKeyVersion());
    }

    /**
     * 解出库内字段值
     *
     * @return 明文；迁移前的存量明文原样返回；密钥换了导致解不出时返回 null（调用方按「读不到」处理，不外泄密文）
     */
    public String decrypt(String stored)
    {
        String payload = SmCodec.storedPayload(stored);
        if (payload == null)
        {
            return stored;
        }
        try
        {
            return Sm4Utils.decryptGcm(keyHex(), payload);
        }
        catch (Exception e)
        {
            log.warn("[字段加密] 解出失败（多半是主密钥已轮换）：{}", e.getMessage());
            return null;
        }
    }

    /** 派生的字段子密钥（hex32） */
    public String keyHex()
    {
        String key = cachedKeyHex;
        if (key == null)
        {
            key = keyStore.deriveKeyHex(PURPOSE_FIELD, SmCodec.SM4_KEY_BYTES);
            cachedKeyHex = key;
        }
        return key;
    }
}
