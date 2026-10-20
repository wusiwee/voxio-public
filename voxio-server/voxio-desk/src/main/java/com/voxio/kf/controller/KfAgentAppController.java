package com.voxio.kf.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.voxio.common.annotation.Anonymous;
import com.voxio.common.core.controller.BaseController;
import com.voxio.common.core.domain.AjaxResult;
import com.voxio.kf.domain.KfAgentLoginUser;
import com.voxio.kf.security.KfAgentContext;
import com.voxio.kf.service.IKfAgentService;
import com.voxio.kf.service.KfAgentLoginService;

import lombok.RequiredArgsConstructor;

/**
 * 客服端自助接口（客服工作台使用，需客服令牌）
 * 
 * 路径以 @Anonymous 放行 Spring Security，由 KfAgentAuthInterceptor 校验客服令牌，
 * 管理端 sys_user 令牌无法访问本组接口。
 * 
 * @author voxio
 */
@Anonymous
@RestController
@RequestMapping("/kf/agent-app")
@RequiredArgsConstructor
public class KfAgentAppController extends BaseController
{
    private final IKfAgentService agentService;

    private final KfAgentLoginService agentLoginService;

    /**
     * 获取当前登录客服信息
     */
    @GetMapping("/profile")
    public AjaxResult profile()
    {
        KfAgentLoginUser loginUser = KfAgentContext.require();
        AjaxResult ajax = AjaxResult.success();
        ajax.put("agent", agentService.getById(loginUser.getAgentId()));
        ajax.put("login", loginUser);
        return ajax;
    }

    /**
     * 客服登出
     */
    @PostMapping("/logout")
    public AjaxResult logout()
    {
        agentLoginService.logout(KfAgentContext.require());
        return success();
    }
}
