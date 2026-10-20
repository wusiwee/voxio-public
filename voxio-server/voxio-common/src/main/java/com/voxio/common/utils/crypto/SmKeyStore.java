package com.voxio.common.utils.crypto;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 服务端密钥材料装载：SM2 密钥对（保护会话密钥）+ SM4 主密钥（派生各类子密钥）
 *
 * 密钥文件默认落在仓库与交付包之外的目录（${user.home}/voxio/keys），首次启动自动生成；
 * 按总控「不自建 KMS」的约束，密钥靠文件与环境变量管理，轮换 = 换文件 + 递增 keyVersion 后重启。
 *
 * 再往后还有一层兜底：key-dir 所在文件系统不可写时（只读挂载、容器里没有可写 HOME 等），
 * 回退读随仓库发布的 classpath:keys/ 开发密钥，保证 clone 下来不带任何参数也能起来。
 */
@Component
public class SmKeyStore
{
    private static final Logger log = LoggerFactory.getLogger(SmKeyStore.class);

    private static final ObjectMapper JSON = new ObjectMapper();

    private final Path dir;

    private Sm2Utils.Sm2KeyPair serverKeyPair;

    private String masterKeyHex;

    private int keyVersion;

    public SmKeyStore(@Value("${voxio.crypto.key-dir:${user.home}/voxio/keys}") String keyDir)
    {
        this.dir = Paths.get(expandHome(keyDir)).toAbsolutePath().normalize();
        loadOrGenerate();
    }

    /** 服务端 SM2 公钥（hex，下发给三端用于加密会话密钥） */
    public String getServerPublicKeyHex()
    {
        return serverKeyPair.publicKeyHex();
    }

    public String getServerPrivateKeyHex()
    {
        return serverKeyPair.privateKeyHex();
    }

    /** SM4 主密钥（hex），只用于 HKDF 派生，不直接加密数据 */
    public String getMasterKeyHex()
    {
        return masterKeyHex;
    }

    public int getKeyVersion()
    {
        return keyVersion;
    }

    public Path getKeyDir()
    {
        return dir;
    }

    /** 按用途派生子密钥：字段加密、附件加密、接口 HMAC 各自一把，互不复用 */
    public String deriveKeyHex(String purpose, int lengthBytes)
    {
        return Sm3Hkdf.deriveHex(masterKeyHex, saltHex(purpose), purpose, lengthBytes);
    }

    /** 派生盐固定 26 位 hex：用途名不足就补零，超长截断（补零而不是截断，短用途名如 field/media 才不会越界） */
    public static String saltHex(String purpose)
    {
        String hex = SmCodec.hex(purpose.getBytes(StandardCharsets.UTF_8));
        if (hex.length() >= 26)
        {
            return hex.substring(0, 26);
        }
        return hex + "0".repeat(26 - hex.length());
    }

    private void loadOrGenerate()
    {
        try
        {
            Files.createDirectories(dir);
            Path sm2 = dir.resolve("sm2-server.json");
            Path master = dir.resolve("master-sm4.json");
            if (Files.exists(sm2))
            {
                Map<?, ?> node = JSON.readValue(sm2.toFile(), Map.class);
                serverKeyPair = new Sm2Utils.Sm2KeyPair(String.valueOf(node.get("privateKeyHex")),
                        String.valueOf(node.get("publicKeyHex")));
                keyVersion = intOf(node.get("keyVersion"), 1);
            }
            else
            {
                serverKeyPair = Sm2Utils.generateKeyPair();
                keyVersion = 1;
                write(sm2, serverKeyPair.privateKeyHex(), serverKeyPair.publicKeyHex());
            }
            if (Files.exists(master))
            {
                Map<?, ?> node = JSON.readValue(master.toFile(), Map.class);
                masterKeyHex = String.valueOf(node.get("sm4KeyHex"));
                keyVersion = Math.max(keyVersion, intOf(node.get("keyVersion"), 1));
            }
            else
            {
                masterKeyHex = Sm4Utils.randomKeyHex();
                write(master, masterKeyHex, null);
            }
        }
        catch (Exception e)
        {
            // key-dir 用不了（只读文件系统、无可写 HOME 等）时回退到仓库自带的开发密钥，
            // 让「clone 下来直接启动」不会因为国密目录失败；能写盘的正常走上面的生成分支。
            if (loadFromClasspath())
            {
                return;
            }
            throw new IllegalStateException("国密密钥装载失败（目录 " + dir + "）：" + e.getMessage(), e);
        }
    }

    /**
     * 兜底：读随仓库发布的 classpath:keys/ 开发密钥
     *
     * @return 两份都在 classpath 上读到并装载成功才为 true；任一缺失就返回 false，由调用方按原样报错
     */
    private boolean loadFromClasspath()
    {
        try
        {
            Map<?, ?> sm2 = readResource("keys/sm2-server.json");
            Map<?, ?> master = readResource("keys/master-sm4.json");
            if (sm2 == null || master == null)
            {
                return false;
            }
            serverKeyPair = new Sm2Utils.Sm2KeyPair(String.valueOf(sm2.get("privateKeyHex")),
                    String.valueOf(sm2.get("publicKeyHex")));
            masterKeyHex = String.valueOf(master.get("sm4KeyHex"));
            keyVersion = Math.max(intOf(sm2.get("keyVersion"), 1), intOf(master.get("keyVersion"), 1));
            log.warn("国密密钥目录不可写（{}），已回退到随仓库发布的 classpath:keys/ 开发密钥。"
                    + "加密默认关闭时这不影响使用；要开启加密，请先在 key-dir 放自己的密钥。", dir);
            return true;
        }
        catch (Exception fallbackFailure)
        {
            return false;
        }
    }

    private Map<?, ?> readResource(String name) throws Exception
    {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(name))
        {
            return in == null ? null : JSON.readValue(in, Map.class);
        }
    }

    private void write(Path file, String first, String second) throws Exception
    {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("keyVersion", keyVersion);
        if (second == null)
        {
            body.put("sm4KeyHex", first);
        }
        else
        {
            body.put("privateKeyHex", first);
            body.put("publicKeyHex", second);
        }
        JSON.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), body);
        try
        {
            file.toFile().setReadable(false, false);
            file.toFile().setReadable(true, true);
        }
        catch (Exception ignore)
        {
            // Windows 上无 POSIX 权限位，失败不影响使用
        }
    }

    private static int intOf(Object value, int defaultValue)
    {
        return value instanceof Number n ? n.intValue() : defaultValue;
    }

    /** 支持 ${user.home} 之外的家目录写法，Linux/Windows 都能落到运维可写的位置 */
    public static String expandHome(String path)
    {
        if (path.startsWith("~/"))
        {
            return System.getProperty("user.home") + path.substring(1);
        }
        return path;
    }
}
