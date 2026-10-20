package com.voxio.kf.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.voxio.common.annotation.Anonymous;
import com.voxio.common.core.controller.BaseController;
import com.voxio.common.core.domain.AjaxResult;
import com.voxio.kf.domain.dto.KfAgentLoginBody;
import com.voxio.kf.service.KfAgentLoginService;

import lombok.RequiredArgsConstructor;

/**
 * 客服端登录（客服工作台使用，账号来源 kf_agent，与管理员 sys_user 隔离）
 * 
 * @author voxio
 */
@RestController
@RequestMapping("/kf/agent")
@RequiredArgsConstructor
public class KfAgentLoginController extends BaseController
{
    private final KfAgentLoginService agentLoginService;

    /**
     * 客服账号登录
     */
    @Anonymous
    @PostMapping("/login")
    public AjaxResult login(@RequestBody KfAgentLoginBody body)
    {
        String token = agentLoginService.login(body.getAgentNo(), body.getPassword());
        AjaxResult ajax = AjaxResult.success();
        ajax.put("token", token);
        return ajax;
    }
}
