package com.voxio.kf.service;

import com.voxio.kf.domain.KfVisitorLoginUser;

/**
 * 访客端令牌 服务层
 * 
 * 与客服令牌、管理员令牌完全隔离：独立 Redis 前缀与独立 claim。
 * 
 * @author voxio
 */
public interface IKfVisitorTokenService
{
    /** 令牌 claim 键 */
    public static final String CLAIM_KEY = "kf_visitor_key";

    /** 令牌主体类型 claim 键 */
    public static final String CLAIM_PRINCIPAL = "principal";

    /** 令牌主体类型：访客 */
    public static final String PRINCIPAL_VISITOR = "VISITOR";

    /**
     * 签发访客令牌
     * 
     * @param loginUser 访客登录信息
     * @return 令牌
     */
    public String createToken(KfVisitorLoginUser loginUser);

    /**
     * 校验令牌并返回访客身份，非访客令牌返回空
     * 
     * @param token 令牌
     * @return 访客登录信息
     */
    public KfVisitorLoginUser getLoginUser(String token);

    /**
     * 延长令牌有效期
     * 
     * @param loginUser 访客登录信息
     */
    public void refreshToken(KfVisitorLoginUser loginUser);

    /**
     * 使访客当前令牌失效
     * 
     * @param visitorId 访客ID
     */
    public void delToken(Long visitorId);

    /**
     * 重新绑定令牌所属会话
     * 
     * @param visitorId 访客ID
     * @param sessionId 会话ID
     */
    public void rebindSession(Long visitorId, Long sessionId);
}
