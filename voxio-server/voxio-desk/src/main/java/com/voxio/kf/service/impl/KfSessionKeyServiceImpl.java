package com.voxio.kf.service.impl;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import com.voxio.common.utils.StringUtils;
import com.voxio.common.utils.crypto.Sm4Utils;
import com.voxio.common.utils.crypto.Sm3Hkdf;
import com.voxio.common.utils.crypto.Sm3Utils;
import com.voxio.common.utils.crypto.SmCodec;
import com.voxio.common.utils.crypto.SmKeyStore;
import com.voxio.kf.domain.KfSession;
import com.voxio.kf.mapper.KfSessionMapper;
import com.voxio.kf.service.IKfSecurityService;
import com.voxio.kf.service.IKfSessionKeyService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 会话密钥服务实现
 *
 * 库里只存「包装后的会话密钥」，包装密钥由 jar 外主密钥经 SM3-HKDF 派生，因此只拿到数据库
 * 无法解出消息；明文密钥仅在进程内缓存（{@link #cache}），会话结束或进程重启后重新协商。
 *
 * @author voxio
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KfSessionKeyServiceImpl implements IKfSessionKeyService
{
    /** 消息落库专用派生用途，与线路上的会话密钥分开 */
    public static final String PURPOSE_STORE = "msg-store";

    private static final String WRAP_PURPOSE = "session-key-wrap";

    private final KfSessionMapper sessionMapper;

    private final SmKeyStore keyStore;

    private final IKfSecurityService securityService;

    private final Map<Long, CachedKey> cache = new ConcurrentHashMap<>();

    /**
     * 会话落库子钥缓存（阶段11）。
     *
     * 原先每条消息写入与读取都要重算一次 SM3-HKDF，并且再回查一次密钥版本（一次公网数据库往返）；
     * 现在按会话缓存「派生钥 + 版本」，同一会话内只做一次。
     */
    private final Map<Long, DerivedKeys> derivedCache = new ConcurrentHashMap<>();

    /** 会话密钥包装子钥（主密钥派生，进程内算一次） */
    private volatile String wrapKeyHexCache;

    /** 未协商会话时的兜底落库子钥 */
    private volatile String defaultStoreKeyCache;

    @Override
    public String bindSessionKey(Long sessionId, String keyHex, int keyVersion)
    {
        if (sessionId == null || StringUtils.isEmpty(keyHex))
        {
            return null;
        }
        KfSession existing = sessionMapper.selectSessionKey(sessionId);
        if (existing == null)
        {
            return null;
        }
        if (StringUtils.isNotEmpty(existing.getSm4KeyCipher()))
        {
            // 已有密钥（对端先协商）：本次提议作废，返回既有密钥，两端最终收敛到同一把
            evict(sessionId);
            return getSessionKey(sessionId);
        }
        int version = keyVersion > 0 ? keyVersion : keyStore.getKeyVersion();
        int rows = sessionMapper.bindSessionKey(sessionId, wrap(keyHex), Sm3Utils.hashHex(SmCodec.utf8(keyHex)), version);
        if (rows == 0)
        {
            log.info("[会话密钥] 会话 {} 并发协商，采用先写入的那把", sessionId);
            evict(sessionId);
            return getSessionKey(sessionId);
        }
        cache.put(sessionId, new CachedKey(keyHex, version));
        return keyHex;
    }

    @Override
    public String getSessionKey(Long sessionId)
    {
        if (sessionId == null)
        {
            return null;
        }
        CachedKey cached = cache.get(sessionId);
        if (cached != null)
        {
            return cached.keyHex;
        }
        KfSession row = sessionMapper.selectSessionKey(sessionId);
        if (row == null || StringUtils.isEmpty(row.getSm4KeyCipher()))
        {
            return null;
        }
        String keyHex;
        try
        {
            keyHex = Sm4Utils.decryptGcm(wrapKeyHex(), row.getSm4KeyCipher());
        }
        catch (Exception e)
        {
            log.warn("[会话密钥] 会话 {} 的密钥材料解包失败：{}", sessionId, e.getMessage());
            return null;
        }
        // 解包成功也可能是主密钥换了之后的旧材料，摘要不符就当作未协商，宁可拒绝加密也不静默用错钥
        if (StringUtils.isNotEmpty(row.getSm4KeyDigest())
                && !Sm3Utils.hashHex(SmCodec.utf8(keyHex)).equals(row.getSm4KeyDigest()))
        {
            log.warn("[会话密钥] 会话 {} 的密钥摘要校验不通过，需要重新协商", sessionId);
            return null;
        }
        cache.put(sessionId, new CachedKey(keyHex, row.getSm4KeyVersion() == null ? 1 : row.getSm4KeyVersion()));
        return keyHex;
    }

    @Override
    public String deriveSessionKey(Long sessionId, String purpose)
    {
        String keyHex = getSessionKey(sessionId);
        if (keyHex == null)
        {
            return null;
        }
        return Sm3Hkdf.deriveHex(keyHex, saltHex(purpose), purpose, SmCodec.SM4_KEY_BYTES);
    }

    @Override
    public int getSessionKeyVersion(Long sessionId)
    {
        CachedKey cached = cache.get(sessionId);
        if (cached != null)
        {
            return cached.keyVersion;
        }
        KfSession row = sessionMapper.selectSessionKey(sessionId);
        return row == null || row.getSm4KeyVersion() == null ? KfSession.KEY_VERSION_NONE : row.getSm4KeyVersion();
    }

    @Override
    public void evict(Long sessionId)
    {
        if (sessionId != null)
        {
            cache.remove(sessionId);
            derivedCache.remove(sessionId);
        }
    }

    @Override
    public boolean isEncryptEnabled()
    {
        return securityService.isEnabled();
    }

    @Override
    public String encryptForStore(Long sessionId, String plain)
    {
        if (plain == null || !isEncryptEnabled())
        {
            return plain;
        }
        DerivedKeys keys = storeKeys(sessionId);
        return SmCodec.STORE_PREFIX + keys.keyVersion + "$" + Sm4Utils.encryptGcm(keys.storeKeyHex, plain);
    }

    @Override
    public String decryptStored(Long sessionId, String stored)
    {
        if (stored == null || !stored.startsWith(SmCodec.STORE_PREFIX))
        {
            // 阶段10.6 迁移前的存量明文，原样返回
            return stored;
        }
        int at = SmCodec.STORE_PREFIX.length();
        int end = stored.indexOf('$', at);
        if (end < 0)
        {
            return stored;
        }
        int version;
        try
        {
            version = Integer.parseInt(stored.substring(at, end));
        }
        catch (NumberFormatException e)
        {
            log.warn("[会话密钥] 会话 {} 的密文前缀版本号无法解析，按空内容返回", sessionId);
            return null;
        }
        // 版本号写 0 的行用的是主密钥派生兜底钥，其余按会话派生子钥（同 encryptForStore 的口径）
        String storeKey = version > 0 ? storeKeys(sessionId).storeKeyHex : defaultStoreKeyHex();
        if (storeKey == null)
        {
            log.warn("[会话密钥] 会话 {} 有密文消息但取不到密钥，按空内容返回", sessionId);
            return null;
        }
        try
        {
            return Sm4Utils.decryptGcm(storeKey, stored.substring(end + 1));
        }
        catch (Exception e)
        {
            log.warn("[会话密钥] 会话 {} 消息解密失败：{}", sessionId, e.getMessage());
            return null;
        }
    }

    /**
     * 取该会话的落库子钥与密钥版本（阶段11：按会话缓存）。
     *
     * 未协商过密钥的会话走主密钥派生的兜底钥并把版本写 0，读取侧按同样的规则取钥 —— 与原逻辑一致，
     * 区别只是 SM3-HKDF 与「回查密钥版本」的那一次数据库往返从「每条消息」降到「每个会话一次」。
     */
    private DerivedKeys storeKeys(Long sessionId)
    {
        Long sid = sessionId;
        if (sid == null)
        {
            return new DerivedKeys(defaultStoreKeyHex(), KfSession.KEY_VERSION_NONE);
        }
        DerivedKeys cached = derivedCache.get(sid);
        if (cached != null)
        {
            return cached;
        }
        String storeKey = deriveSessionKey(sid, PURPOSE_STORE);
        int version = storeKey == null ? KfSession.KEY_VERSION_NONE : getSessionKeyVersion(sid);
        if (storeKey == null)
        {
            storeKey = defaultStoreKeyHex();
        }
        DerivedKeys keys = new DerivedKeys(storeKey, version);
        // 兜底钥不绑会话，缓存它只会让「会话后来协商了密钥」这件事看不见，所以只缓存真实协商过的会话
        if (version > 0)
        {
            derivedCache.put(sid, keys);
        }
        return keys;
    }

    /** 未协商会话密钥时的落库兜底钥：由 jar 外主密钥派生，与线路上任何一把钥都不同串 */
    private String defaultStoreKeyHex()
    {
        String cached = defaultStoreKeyCache;
        if (cached != null)
        {
            return cached;
        }
        String derived = keyStore.deriveKeyHex(PURPOSE_STORE + "-default", SmCodec.SM4_KEY_BYTES);
        defaultStoreKeyCache = derived;
        return derived;
    }

    private String wrap(String keyHex)
    {
        return Sm4Utils.encryptGcm(wrapKeyHex(), keyHex);
    }

    private String wrapKeyHex()
    {
        String cached = wrapKeyHexCache;
        if (cached != null)
        {
            return cached;
        }
        String derived = keyStore.deriveKeyHex(WRAP_PURPOSE, SmCodec.SM4_KEY_BYTES);
        wrapKeyHexCache = derived;
        return derived;
    }

    /** 派生盐按用途固定，保证三端与重启后的 Java 都能算出同一把子密钥 */
    private static String saltHex(String purpose)
    {
        return SALT_CACHE.computeIfAbsent(purpose,
                p -> Sm3Utils.hashHex(SmCodec.utf8("sinbyte-kf-" + p)).substring(0, 24));
    }

    /** 用途 → 派生盐：盐只跟用途走，算一次就够（阶段11 顺手缓存，避免每条消息重算一次 SM3） */
    private static final Map<String, String> SALT_CACHE = new ConcurrentHashMap<>();

    /** 会话落库子钥与密钥版本 */
    private static final class DerivedKeys
    {
        private final String storeKeyHex;

        private final int keyVersion;

        private DerivedKeys(String storeKeyHex, int keyVersion)
        {
            this.storeKeyHex = storeKeyHex;
            this.keyVersion = keyVersion;
        }
    }

    private static final class CachedKey
    {
        private final String keyHex;

        private final int keyVersion;

        private CachedKey(String keyHex, int keyVersion)
        {
            this.keyHex = keyHex;
            this.keyVersion = keyVersion;
        }
    }
}
