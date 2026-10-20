package com.voxio.common.utils.crypto;

import java.nio.file.Path;

/**
 * 配置凭据加解密命令行（阶段10.5 P0-1）
 *
 * 运维在部署机上生成 {@code enc:…} 配置值用，不写进任何接口，也不接受批量文件——凭据是逐条粘进
 * application.yml 的，脚本化反而容易把明文留在 shell 历史里，所以只保留最小三个子命令。
 *
 * 打包后运行（Spring Boot 的嵌套依赖需要 PropertiesLauncher）：
 *   java -Dloader.main=com.voxio.common.utils.crypto.CredentialCli ^
 *        -cp voxio-admin.jar org.springframework.boot.loader.launch.PropertiesLauncher encrypt "口令"
 */
public final class CredentialCli
{
    private CredentialCli()
    {
    }

    public static void main(String[] args)
    {
        Path keyDir = SmCredential.keyDir(option(args, "--key-dir", System.getProperty("user.home") + "/voxio/keys"));
        String action = args.length > 0 ? args[0] : "";
        switch (action)
        {
            case "keyinfo":
                System.out.println("key-dir=" + keyDir);
                System.out.println("master-sm4.json=" + (keyDir.resolve("master-sm4.json").toFile().exists() ? "存在" : "缺失"));
                System.out.println("keyVersion=" + SmCredential.keyVersion(keyDir));
                break;
            case "encrypt":
                require(args, 2, "用法：encrypt \"明文口令\"");
                System.out.println(SmCredential.encrypt(keyDir, args[1]));
                break;
            case "decrypt":
                require(args, 2, "用法：decrypt \"enc:sm4-gcm$…\"");
                System.out.println(SmCredential.decrypt(keyDir, args[1]));
                break;
            default:
                System.out.println("用法：keyinfo | encrypt \"明文\" | decrypt \"enc:…\" [--key-dir=目录]");
                System.exit(2);
        }
    }

    private static String option(String[] args, String name, String defaultValue)
    {
        String prefix = "--" + name + "=";
        for (String arg : args)
        {
            if (arg.startsWith(prefix))
            {
                return arg.substring(prefix.length());
            }
        }
        return defaultValue;
    }

    private static void require(String[] args, int length, String usage)
    {
        if (args.length < length)
        {
            System.out.println(usage);
            System.exit(2);
        }
    }
}
