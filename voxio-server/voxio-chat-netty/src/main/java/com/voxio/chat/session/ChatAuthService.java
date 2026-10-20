package com.voxio.chat.session;

import com.voxio.chat.protocol.ChatPrincipal;
import com.voxio.kf.domain.KfAgentLoginUser;
import com.voxio.kf.domain.KfVisitorLoginUser;
import com.voxio.kf.service.IKfAgentTokenService;
import com.voxio.kf.service.IKfVisitorTokenService;

import lombok.RequiredArgsConstructor;

/**
 * 网关连接鉴权
 * 
 * 客服令牌与访客令牌分别校验，管理员令牌（sys_user）在此被拒绝。
 * 
 * @author voxio
 */
@RequiredArgsConstructor
public class ChatAuthService
{
    private final IKfAgentTokenService agentTokenService;

    private final IKfVisitorTokenService visitorTokenService;

    /**
     * 校验令牌并解析主体
     * 
     * @param token 令牌
     * @return 主体，校验失败返回空
     */
    public ChatPrincipal resolve(String token)
    {
        KfAgentLoginUser agent = agentTokenService.getLoginUserByToken(token);
        if (agent != null)
        {
            return new ChatPrincipal(ChatPrincipal.TYPE_AGENT, agent.getAgentId(), agent.getNickName(), null, token);
        }
        KfVisitorLoginUser visitor = visitorTokenService.getLoginUser(token);
        if (visitor != null)
        {
            return new ChatPrincipal(ChatPrincipal.TYPE_VISITOR, visitor.getVisitorId(), visitor.getVisitorNo(),
                    visitor.getSessionId(), token);
        }
        return null;
    }

    /**
     * 心跳时二次校验令牌是否仍然有效（管理员强制下线、改密后令牌已失效）
     * 
     * @param principal 主体
     * @return 是否仍然有效
     */
    public boolean stillValid(ChatPrincipal principal)
    {
        if (principal.isAgent())
        {
            return agentTokenService.getLoginUserByToken(principal.getToken()) != null;
        }
        return visitorTokenService.getLoginUser(principal.getToken()) != null;
    }

    /**
     * 访客令牌绑定的会话变更时刷新主体
     * 
     * @param principal 主体
     * @return 最新主体
     */
    public ChatPrincipal refresh(ChatPrincipal principal)
    {
        if (principal.isVisitor())
        {
            KfVisitorLoginUser visitor = visitorTokenService.getLoginUser(principal.getToken());
            if (visitor != null)
            {
                return new ChatPrincipal(ChatPrincipal.TYPE_VISITOR, visitor.getVisitorId(), visitor.getVisitorNo(),
                        visitor.getSessionId(), principal.getToken());
            }
        }
        return principal;
    }
}
