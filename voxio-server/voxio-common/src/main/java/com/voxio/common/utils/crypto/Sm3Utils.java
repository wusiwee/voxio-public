package com.voxio.common.utils.crypto;

import org.bouncycastle.crypto.digests.SM3Digest;
import org.bouncycastle.crypto.macs.HMac;
import org.bouncycastle.crypto.params.KeyParameter;

/**
 * SM3 摘要与 HMAC-SM3（等保三级的完整性算法，替代 MD5/SHA 系）
 */
public final class Sm3Utils
{
    private Sm3Utils()
    {
    }

    public static byte[] hash(byte[] data)
    {
        SM3Digest digest = new SM3Digest();
        digest.update(data, 0, data.length);
        byte[] out = new byte[digest.getDigestSize()];
        digest.doFinal(out, 0);
        return out;
    }

    /** 返回 hex 小写 64 位 */
    public static String hashHex(byte[] data)
    {
        return SmCodec.hex(hash(data));
    }

    public static String hashHex(String text)
    {
        return hashHex(SmCodec.utf8(text));
    }

    /** HMAC-SM3，key 为原始字节，返回 hex 小写 */
    public static String hmacHex(byte[] key, byte[] data)
    {
        HMac mac = new HMac(new SM3Digest());
        mac.init(new KeyParameter(key));
        mac.update(data, 0, data.length);
        byte[] out = new byte[mac.getMacSize()];
        mac.doFinal(out, 0);
        return SmCodec.hex(out);
    }

    /** HMAC-SM3，key 为 hex 字符串 */
    public static String hmacHex(String hexKey, byte[] data)
    {
        return hmacHex(SmCodec.unhex(hexKey), data);
    }

    /** 恒定时间比较，避免签名校验被计时探测 */
    public static boolean constantTimeEquals(String a, String b)
    {
        if (a == null || b == null || a.length() != b.length())
        {
            return false;
        }
        int diff = 0;
        for (int i = 0; i < a.length(); i++)
        {
            diff |= a.charAt(i) ^ b.charAt(i);
        }
        return diff == 0;
    }
}
