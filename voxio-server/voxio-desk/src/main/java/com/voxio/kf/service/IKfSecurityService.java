package com.voxio.kf.service;

import java.util.Map;

/**
 * 国密安全策略服务：加密开关、密钥材料与三端互通向量的统一入口
 *
 * @author voxio
 */
public interface IKfSecurityService
{
    /** 加密总开关（kf_security_config: encrypt.enabled），生产强制为真 */
    boolean isEnabled();

    /** 对称加密算法标识，随消息 envelope 的 algo 字段下发 */
    String getAlgo();

    /** 当前密钥版本，轮换后递增 */
    int getKeyVersion();

    /** 服务端 SM2 公钥（hex），客户端用它加密会话密钥 */
    String getServerPublicKeyHex();

    /** 按用途派生 SM4 子密钥（hex），用途如 chat-session / field / api-hmac */
    String deriveKeyHex(String purpose);

    /** 校验 kf_security_config 里的安全策略键是否可读（供管理端与门禁使用） */
    String getConfig(String key, String defaultValue);

    /** 更新安全策略键（管理端），同时清缓存 */
    void setConfig(String key, String value);

    /** 三端互通固定向量（Java 侧计算结果） */
    Map<String, Object> vectors();

    /** 验证客户端对固定向量消息的 SM2 签名 */
    boolean verifySignature(String signatureHex);

    /**
     * 用固定向量的示例密钥对（GM/T 0003 公开测试值）解密客户端密文，并比对是否等于固定消息。
     * 只证明「客户端加密 → 服务端解密」互通，不涉及任何生产密钥材料。
     */
    boolean verifyVectorCipher(String cipherHex);

    /** 可公开的安全参数（开关、算法、公钥、密钥版本），不含任何私钥材料 */
    Map<String, Object> publicStatus();
}
