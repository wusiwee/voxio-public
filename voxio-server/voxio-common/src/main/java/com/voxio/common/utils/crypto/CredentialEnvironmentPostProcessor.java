package com.voxio.common.utils.crypto;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;

/**
 * 配置凭据解密（阶段10.5 P0-1）
 *
 * 在 Environment 装载完 application.yml 之后、任何 Bean（含 Druid 数据源与 Redis 连接工厂）创建之前，
 * 把所有 {@code enc:sm4-gcm$…} 形态的属性就地解成明文，放进最高优先级属性源。
 * 因此业务侧（{@code @Value}、Druid starter、TokenService）读到的仍是明文，不需要改一行调用代码。
 *
 * 只处理「值本身带 enc: 前缀」的属性，不维护密钥名清单：新增一个需要加密的配置项不必改这里。
 */
public class CredentialEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered
{
    private static final String SOURCE_NAME = "voxioDecryptedCredentials";

    private static final String KEY_DIR_PROPERTY = "voxio.crypto.key-dir";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application)
    {
        Map<String, Object> decrypted = new LinkedHashMap<>();
        for (PropertySource<?> source : environment.getPropertySources())
        {
            if (!(source instanceof EnumerablePropertySource<?> enumerable))
            {
                continue;
            }
            for (String name : enumerable.getPropertyNames())
            {
                if (decrypted.containsKey(name))
                {
                    continue;
                }
                Object raw;
                try
                {
                    raw = enumerable.getProperty(name);
                }
                catch (RuntimeException e)
                {
                    continue;
                }
                if (raw instanceof String text && SmCredential.isEncrypted(text))
                {
                    decrypted.put(name, SmCredential.decrypt(keyDir(environment), text));
                }
            }
        }
        if (!decrypted.isEmpty())
        {
            environment.getPropertySources().addFirst(new MapPropertySource(SOURCE_NAME, decrypted));
        }
    }

    private static java.nio.file.Path keyDir(ConfigurableEnvironment environment)
    {
        String configured = environment.getProperty(KEY_DIR_PROPERTY, "${user.home}/voxio/keys");
        return SmCredential.keyDir(environment.resolvePlaceholders(configured));
    }

    @Override
    public int getOrder()
    {
        // 必须晚于 ConfigDataEnvironmentPostProcessor，否则读不到 application.yml 里的 enc: 值与 key-dir
        return Ordered.LOWEST_PRECEDENCE;
    }
}
