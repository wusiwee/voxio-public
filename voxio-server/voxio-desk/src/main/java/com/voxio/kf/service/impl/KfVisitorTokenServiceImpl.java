package com.voxio.kf.service.impl;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.voxio.common.core.redis.RedisCache;
import com.voxio.common.utils.StringUtils;
import com.voxio.common.utils.uuid.IdUtils;
import com.voxio.kf.domain.KfVisitorLoginUser;
import com.voxio.kf.service.IKfVisitorTokenService;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import lombok.RequiredArgsConstructor;

/**
 * 访客端令牌 服务实现
 * 
 * @author voxio
 */
@Service
@RequiredArgsConstructor
public class KfVisitorTokenServiceImpl implements IKfVisitorTokenService
{
    private static final Logger log = LoggerFactory.getLogger(KfVisitorTokenServiceImpl.class);

    /** 访客登录态缓存前缀 */
    private static final String LOGIN_TOKEN_KEY = "kf_vt_login_ids:";

    /** 访客ID 与令牌标识关联键前缀 */
    private static final String LOGIN_REF_KEY = "kf_vt_token_ref:";

    private static final long MILLIS_MINUTE = 60 * 1000L;

    private final RedisCache redisCache;

    @Value("${token.secret}")
    private String secret;

    @Value("${kf.visitor.token.expireTime:1440}")
    private int expireTime;

    @Override
    public String createToken(KfVisitorLoginUser loginUser)
    {
        String tokenUuid = IdUtils.fastUUID();
        loginUser.setToken(tokenUuid);
        refreshToken(loginUser);
        redisCache.setCacheObject(LOGIN_REF_KEY + loginUser.getVisitorId(), tokenUuid, expireTime, TimeUnit.MINUTES);

        Map<String, Object> claims = new HashMap<>();
        claims.put(CLAIM_KEY, tokenUuid);
        claims.put(CLAIM_PRINCIPAL, PRINCIPAL_VISITOR);
        return Jwts.builder().setClaims(claims).signWith(SignatureAlgorithm.HS512, secret).compact();
    }

    @Override
    public KfVisitorLoginUser getLoginUser(String token)
    {
        if (StringUtils.isEmpty(token))
        {
            return null;
        }
        try
        {
            Claims claims = Jwts.parser().setSigningKey(secret).parseClaimsJws(token).getBody();
            if (!PRINCIPAL_VISITOR.equals(claims.get(CLAIM_PRINCIPAL, String.class)))
            {
                return null;
            }
            String tokenUuid = (String) claims.get(CLAIM_KEY);
            KfVisitorLoginUser loginUser = redisCache.getCacheObject(LOGIN_TOKEN_KEY + tokenUuid);
            if (StringUtils.isNotNull(loginUser))
            {
                loginUser.setToken(tokenUuid);
            }
            return loginUser;
        }
        catch (Exception e)
        {
            log.debug("访客令牌解析失败 '{}'", e.getMessage());
            return null;
        }
    }

    @Override
    public void refreshToken(KfVisitorLoginUser loginUser)
    {
        loginUser.setLoginTime(System.currentTimeMillis());
        loginUser.setExpireTime(loginUser.getLoginTime() + expireTime * MILLIS_MINUTE);
        redisCache.setCacheObject(LOGIN_TOKEN_KEY + loginUser.getToken(), loginUser, expireTime, TimeUnit.MINUTES);
    }

    @Override
    public void delToken(Long visitorId)
    {
        String refKey = LOGIN_REF_KEY + visitorId;
        String tokenUuid = redisCache.getCacheObject(refKey);
        if (StringUtils.isNotEmpty(tokenUuid))
        {
            redisCache.deleteObject(LOGIN_TOKEN_KEY + tokenUuid);
            redisCache.deleteObject(refKey);
        }
    }

    @Override
    public void rebindSession(Long visitorId, Long sessionId)
    {
        String tokenUuid = redisCache.getCacheObject(LOGIN_REF_KEY + visitorId);
        if (StringUtils.isEmpty(tokenUuid))
        {
            return;
        }
        KfVisitorLoginUser loginUser = redisCache.getCacheObject(LOGIN_TOKEN_KEY + tokenUuid);
        if (StringUtils.isNotNull(loginUser))
        {
            loginUser.setSessionId(sessionId);
            refreshToken(loginUser);
        }
    }
}
