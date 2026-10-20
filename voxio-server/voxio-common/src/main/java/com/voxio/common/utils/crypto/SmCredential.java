package com.voxio.common.utils.crypto;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 配置凭据密文（阶段10.5 P0-1）
 *
 * application.yml 里的数据库口令、Redis 口令、Druid 控制台口令、JWT 签名密钥不再写明文，
 * 改写成 {@code enc:sm4-gcm$版本$base64(iv‖密文‖标签)}，由 {@code CredentialEnvironmentPostProcessor}
 * 在数据源与 Redis 连接工厂创建之前解回内存属性源，业务代码读到的仍是明文，无需感知。
 *
 * 密钥仍是仓库与交付包之外的 SM4 主密钥（{@code voxio.crypto.key-dir}），派生用途
 * {@value #PURPOSE} 与字段/附件/链路/包装密钥互不复用：拿到 jar 与配置文件不等于拿到凭据，
 * 反过来凭据密文也解不出聊天数据用的子密钥。
 */
public final class SmCredential
{
    /** 密文值前缀，enc: 一眼区分「这是配置凭据密文」与库内数据密文 */
    public static final String PREFIX = "enc:" + SmCodec.STORE_PREFIX;

    /** 派生用途名，与 field / media / msg-store 等并列，不可复用 */
    public static final String PURPOSE = "config-credential";

    private static final ObjectMapper JSON = new ObjectMapper();

    private SmCredential()
    {
    }

    public static boolean isEncrypted(String value)
    {
        return value != null && value.startsWith(PREFIX);
    }

    /**
     * 解密入口用的值归一：非 enc: 前缀原样返回，便于平滑迁移（未改写的配置照常工作）
     */
    public static String resolve(Path keyDir, String value)
    {
        return isEncrypted(value) ? decrypt(keyDir, value) : value;
    }

    public static String encrypt(Path keyDir, String plaintext)
    {
        Master master = readMaster(keyDir);
        String packed = Sm4Utils.encryptGcm(deriveKeyHex(master), plaintext);
        return PREFIX + master.keyVersion + "$" + packed;
    }

    public static String decrypt(Path keyDir, String cipherText)
    {
        if (!isEncrypted(cipherText))
        {
            throw new IllegalArgumentException("不是配置凭据密文（缺少 " + PREFIX + " 前缀）");
        }
        int from = PREFIX.length();
        int end = cipherText.indexOf('$', from);
        if (end < 0)
        {
            throw new IllegalArgumentException("配置凭据密文形态不合法：" + cipherText.substring(0, Math.min(cipherText.length(), 24)));
        }
        Master master = readMaster(keyDir);
        int version = Integer.parseInt(cipherText.substring(from, end));
        if (version != master.keyVersion)
        {
            throw new IllegalStateException("配置凭据的密钥版本 " + version + " 与当前主密钥版本 " + master.keyVersion + " 不一致");
        }
        return Sm4Utils.decryptGcm(deriveKeyHex(master), cipherText.substring(end + 1));
    }

    /** 当前主密钥版本，供 CLI 与门禁核对 */
    public static int keyVersion(Path keyDir)
    {
        return readMaster(keyDir).keyVersion;
    }

    public static Path keyDir(String configured)
    {
        return Paths.get(SmKeyStore.expandHome(configured)).toAbsolutePath().normalize();
    }

    private static String deriveKeyHex(Master master)
    {
        return Sm3Hkdf.deriveHex(master.keyHex, SmKeyStore.saltHex(PURPOSE), PURPOSE, SmCodec.SM4_KEY_BYTES);
    }

    /**
     * 只读不生成：主密钥文件缺失时必须失败。
     * 若像 SmKeyStore 那样在启动期顺手生成新密钥，配置里的旧密文会解不开并抛更晚、更难定位的异常，
     * 甚至出现「凭据解错但连上了别的库」这类更糟的情况。
     */
    private static Master readMaster(Path dir)
    {
        Path master = dir.resolve("master-sm4.json");
        if (!Files.exists(master))
        {
            throw new IllegalStateException("配置凭据密文需要主密钥文件，但 " + master + " 不存在。"
                    + "请先备份并恢复密钥目录（与库同批备份），或改用明文配置后由运维重新生成凭据");
        }
        try
        {
            Map<?, ?> node = JSON.readValue(master.toFile(), Map.class);
            String keyHex = String.valueOf(node.get("sm4KeyHex"));
            Object version = node.get("keyVersion");
            return new Master(keyHex, version instanceof Number n ? n.intValue() : 1);
        }
        catch (Exception e)
        {
            throw new IllegalStateException("配置凭据主密钥读取失败（" + master + "）：" + e.getMessage(), e);
        }
    }

    private record Master(String keyHex, int keyVersion)
    {
    }
}
