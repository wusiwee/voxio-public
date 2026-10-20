package com.voxio.common.utils.crypto;

import org.bouncycastle.crypto.digests.SM3Digest;
import org.bouncycastle.crypto.macs.HMac;
import org.bouncycastle.crypto.params.KeyParameter;

/**
 * SM3-HKDF（RFC5869 换哈希算法）：从主密钥派生各类子密钥，避免一把密钥同时用于消息、字段与附件
 */
public final class Sm3Hkdf
{
    private static final int HASH_LEN = 32;

    private Sm3Hkdf()
    {
    }

    /**
     * @param ikmHex  输入密钥材料（hex）
     * @param saltHex 盐（hex，可为空串）
     * @param info    上下文标签（UTF-8）
     * @param length  需要的字节数
     * @return 派生密钥 hex
     */
    public static String deriveHex(String ikmHex, String saltHex, String info, int length)
    {
        byte[] prk = mac(SmCodec.unhex(saltHex), SmCodec.unhex(ikmHex));
        byte[] out = new byte[length];
        byte[] t = new byte[0];
        int at = 0;
        for (int i = 1; at < length; i++)
        {
            byte[] input = SmCodec.concat(t, SmCodec.utf8(info), new byte[] { (byte) i });
            t = mac(prk, input);
            int take = Math.min(t.length, length - at);
            System.arraycopy(t, 0, out, at, take);
            at += take;
        }
        return SmCodec.hex(out);
    }

    private static byte[] mac(byte[] key, byte[] data)
    {
        HMac hmac = new HMac(new SM3Digest());
        hmac.init(new KeyParameter(key.length == 0 ? new byte[HASH_LEN] : key));
        hmac.update(data, 0, data.length);
        byte[] out = new byte[hmac.getMacSize()];
        hmac.doFinal(out, 0);
        return out;
    }
}
