package com.voxio.kf.service.impl;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.voxio.common.constant.Constants;
import com.voxio.common.core.redis.RedisCache;
import com.voxio.common.utils.StringUtils;
import com.voxio.common.utils.uuid.IdUtils;
import com.voxio.kf.domain.KfAgentLoginUser;
import com.voxio.kf.service.IKfAgentTokenService;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

/**
 * 客服端令牌 服务实现
 * 
 * @author voxio
 */
@Service
@RequiredArgsConstructor
public class KfAgentTokenServiceImpl implements IKfAgentTokenService
{
    private static final Logger log = LoggerFactory.getLogger(KfAgentTokenServiceImpl.class);

    /** 客服登录态缓存前缀 */
    private static final String LOGIN_TOKEN_KEY = "kf_ag_login_ids:";

    /** 客服ID 与令牌标识的关联键前缀（保证同一客服只有一个有效登录） */
    private static final String LOGIN_REF_KEY = "kf_ag_token_ref:";

    private static final long MILLIS_MINUTE = 60 * 1000L;

    private final RedisCache redisCache;

    @Value("${token.header}")
    private String header;

    @Value("${token.secret}")
    private String secret;

    @Value("${kf.agent.token.expireTime:720}")
    private int expireTime;

    @Override
    public String createToken(KfAgentLoginUser loginUser)
    {
        String tokenUuid = IdUtils.fastUUID();
        loginUser.setToken(tokenUuid);
        refreshToken(loginUser);
        redisCache.setCacheObject(LOGIN_REF_KEY + loginUser.getAgentId(), tokenUuid, expireTime, TimeUnit.MINUTES);

        Map<String, Object> claims = new HashMap<>();
        claims.put(CLAIM_KEY, tokenUuid);
        claims.put(CLAIM_PRINCIPAL, PRINCIPAL_AGENT);
        return Jwts.builder().setClaims(claims).signWith(SignatureAlgorithm.HS512, secret).compact();
    }

    @Override
    public KfAgentLoginUser getLoginUser(HttpServletRequest request)
    {
        return getLoginUserByToken(getToken(request));
    }

    @Override
    public KfAgentLoginUser getLoginUserByToken(String token)
    {
        if (StringUtils.isEmpty(token))
        {
            return null;
        }
        try
        {
            Claims claims = Jwts.parser().setSigningKey(secret).parseClaimsJws(token).getBody();
            // 只接受客服主体令牌，管理端与访客令牌在此被拒绝
            if (!PRINCIPAL_AGENT.equals(claims.get(CLAIM_PRINCIPAL, String.class)))
            {
                return null;
            }
            String tokenUuid = (String) claims.get(CLAIM_KEY);
            if (StringUtils.isEmpty(tokenUuid))
            {
                return null;
            }
            KfAgentLoginUser loginUser = redisCache.getCacheObject(LOGIN_TOKEN_KEY + tokenUuid);
            if (StringUtils.isNotNull(loginUser))
            {
                loginUser.setToken(tokenUuid);
            }
            return loginUser;
        }
        catch (Exception e)
        {
            log.debug("客服令牌解析失败 '{}'", e.getMessage());
            return null;
        }
    }

    @Override
    public void refreshToken(KfAgentLoginUser loginUser)
    {
        loginUser.setLoginTime(System.currentTimeMillis());
        loginUser.setExpireTime(loginUser.getLoginTime() + expireTime * MILLIS_MINUTE);
        redisCache.setCacheObject(LOGIN_TOKEN_KEY + loginUser.getToken(), loginUser, expireTime, TimeUnit.MINUTES);
    }

    @Override
    public void delLoginUser(String tokenUuid)
    {
        if (StringUtils.isNotEmpty(tokenUuid))
        {
            redisCache.deleteObject(LOGIN_TOKEN_KEY + tokenUuid);
        }
    }

    @Override
    public void delLoginUserByAgentId(Long agentId)
    {
        String refKey = LOGIN_REF_KEY + agentId;
        String tokenUuid = redisCache.getCacheObject(refKey);
        if (StringUtils.isNotEmpty(tokenUuid))
        {
            delLoginUser(tokenUuid);
            redisCache.deleteObject(refKey);
        }
    }

    private String getToken(HttpServletRequest request)
    {
        String token = request.getHeader(header);
        if (StringUtils.isNotEmpty(token) && token.startsWith(Constants.TOKEN_PREFIX))
        {
            token = token.replace(Constants.TOKEN_PREFIX, "");
        }
        return token;
    }
}
