package com.voxio.kf.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.voxio.common.annotation.Anonymous;
import com.voxio.common.core.controller.BaseController;
import com.voxio.common.core.domain.AjaxResult;
import com.voxio.kf.service.IKfConfigService;

import lombok.RequiredArgsConstructor;

/**
 * 访客端只读接口（业务参数白名单）
 * 
 * 聊天收发一律走 Netty 网关，不提供 HTTP 聊天接口。
 * 
 * @author voxio
 */
@Anonymous
@RestController
@RequestMapping("/kf/open")
@RequiredArgsConstructor
public class KfOpenController extends BaseController
{
    private final IKfConfigService configService;

    /**
     * 访客端可见参数（白名单）
     */
    @GetMapping("/config")
    public AjaxResult config()
    {
        return success(configService.selectPublicConfig());
    }
}
