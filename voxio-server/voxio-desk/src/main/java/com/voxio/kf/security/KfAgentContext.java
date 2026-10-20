package com.voxio.kf.security;

import com.voxio.common.exception.ServiceException;
import com.voxio.kf.domain.KfAgentLoginUser;

/**
 * 当前客服登录上下文
 * 
 * @author voxio
 */
public class KfAgentContext
{
    private static final ThreadLocal<KfAgentLoginUser> HOLDER = new ThreadLocal<KfAgentLoginUser>();

    public static void set(KfAgentLoginUser loginUser)
    {
        HOLDER.set(loginUser);
    }

    public static KfAgentLoginUser get()
    {
        return HOLDER.get();
    }

    /**
     * 获取当前客服，未登录直接拒绝
     */
    public static KfAgentLoginUser require()
    {
        KfAgentLoginUser loginUser = HOLDER.get();
        if (loginUser == null)
        {
            throw new ServiceException("客服登录状态已失效，请重新登录");
        }
        return loginUser;
    }

    public static Long getAgentId()
    {
        KfAgentLoginUser loginUser = HOLDER.get();
        return loginUser == null ? null : loginUser.getAgentId();
    }

    public static void clear()
    {
        HOLDER.remove();
    }
}
