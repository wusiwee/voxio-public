package com.voxio.kf.service;

/**
 * 会话密钥服务：聊天消息加密的密钥建立与取用（阶段10.2）
 *
 * 会话密钥 K 由客户端生成、SM2 加密上送，服务端只在内存里持有明文 K，
 * 库里落的是「主密钥派生的包装密钥」加密后的 K（kf_session.sm4_key_cipher）。
 *
 * @author voxio
 */
public interface IKfSessionKeyService
{
    /**
     * 建立或复用会话密钥
     *
     * @param sessionId 会话ID
     * @param keyHex 客户端提议的会话密钥（hex32）
     * @param keyVersion 服务端密钥版本（主密钥版本）
     * @return 该会话最终生效的密钥（可能不是本次提议的那把，首写者胜出）
     */
    String bindSessionKey(Long sessionId, String keyHex, int keyVersion);

    /** 取会话密钥明文（hex32）；尚未协商或校验不通过返回 null */
    String getSessionKey(Long sessionId);

    /**
     * 按用途派生子密钥：消息落库用 msg-store，与线路上的会话密钥不同串，
     * 避免「拿到库内密文就等于能解线路流量」
     *
     * @param sessionId 会话ID
     * @param purpose 用途标签
     * @return 派生的 SM4 密钥 hex32，会话无密钥时返回 null
     */
    String deriveSessionKey(Long sessionId, String purpose);

    /** 会话密钥版本（0 表示尚未协商） */
    int getSessionKeyVersion(Long sessionId);

    /** 忘记某会话的密钥（会话结束或密钥轮换时调用） */
    void evict(Long sessionId);

    /** 落库加密是否生效（voxio.crypto.enforce + encrypt.enabled） */
    boolean isEncryptEnabled();

    /**
     * 解出库内密文文本：前缀里的密钥版本 &gt;0 用会话密钥，=0 用主密钥派生的兜底钥
     *
     * @param sessionId 会话ID
     * @param stored 库内字段值
     * @return 明文；传入本来就是存量明文则原样返回，密钥失效或密文被改时返回 null
     */
    String decryptStored(Long sessionId, String stored);

    /**
     * 加密待落库的文本，返回带版本前缀的密文
     *
     * 会话没协商过密钥时用主密钥派生的兜底钥（版本号写 0），保证任何写入路径都不落明文；
     * 只有部署上关闭加密（开发机 enforce=false）才原样返回明文。
     */
    String encryptForStore(Long sessionId, String plain);
}
