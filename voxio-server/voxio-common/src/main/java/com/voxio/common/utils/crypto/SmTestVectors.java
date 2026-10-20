package com.voxio.common.utils.crypto;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 三端互通固定向量：同一组输入在 Java / 浏览器(sm-crypto) / C#(BouncyCastle) 各算一遍，结果必须逐字节相同
 *
 * 这里只放确定性的算法（SM3/HMAC/HKDF/SM4 固定 IV）与「用固定密钥对签名给对方验」的能力；
 * SM2 加密与签名都带随机数，无法比对固定输出，跨端解密由 10.2 的真实握手链路证明。
 */
public final class SmTestVectors
{
    /** 含中文与 4 字节表情，顺带验证三端的 UTF-8 处理一致 */
    public static final String MESSAGE = "Voxio 国密互通向量😀";

    public static final String SM4_KEY_HEX = "0123456789abcdeffedcba9876543210";

    public static final String CBC_IV_HEX = "000102030405060708090a0b0c0d0e0f";

    public static final String GCM_IV_HEX = "000102030405060708090a0b";

    public static final String HMAC_KEY_HEX = "31323334353637383132333435363738";

    public static final String HKDF_IKM_HEX = "0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b";

    public static final String HKDF_SALT_HEX = "000102030405060708090a0b0c";

    public static final String HKDF_INFO = "voxio-desk-chat";

    public static final int HKDF_LENGTH = 32;

    /** 向量专用固定 SM2 私钥（GM/T 0003 示例值），只用于互通验证，不参与任何生产密钥 */
    public static final String SM2_PRIVATE_HEX =
            "3945209f7b21c43b3f1479c3954447d3d02e7e4e60a2c98688b37f203d86963c";

    /** 帧完整性向量用的令牌样本（只用于比对，不是任何环境的真实令牌） */
    public static final String CONTROL_TOKEN = "voxio-demo-token-0123456789";

    /** 帧完整性向量用的时间戳（固定，保证三端算出同一个摘要） */
    public static final long FRAME_TS = 1700000000000L;

    private SmTestVectors()
    {
    }

    /** 规范化帧向量样本：负载里带 = & % 换行与 null 值，正是三端最容易编出两种结果的地方 */
    public static Map<String, Object> frameBody()
    {
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("serverTime", FRAME_TS);
        body.put("typing", 1);
        body.put("enabled", true);
        body.put("note", "含中文&等号=与%号");
        body.put("skipped", null);
        return body;
    }

    public static String frameCanonical()
    {
        return SmFrameMac.canonical("PING", 7L, null, FRAME_TS, null, null, null, null, null, frameBody());
    }


    public static String publicKeyHex()
    {
        return Sm2Utils.publicKeyFromPrivate(SM2_PRIVATE_HEX);
    }

    /** Java 侧签名，交给前端验；反向由 {@link #verifySignature} 完成 */
    public static String signatureHex()
    {
        return Sm2Utils.signHex(SM2_PRIVATE_HEX, SmCodec.utf8(MESSAGE));
    }

    public static boolean verifySignature(String signatureHex)
    {
        return Sm2Utils.verifyHex(publicKeyHex(), SmCodec.utf8(MESSAGE), signatureHex);
    }

    /**
     * 前端/C# 用向量公钥加密固定消息后交给服务端解密，证明「客户端加密 → 服务端解密」互通
     *
     * 这里只用 GM/T 0003 公开的示例密钥，与生产密钥材料无关，因此可以安全地对外提供解密比对。
     */
    public static boolean matchesVectorCipher(String cipherHex)
    {
        if (cipherHex == null || cipherHex.length() < 162 || cipherHex.length() % 2 != 0)
        {
            return false;
        }
        try
        {
            return MESSAGE.equals(new String(Sm2Utils.decryptHex(SM2_PRIVATE_HEX, cipherHex),
                    java.nio.charset.StandardCharsets.UTF_8));
        }
        catch (Exception e)
        {
            return false;
        }
    }

    /** 固定输入 → 各算法输出，字段名即三端比对键 */
    public static Map<String, Object> build()
    {
        byte[] key = SmCodec.unhex(SM4_KEY_HEX);
        byte[] plain = SmCodec.utf8(MESSAGE);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("message", MESSAGE);
        out.put("sm3MessageHex", Sm3Utils.hashHex(plain));
        out.put("sm3HmacHex", Sm3Utils.hmacHex(SmCodec.unhex(HMAC_KEY_HEX), plain));
        out.put("hkdfHex", Sm3Hkdf.deriveHex(HKDF_IKM_HEX, HKDF_SALT_HEX, HKDF_INFO, HKDF_LENGTH));
        out.put("sm4CbcPackedBase64", Sm4Utils.encryptCbcWithIv(key, SmCodec.unhex(CBC_IV_HEX), plain));
        out.put("sm4GcmPackedBase64", Sm4Utils.encryptGcmWithIv(key, SmCodec.unhex(GCM_IV_HEX), plain));
        out.put("sm2PublicKeyHex", publicKeyHex());
        out.put("sm2SignatureHex", signatureHex());
        // SM2 自环：加密后立刻解密，验证 C1C3C2 编解码在本端一致（跨端解密由握手链路证明）
        String cipher = Sm2Utils.encryptHex(publicKeyHex(), plain);
        out.put("sm2CipherHex", cipher);
        out.put("sm2CipherLength", cipher.length());
        out.put("sm2SelfRoundTrip", MESSAGE.equals(new String(Sm2Utils.decryptHex(SM2_PRIVATE_HEX, cipher),
                java.nio.charset.StandardCharsets.UTF_8)));
        // 帧完整性：令牌派生密钥 + 规范化串 + 摘要，三端必须逐字符一致
        String controlKey = SmFrameMac.controlKeyHex(CONTROL_TOKEN);
        out.put("controlToken", CONTROL_TOKEN);
        out.put("controlKeyHex", controlKey);
        out.put("frameCanonical", frameCanonical());
        out.put("frameHmacHex", SmFrameMac.sign(controlKey, frameCanonical()));
        return out;
    }
}
