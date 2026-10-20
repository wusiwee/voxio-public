package com.voxio.kf.controller;

import java.util.Map;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.voxio.common.annotation.Log;
import com.voxio.common.core.controller.BaseController;
import com.voxio.common.core.domain.AjaxResult;
import com.voxio.common.enums.BusinessType;
import com.voxio.common.utils.SecurityUtils;
import com.voxio.common.utils.StringUtils;
import com.voxio.kf.service.IKfConfigService;
import com.voxio.kf.service.IKfSessionService;

import lombok.RequiredArgsConstructor;

/**
 * 客服业务设置（管理后台）
 * 
 * 单商户固定白名单参数，密钥类参数仍走 /kf/config 参数配置页，避免误改。
 * 
 * @author voxio
 */
@RestController
@RequestMapping("/kf/settings")
@RequiredArgsConstructor
public class KfSettingsController extends BaseController
{
    private final IKfConfigService configService;

    private final IKfSessionService sessionService;

    /**
     * 查询业务设置
     */
    @PreAuthorize("@ss.hasPermi('kf:settings:list')")
    @GetMapping("/business")
    public AjaxResult business()
    {
        return success(configService.selectBusinessConfig());
    }

    /**
     * 保存业务设置
     */
    @PreAuthorize("@ss.hasPermi('kf:settings:edit')")
    @Log(title = "客服业务设置", businessType = BusinessType.UPDATE)
    @PutMapping("/business")
    public AjaxResult saveBusiness(@RequestBody Map<String, String> configs)
    {
        configService.updateBusinessConfig(configs, SecurityUtils.getUsername());
        // 排队中的会话不会自己动起来（没有新的上线/结束事件触发拉取），
        // 所以重新开启自动分配、或放宽单客服并发上限时，立刻按剩余容量排空一次队列
        boolean autoAssignOn = configs != null && "true".equalsIgnoreCase(
                StringUtils.nvl(configs.get("kf.assign.enabled"), configService.selectConfigByKey("kf.assign.enabled")));
        boolean capacityChanged = configs != null && configs.containsKey("kf.assign.maxPerAgent");
        if (autoAssignOn && (capacityChanged || "true".equalsIgnoreCase(configs.get("kf.assign.enabled"))))
        {
            sessionService.assignQueuedToOnlineAgents();
        }
        return success();
    }
}
