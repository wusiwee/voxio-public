package com.voxio.common.utils.crypto;

import java.security.SecureRandom;
import java.security.Security;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

/**
 * SM4 对称加解密：聊天正文与敏感字段用 GCM（认证加密），CBC/PKCS7 只用于与外部系统的固定向量互通
 *
 * 编码见 {@link SmCodec}：GCM = base64(iv12 || tag16 || ct)，CBC = base64(iv16 || ct)。
 */
public final class Sm4Utils
{
    private static final SecureRandom RANDOM = new SecureRandom();

    static
    {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null)
        {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    private Sm4Utils()
    {
    }

    /** 生成一把随机 SM4 会话密钥（hex 32 位） */
    public static String randomKeyHex()
    {
        byte[] key = new byte[SmCodec.SM4_KEY_BYTES];
        RANDOM.nextBytes(key);
        return SmCodec.hex(key);
    }

    public static byte[] randomBytes(int len)
    {
        byte[] out = new byte[len];
        RANDOM.nextBytes(out);
        return out;
    }

    /** SM4-GCM 加密，返回 base64(iv || tag || ciphertext) */
    public static String encryptGcm(String hexKey, String plaintext)
    {
        return encryptGcm(SmCodec.unhex(hexKey), SmCodec.utf8(plaintext));
    }

    public static String encryptGcm(byte[] key, byte[] plaintext)
    {
        return SmCodec.base64(encryptGcmPacked(key, plaintext));
    }

    /** SM4-GCM 加密，返回原始 packed 字节（iv || tag || ciphertext）：附件落盘用，省掉 base64 多出的三分之一体积 */
    public static byte[] encryptGcmPacked(byte[] key, byte[] plaintext)
    {
        try
        {
            byte[] iv = randomBytes(SmCodec.GCM_IV_BYTES);
            Cipher cipher = cipher("SM4/GCM/NoPadding", Cipher.ENCRYPT_MODE, key,
                    new GCMParameterSpec(SmCodec.GCM_TAG_BYTES * 8, iv));
            // BC 的 GCM 把标签直接接在密文尾部，正好等于契约里的 tag || ciphertext
            return SmCodec.concat(iv, cipher.doFinal(plaintext));
        }
        catch (Exception e)
        {
            throw new IllegalStateException("SM4-GCM 加密失败：" + e.getMessage(), e);
        }
    }

    /**
     * 流式 SM4-GCM 加密：分块读入、分块写出，产出的字节序列与 {@link #encryptGcmPacked} 完全一致
     * （同样是 iv12 || ciphertext || tag16），所以附件的落盘格式、下载解密与验收断言都不受影响。
     *
     * 阶段11：原先上传要在内存里同时存在「原文 byte[] + 密文 byte[]」两份，加上 MultipartFile
     * 自身与落盘缓冲，一张 10MB 图片峰值接近 40MB；改成 64KB 一块直写文件后与文件大小无关。
     *
     * @param key SM4 密钥
     * @param in 明文输入流（由调用方负责关闭）
     * @param out 密文输出流（由调用方负责关闭）
     * @return 写出的字节数
     */
    public static long encryptGcmStream(byte[] key, java.io.InputStream in, java.io.OutputStream out)
            throws java.io.IOException
    {
        byte[] iv = randomBytes(SmCodec.GCM_IV_BYTES);
        Cipher cipher;
        try
        {
            cipher = cipher("SM4/GCM/NoPadding", Cipher.ENCRYPT_MODE, key,
                    new GCMParameterSpec(SmCodec.GCM_TAG_BYTES * 8, iv));
        }
        catch (Exception e)
        {
            throw new IllegalStateException("SM4-GCM 流式加密初始化失败：" + e.getMessage(), e);
        }
        try
        {
            out.write(iv);
            long written = SmCodec.GCM_IV_BYTES;
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = in.read(buffer)) > 0)
            {
                // GCM 会按 16 字节块对齐缓冲，不足一块时 update 返回空，交给最后的 doFinal 补齐
                byte[] chunk = cipher.update(buffer, 0, read);
                if (chunk != null && chunk.length > 0)
                {
                    out.write(chunk);
                    written += chunk.length;
                }
            }
            byte[] tail = cipher.doFinal();
            out.write(tail);
            return written + tail.length;
        }
        catch (java.security.GeneralSecurityException e)
        {
            throw new IllegalStateException("SM4-GCM 流式加密失败：" + e.getMessage(), e);
        }
    }

    /** 用给定 IV 做 GCM 加密（固定向量互通用；正常业务一律走随机 IV） */
    public static String encryptGcmWithIv(byte[] key, byte[] iv, byte[] plaintext)
    {
        try
        {
            Cipher cipher = cipher("SM4/GCM/NoPadding", Cipher.ENCRYPT_MODE, key,
                    new GCMParameterSpec(SmCodec.GCM_TAG_BYTES * 8, iv));
            return SmCodec.base64(SmCodec.concat(iv, cipher.doFinal(plaintext)));
        }
        catch (Exception e)
        {
            throw new IllegalStateException("SM4-GCM 加密失败：" + e.getMessage(), e);
        }
    }

    /** SM4-GCM 解密；密文被篡改或密钥不符时抛异常，由调用方按「解密失败」处理 */
    public static String decryptGcm(String hexKey, String packedBase64)
    {
        return new String(decryptGcm(SmCodec.unhex(hexKey), SmCodec.unbase64(packedBase64)), java.nio.charset.StandardCharsets.UTF_8);
    }

    public static byte[] decryptGcm(byte[] key, byte[] packed)
    {
        try
        {
            byte[] iv = SmCodec.slice(packed, 0, SmCodec.GCM_IV_BYTES);
            byte[] ct = SmCodec.slice(packed, SmCodec.GCM_IV_BYTES, packed.length - SmCodec.GCM_IV_BYTES);
            Cipher cipher = cipher("SM4/GCM/NoPadding", Cipher.DECRYPT_MODE, key,
                    new GCMParameterSpec(SmCodec.GCM_TAG_BYTES * 8, iv));
            return cipher.doFinal(ct);
        }
        catch (Exception e)
        {
            throw new IllegalStateException("SM4-GCM 解密失败：" + e.getMessage(), e);
        }
    }

    /** SM4-CBC/PKCS7，返回 base64(iv || ciphertext)，IV 随机且随密文一起传 */
    public static String encryptCbc(byte[] key, byte[] plaintext)
    {
        try
        {
            byte[] iv = randomBytes(16);
            Cipher cipher = cipher("SM4/CBC/PKCS7Padding", Cipher.ENCRYPT_MODE, key, new IvParameterSpec(iv));
            return SmCodec.base64(SmCodec.concat(iv, cipher.doFinal(plaintext)));
        }
        catch (Exception e)
        {
            throw new IllegalStateException("SM4-CBC 加密失败：" + e.getMessage(), e);
        }
    }

    /** 用给定 IV 做 CBC 加密，只为固定向量互通用 */
    public static String encryptCbcWithIv(byte[] key, byte[] iv, byte[] plaintext)
    {
        try
        {
            Cipher cipher = cipher("SM4/CBC/PKCS7Padding", Cipher.ENCRYPT_MODE, key, new IvParameterSpec(iv));
            return SmCodec.base64(SmCodec.concat(iv, cipher.doFinal(plaintext)));
        }
        catch (Exception e)
        {
            throw new IllegalStateException("SM4-CBC 加密失败：" + e.getMessage(), e);
        }
    }

    public static byte[] decryptCbc(byte[] key, byte[] packed)
    {
        try
        {
            byte[] iv = SmCodec.slice(packed, 0, 16);
            byte[] ct = SmCodec.slice(packed, 16, packed.length - 16);
            Cipher cipher = cipher("SM4/CBC/PKCS7Padding", Cipher.DECRYPT_MODE, key, new IvParameterSpec(iv));
            return cipher.doFinal(ct);
        }
        catch (Exception e)
        {
            throw new IllegalStateException("SM4-CBC 解密失败：" + e.getMessage(), e);
        }
    }

    private static Cipher cipher(String transformation, int mode, byte[] key, java.security.spec.AlgorithmParameterSpec spec) throws Exception
    {
        Cipher cipher = Cipher.getInstance(transformation, BouncyCastleProvider.PROVIDER_NAME);
        cipher.init(mode, new SecretKeySpec(key, "SM4"), spec);
        return cipher;
    }
}
