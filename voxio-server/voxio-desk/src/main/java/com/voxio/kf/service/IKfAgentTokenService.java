package com.voxio.kf.service;

import com.voxio.kf.domain.KfAgentLoginUser;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 客服端令牌 服务层
 * 
 * 与若依 sys_user 令牌完全隔离：独立 Redis 前缀 + 独立 claim，
 * 管理端 token 无法访问客服端接口，反之亦然。
 * 
 * @author voxio
 */
public interface IKfAgentTokenService
{
    /** 令牌请求头 */
    public static final String HEADER = "Authorization";

    /** 令牌 claim 键 */
    public static final String CLAIM_KEY = "kf_agent_key";

    /** 令牌主体类型 claim 键 */
    public static final String CLAIM_PRINCIPAL = "principal";

    /** 令牌主体类型：客服 */
    public static final String PRINCIPAL_AGENT = "AGENT";

    /**
     * 建立客服令牌并写入 Redis
     * 
     * @param loginUser 客服登录信息
     * @return 令牌
     */
    public String createToken(KfAgentLoginUser loginUser);

    /**
     * 从请求中解析客服登录信息，非客服令牌一律返回空
     * 
     * @param request 请求
     * @return 客服登录信息
     */
    public KfAgentLoginUser getLoginUser(HttpServletRequest request);

    /**
     * 从令牌字符串解析客服登录信息（网关使用，不依赖 Servlet 上下文）
     * 
     * @param token 令牌
     * @return 客服登录信息
     */
    public KfAgentLoginUser getLoginUserByToken(String token);

    /**
     * 延长客服登录信息缓存
     * 
     * @param loginUser 客服登录信息
     */
    public void refreshToken(KfAgentLoginUser loginUser);

    /**
     * 按令牌唯一标识清除登录态
     * 
     * @param tokenUuid 令牌唯一标识
     */
    public void delLoginUser(String tokenUuid);

    /**
     * 清除某客服的登录态（强制下线、停用、改密时调用）
     * 
     * @param agentId 客服ID
     */
    public void delLoginUserByAgentId(Long agentId);
}
