package com.voxio.kf.controller;

import java.util.List;
import java.util.Map;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.voxio.common.annotation.Log;
import com.voxio.common.core.controller.BaseController;
import com.voxio.common.core.domain.AjaxResult;
import com.voxio.common.core.page.TableDataInfo;
import com.voxio.common.enums.BusinessType;
import com.voxio.common.exception.ServiceException;
import com.voxio.common.utils.StringUtils;
import com.voxio.kf.service.IKfMonitorService;
import com.voxio.kf.service.IKfSessionService;
import com.voxio.kf.util.KfQuerySupport;

import lombok.RequiredArgsConstructor;

/**
 * 会话监控（管理后台）
 * 
 * 进行中会话、历史会话、会话详情与客服实时状态为只读；额外提供排队会话的人工分配，
 * 用于关闭自动分配后由主管指定客服，接待中的会话仍然不做强制接管。
 * 
 * @author voxio
 */
@RestController
@RequestMapping("/kf/monitor")
@RequiredArgsConstructor
public class KfMonitorController extends BaseController
{
    private final IKfMonitorService monitorService;

    private final IKfSessionService sessionService;

    /**
     * 会话列表。status：active 进行中（默认）/ all 全部 / 0 排队 / 1 接待 / 2 结束
     */
    @PreAuthorize("@ss.hasPermi('kf:monitor:list')")
    @GetMapping("/sessions")
    public TableDataInfo sessions(@RequestParam(required = false) String status,
            @RequestParam(required = false) Long agentId,
            @RequestParam(required = false) Long visitorId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String beginTime,
            @RequestParam(required = false) String endTime)
    {
        startPage();
        List<Map<String, Object>> list = monitorService.selectSessionList(StringUtils.nvl(status, "active"), agentId,
                visitorId, keyword, KfQuerySupport.begin(beginTime), KfQuerySupport.end(endTime));
        return getDataTable(list);
    }

    /**
     * 客服在线状态与实时负载
     */
    @PreAuthorize("@ss.hasPermi('kf:monitor:query')")
    @GetMapping("/agents")
    public AjaxResult agents(@RequestParam(required = false) String onlineStatus,
            @RequestParam(required = false) String keyword)
    {
        return success(monitorService.selectAgentStatus(onlineStatus, keyword));
    }

    /**
     * 会话详情（含访客、客服与最近消息，只读）
     */
    @PreAuthorize("@ss.hasPermi('kf:monitor:detail')")
    @GetMapping("/session/{sessionId}")
    public AjaxResult detail(@PathVariable Long sessionId)
    {
        Map<String, Object> detail = monitorService.selectSessionDetail(sessionId);
        if (StringUtils.isNull(detail))
        {
            throw new ServiceException("会话不存在");
        }
        return success(detail);
    }

    /**
     * 人工分配排队中的会话给指定客服，接待中的会话不接受改派
     */
    @PreAuthorize("@ss.hasPermi('kf:monitor:assign')")
    @Log(title = "会话人工分配", businessType = BusinessType.UPDATE)
    @PutMapping("/session/{sessionId}/assign/{agentId}")
    public AjaxResult assign(@PathVariable Long sessionId, @PathVariable Long agentId)
    {
        return success(sessionService.assignManually(sessionId, agentId, getUsername()));
    }
}
