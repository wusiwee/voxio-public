package com.voxio.kf.service.impl;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.voxio.common.core.redis.RedisCache;
import com.voxio.common.exception.ServiceException;
import com.voxio.common.utils.StringUtils;
import com.voxio.common.utils.crypto.SmCodec;
import com.voxio.common.utils.crypto.SmKeyStore;
import com.voxio.common.utils.crypto.SmTestVectors;
import com.voxio.kf.domain.KfSecurityConfig;
import com.voxio.kf.mapper.KfSecurityConfigMapper;
import com.voxio.kf.service.IKfSecurityService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 国密安全策略服务实现
 *
 * 开关来源是 kf_security_config（表），但「是否允许关闭」由 jar 内 application.yml 的
 * voxio.crypto.enforce 决定：交付环境保持 enforce=true，配置文件里写 false 也无效，
 * 开发机要临时关掉只能用启动参数 --voxio.crypto.enforce=false，避免误把生产加密关掉。
 *
 * @author voxio
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KfSecurityServiceImpl extends ServiceImpl<KfSecurityConfigMapper, KfSecurityConfig>
        implements IKfSecurityService
{
    private static final String CACHE_KEY = "kf_security_config:";

    /**
     * 进程内短期记忆（阶段11）：网关每帧封包都要取一次算法标识，而 Redis 在公网，
     * 一次往返 15~20ms 直接加在消息投递延迟上。这类开关几乎不变，2 秒的滞后换来每帧省一次往返。
     * setConfig 会立即清掉本地记忆，管理后台改完仍算「马上生效」。
     */
    private static final long LOCAL_MEMO_MILLIS = 2000L;

    private final Map<String, Memo> localMemo = new ConcurrentHashMap<>();

    private final RedisCache redisCache;

    private final SmKeyStore keyStore;

    @Value("${voxio.crypto.enabled:true}")
    private boolean configEnabled;

    @Value("${voxio.crypto.enforce:true}")
    private boolean enforce;

    /** 本地记忆条目：值 + 过期时刻 */
    private record Memo(String value, long expireAt)
    {
    }

    @Override
    public boolean isEnabled()
    {
        if (!configEnabled)
        {
            if (enforce)
            {
                throw new ServiceException("等保合规要求加密常开：application.yml 的 voxio.crypto.enabled=false "
                        + "且 voxio.crypto.enforce=true，请改回 true 或用启动参数临时降级");
            }
            return false;
        }
        if (!enforce)
        {
            return !"false".equalsIgnoreCase(getConfig("encrypt.enabled", "true"));
        }
        return true;
    }

    @Override
    public String getAlgo()
    {
        return getConfig("encrypt.algo", "SM4-GCM");
    }

    @Override
    public int getKeyVersion()
    {
        return keyStore.getKeyVersion();
    }

    @Override
    public String getServerPublicKeyHex()
    {
        return keyStore.getServerPublicKeyHex();
    }

    @Override
    public String deriveKeyHex(String purpose)
    {
        return keyStore.deriveKeyHex(purpose, SmCodec.SM4_KEY_BYTES);
    }

    @Override
    public String getConfig(String key, String defaultValue)
    {
        Memo memo = localMemo.get(key);
        long now = System.currentTimeMillis();
        if (memo != null && memo.expireAt() > now && StringUtils.isNotEmpty(memo.value()))
        {
            return memo.value();
        }
        String cached = redisCache.getCacheObject(CACHE_KEY + key);
        if (StringUtils.isNotEmpty(cached))
        {
            localMemo.put(key, new Memo(cached, now + LOCAL_MEMO_MILLIS));
            return cached;
        }
        KfSecurityConfig row = getOne(new LambdaQueryWrapper<KfSecurityConfig>()
                .eq(KfSecurityConfig::getConfigKey, key), false);
        String value = row == null || row.getConfigValue() == null ? defaultValue : row.getConfigValue();
        redisCache.setCacheObject(CACHE_KEY + key, value);
        localMemo.put(key, new Memo(value, now + LOCAL_MEMO_MILLIS));
        return value;
    }

    @Override
    public void setConfig(String key, String value)
    {
        KfSecurityConfig row = getOne(new LambdaQueryWrapper<KfSecurityConfig>()
                .eq(KfSecurityConfig::getConfigKey, key), false);
        if (row == null)
        {
            row = new KfSecurityConfig();
            row.setConfigKey(key);
            row.setConfigValue(value);
            save(row);
        }
        else
        {
            row.setConfigValue(value);
            updateById(row);
        }
        redisCache.deleteObject(CACHE_KEY + key);
        // 本地记忆同时清掉：管理后台改完这一项就要看到新值
        localMemo.remove(key);
    }

    @Override
    public Map<String, Object> vectors()
    {
        Map<String, Object> out = new LinkedHashMap<>(SmTestVectors.build());
        out.put("keyVersion", getKeyVersion());
        out.put("algo", getAlgo());
        return out;
    }

    @Override
    public boolean verifySignature(String signatureHex)
    {
        return StringUtils.isNotEmpty(signatureHex) && SmTestVectors.verifySignature(signatureHex);
    }

    @Override
    public boolean verifyVectorCipher(String cipherHex)
    {
        return SmTestVectors.matchesVectorCipher(cipherHex);
    }

    @Override
    public Map<String, Object> publicStatus()
    {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("encryptEnabled", isEnabled());
        out.put("algo", getAlgo());
        out.put("sm2PublicKey", getServerPublicKeyHex());
        out.put("keyVersion", getKeyVersion());
        out.put("sm2CipherMode", "C1C3C2");
        out.put("signatureAlgo", "SM3withSM2");
        return out;
    }
}
