package com.voxio.kf.controller;

import java.util.List;
import java.util.Map;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.voxio.common.annotation.Log;
import com.voxio.common.core.controller.BaseController;
import com.voxio.common.core.domain.AjaxResult;
import com.voxio.common.core.domain.entity.SysUser;
import com.voxio.common.core.page.TableDataInfo;
import com.voxio.common.enums.BusinessType;
import com.voxio.common.utils.SecurityUtils;
import com.voxio.common.utils.StringUtils;
import com.voxio.kf.service.IKfOfflineMessageService;
import com.voxio.kf.util.KfQuerySupport;

import lombok.RequiredArgsConstructor;

/**
 * 离线留言处理（管理后台）
 * 
 * 留言可转为工单跟进或直接关闭，转工单后来源标记为离线留言。
 * 
 * @author voxio
 */
@RestController
@RequestMapping("/kf/offline")
@RequiredArgsConstructor
public class KfOfflineMessageController extends BaseController
{
    private final IKfOfflineMessageService offlineMessageService;

    @PreAuthorize("@ss.hasPermi('kf:offline:list')")
    @GetMapping("/list")
    public TableDataInfo list(@RequestParam(required = false) String status,
            @RequestParam(required = false) Long handlerAgentId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String beginTime,
            @RequestParam(required = false) String endTime)
    {
        startPage();
        List<Map<String, Object>> list = offlineMessageService.selectAdminList(status, handlerAgentId, keyword,
                KfQuerySupport.begin(beginTime), KfQuerySupport.end(endTime));
        return getDataTable(list);
    }

    @PreAuthorize("@ss.hasPermi('kf:offline:query')")
    @GetMapping("/{msgId}")
    public AjaxResult detail(@PathVariable Long msgId)
    {
        return success(offlineMessageService.selectAdminDetail(msgId));
    }

    /**
     * 转为工单：handlerAgentId 为空表示先建单不指派
     */
    @PreAuthorize("@ss.hasPermi('kf:offline:toticket')")
    @Log(title = "离线留言转工单", businessType = BusinessType.INSERT)
    @PostMapping("/{msgId}/toTicket")
    public AjaxResult toTicket(@PathVariable Long msgId, @RequestBody(required = false) Map<String, Object> body)
    {
        Long handlerAgentId = null;
        String title = null;
        if (StringUtils.isNotNull(body))
        {
            Object handler = body.get("handlerAgentId");
            if (StringUtils.isNotNull(handler) && StringUtils.isNotEmpty(String.valueOf(handler)))
            {
                handlerAgentId = Long.valueOf(String.valueOf(handler));
            }
            title = (String) body.get("title");
        }
        return success(offlineMessageService.toTicket(msgId, handlerAgentId, title, SecurityUtils.getUserId(),
                adminName()));
    }

    @PreAuthorize("@ss.hasPermi('kf:offline:close')")
    @Log(title = "关闭离线留言", businessType = BusinessType.UPDATE)
    @PutMapping("/{msgId}/close")
    public AjaxResult close(@PathVariable Long msgId)
    {
        offlineMessageService.close(msgId);
        return success();
    }

    private String adminName()
    {
        SysUser user = SecurityUtils.getLoginUser().getUser();
        return StringUtils.isNotNull(user) && StringUtils.isNotEmpty(user.getNickName()) ? user.getNickName()
                : SecurityUtils.getUsername();
    }
}
