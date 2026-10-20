package com.voxio.common.utils.crypto;

import java.math.BigInteger;
import java.security.SecureRandom;

import org.bouncycastle.asn1.gm.GMNamedCurves;
import org.bouncycastle.asn1.x9.X9ECParameters;
import org.bouncycastle.crypto.engines.SM2Engine;
import org.bouncycastle.crypto.params.ECDomainParameters;
import org.bouncycastle.crypto.params.ECPrivateKeyParameters;
import org.bouncycastle.crypto.params.ECPublicKeyParameters;
import org.bouncycastle.crypto.params.ParametersWithID;
import org.bouncycastle.crypto.params.ParametersWithRandom;
import org.bouncycastle.crypto.signers.SM2Signer;
import org.bouncycastle.math.ec.ECPoint;

/**
 * SM2 非对称：只用于保护会话密钥（密钥交换）与签名验签，不直接加密业务数据
 *
 * 密文为 C1C3C2（与 sm-crypto 的 cipherMode=1 一致）、hex 且 C1 带 04 未压缩前缀；
 * 签名为 DER 编码 hex，userId 固定 1234567812345678。
 */
public final class Sm2Utils
{
    private static final X9ECParameters X9 = GMNamedCurves.getByName("sm2p256v1");

    private static final ECDomainParameters DOMAIN =
            new ECDomainParameters(X9.getCurve(), X9.getG(), X9.getN(), X9.getH());

    private static final SecureRandom RANDOM = new SecureRandom();

    private Sm2Utils()
    {
    }

    /** 生成密钥对：privateKeyHex 64 位、publicKeyHex 130 位（04||X||Y） */
    public static Sm2KeyPair generateKeyPair()
    {
        BigInteger d;
        do
        {
            d = new BigInteger(256, RANDOM);
        }
        while (d.compareTo(X9.getN().subtract(BigInteger.ONE)) >= 0 || d.signum() == 0);
        ECPoint q = X9.getG().multiply(d).normalize();
        return new Sm2KeyPair(SmCodec.hex(leftPad32(d)), SmCodec.hex(q.getEncoded(false)));
    }

    public static String publicKeyFromPrivate(String privateKeyHex)
    {
        ECPoint q = X9.getG().multiply(new BigInteger(1, SmCodec.unhex(privateKeyHex))).normalize();
        return SmCodec.hex(q.getEncoded(false));
    }

    /** 用服务端公钥加密任意字节，返回 hex（C1C3C2） */
    public static String encryptHex(String publicKeyHex, byte[] data)
    {
        try
        {
            SM2Engine engine = new SM2Engine(SM2Engine.Mode.C1C3C2);
            engine.init(true, new ParametersWithRandom(publicKey(publicKeyHex), RANDOM));
            return SmCodec.hex(engine.processBlock(data, 0, data.length));
        }
        catch (Exception e)
        {
            throw new IllegalStateException("SM2 加密失败：" + e.getMessage(), e);
        }
    }

    public static byte[] decryptHex(String privateKeyHex, String cipherHex)
    {
        try
        {
            SM2Engine engine = new SM2Engine(SM2Engine.Mode.C1C3C2);
            engine.init(false, privateKey(privateKeyHex));
            return engine.processBlock(SmCodec.unhex(cipherHex), 0, cipherHex.length() / 2);
        }
        catch (Exception e)
        {
            throw new IllegalStateException("SM2 解密失败：" + e.getMessage(), e);
        }
    }

    /** SM3withSM2 签名，返回 DER 签名 hex */
    public static String signHex(String privateKeyHex, byte[] message)
    {
        try
        {
            SM2Signer signer = new SM2Signer();
            signer.init(true, new ParametersWithID(privateKey(privateKeyHex), SmCodec.SM2_USER_ID));
            signer.update(message, 0, message.length);
            return SmCodec.hex(signer.generateSignature());
        }
        catch (Exception e)
        {
            throw new IllegalStateException("SM2 签名失败：" + e.getMessage(), e);
        }
    }

    public static boolean verifyHex(String publicKeyHex, byte[] message, String signatureHex)
    {
        try
        {
            SM2Signer signer = new SM2Signer();
            signer.init(false, new ParametersWithID(publicKey(publicKeyHex), SmCodec.SM2_USER_ID));
            signer.update(message, 0, message.length);
            return signer.verifySignature(SmCodec.unhex(signatureHex));
        }
        catch (Exception e)
        {
            return false;
        }
    }

    private static ECPublicKeyParameters publicKey(String hex)
    {
        ECPoint q = X9.getCurve().decodePoint(SmCodec.unhex(hex));
        return new ECPublicKeyParameters(q, DOMAIN);
    }

    private static ECPrivateKeyParameters privateKey(String hex)
    {
        return new ECPrivateKeyParameters(new BigInteger(1, SmCodec.unhex(hex)), DOMAIN);
    }

    private static byte[] leftPad32(BigInteger value)
    {
        byte[] raw = value.toByteArray();
        if (raw.length == 32)
        {
            return raw;
        }
        byte[] out = new byte[32];
        System.arraycopy(raw, Math.max(0, raw.length - 32), out, Math.max(0, 32 - raw.length), Math.min(32, raw.length));
        return out;
    }

    /** SM2 密钥对（hex 材料，便于三端与配置文件传递） */
    public record Sm2KeyPair(String privateKeyHex, String publicKeyHex)
    {
    }
}
