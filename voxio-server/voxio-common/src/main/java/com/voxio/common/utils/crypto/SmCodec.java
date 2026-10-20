package com.voxio.common.utils.crypto;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

/**
 * 国密改造的统一编码契约（三端必须一致，改动任何一条都要同步 Java / Web / C# 与固定向量）
 *
 * <pre>
 * 字节串   线上与落库一律用 base64（标准表，带 = 填充），十六进制只用于密钥材料与固定向量
 * SM4 密钥 16 字节，hex 小写 32 位
 * SM4-CBC  PKCS7 填充，密文 = base64( iv(16) || ciphertext )
 * SM4-GCM  12 字节 IV、16 字节标签，密文 = base64( iv(12) || ciphertext || tag(16) )
 * SM3      输出 hex 小写 64 位；HMAC-SM3 同样 hex 小写
 * HKDF     SM3-HKDF(ikm, salt, info) 按 RFC5869，ikm/salt 为 hex，info 为 UTF-8，输出 hex
 * SM2 密钥 私钥 hex 64 位；公钥 hex 130 位（04||X||Y，未压缩点）
 * SM2 密文 C1C3C2（sm-crypto cipherMode=1），C1 带 04 前缀，整体 hex 小写
 * SM2 签名 SM3withSM2，userId 固定 1234567812345678，输出 DER 签名 hex 小写（sm-crypto der:true）
 * 落库前缀 sm4-gcm${keyVersion}$base64(...)，版本号直接是十进制整数、不带 v（如 sm4-gcm$1$），用于区分密文版本与明文存量
 * </pre>
 *
 * GCM 的 IV 长度固定 12 字节，计数从 J0+1 开始（即首个明文块用 GHASH(H, E_K(J0+1)) 异或），
 * GHASH 用 SM4 块加密代替 AES 块加密；Java/前端/C# 三端各自实现，靠固定向量比对收敛。
 */
public final class SmCodec
{
    public static final byte[] SM2_USER_ID = "1234567812345678".getBytes(StandardCharsets.UTF_8);

    public static final int SM4_KEY_BYTES = 16;

    public static final int GCM_IV_BYTES = 12;

    public static final int GCM_TAG_BYTES = 16;

    /** 密文列的版本前缀：sm4-gcm${keyVersion}$base64(...)（版本号不带 v），读不到该前缀即视为阶段10.6 迁移前的存量明文 */
    public static final String STORE_PREFIX = "sm4-gcm$";

    private static final HexFormat HEX = HexFormat.of();

    private SmCodec()
    {
    }

    public static String hex(byte[] bytes)
    {
        return HEX.formatHex(bytes);
    }

    public static byte[] unhex(String hex)
    {
        return HEX.parseHex(hex);
    }

    public static String base64(byte[] bytes)
    {
        return java.util.Base64.getEncoder().encodeToString(bytes);
    }

    public static byte[] unbase64(String text)
    {
        return java.util.Base64.getDecoder().decode(text);
    }

    public static byte[] utf8(String text)
    {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    /** 是否带库内密文前缀（`sm4-gcm$`） */
    public static boolean isStoredCipher(String text)
    {
        return text != null && text.startsWith(STORE_PREFIX);
    }

    /** 组装库内密文列值：`sm4-gcm${keyVersion}$base64(...)`（版本号不带 v） */
    public static String wrapStored(String packedBase64, int keyVersion)
    {
        return STORE_PREFIX + keyVersion + "$" + packedBase64;
    }

    /** 取出库内密文里的 base64 载荷；形态不合法返回 null */
    public static String storedPayload(String text)
    {
        if (!isStoredCipher(text))
        {
            return null;
        }
        int end = text.indexOf('$', STORE_PREFIX.length());
        return end < 0 || end == text.length() - 1 ? null : text.substring(end + 1);
    }

    /** 取出库内密文的密钥版本；非密文或形态不合法返回 -1 */
    public static int storedKeyVersion(String text)
    {
        if (!isStoredCipher(text))
        {
            return -1;
        }
        int end = text.indexOf('$', STORE_PREFIX.length());
        if (end < 0)
        {
            return -1;
        }
        try
        {
            return Integer.parseInt(text.substring(STORE_PREFIX.length(), end));
        }
        catch (NumberFormatException e)
        {
            return -1;
        }
    }

    /** 拼接若干字节段 */
    public static byte[] concat(byte[]... parts)
    {
        int len = 0;
        for (byte[] p : parts)
        {
            len += p.length;
        }
        byte[] out = new byte[len];
        int at = 0;
        for (byte[] p : parts)
        {
            System.arraycopy(p, 0, out, at, p.length);
            at += p.length;
        }
        return out;
    }

    public static byte[] slice(byte[] src, int from, int length)
    {
        byte[] out = new byte[length];
        System.arraycopy(src, from, out, 0, length);
        return out;
    }
}
