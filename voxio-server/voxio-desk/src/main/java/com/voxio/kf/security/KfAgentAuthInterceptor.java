package com.voxio.kf.security;

import org.springframework.web.servlet.HandlerInterceptor;

import com.alibaba.fastjson2.JSON;
import com.voxio.common.core.domain.AjaxResult;
import com.voxio.common.utils.OperatorContext;
import com.voxio.kf.domain.KfAgentLoginUser;
import com.voxio.kf.service.IKfAgentTokenService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * 客服端接口令牌校验拦截器
 * 
 * 仅作用于 /kf/agent-app/**，不进入若依权限体系（客服账号无 sys_user 角色）。
 * 
 * @author voxio
 */
@RequiredArgsConstructor
public class KfAgentAuthInterceptor implements HandlerInterceptor
{
    private final IKfAgentTokenService tokenService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception
    {
        KfAgentLoginUser loginUser = tokenService.getLoginUser(request);
        if (loginUser == null)
        {
            response.setStatus(HttpServletResponse.SC_OK);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(JSON.toJSONString(AjaxResult.error(401, "客服登录状态已失效，请重新登录")));
            return false;
        }
        KfAgentContext.set(loginUser);
        // 审计切面取不到若依登录用户时用它兜底，否则客服端所有写操作都不会留痕（阶段10.5 P0-3）
        OperatorContext.set("客服:" + loginUser.getAgentNo());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex)
    {
        KfAgentContext.clear();
        OperatorContext.clear();
    }
}
