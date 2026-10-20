package com.voxio.kf.controller;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.voxio.common.annotation.Anonymous;
import com.voxio.common.annotation.Log;
import com.voxio.common.core.controller.BaseController;
import com.voxio.common.core.domain.AjaxResult;
import com.voxio.common.core.page.TableDataInfo;
import com.voxio.common.enums.BusinessType;
import com.voxio.common.exception.ServiceException;
import com.voxio.common.utils.DateUtils;
import com.voxio.common.utils.StringUtils;
import com.voxio.kf.domain.KfAgentLoginUser;
import com.voxio.kf.domain.KfTicket;
import com.voxio.kf.domain.KfTicketReply;
import com.voxio.kf.security.KfAgentContext;
import com.voxio.kf.service.IKfSessionService;
import com.voxio.kf.service.IKfTicketService;

import lombok.RequiredArgsConstructor;

/**
 * 客服端工作台接口（网页会话监控使用，需客服令牌）
 * 
 * 承载历史会话查询与工单创建/流转；聊天收发仍走 Netty 网关。
 * 
 * @author voxio
 */
@Anonymous
@RestController
@RequestMapping("/kf/agent-app")
@RequiredArgsConstructor
public class KfAgentWorkController extends BaseController
{
    private final IKfSessionService sessionService;

    private final IKfTicketService ticketService;

    /**
     * 历史会话分页查询（时间、访客关键字、状态），只看自己接待过的会话
     */
    @GetMapping("/sessions/history")
    public TableDataInfo history(@RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String beginTime,
            @RequestParam(required = false) String endTime)
    {
        startPage();
        List<Map<String, Object>> list = sessionService.listAgentHistory(current().getAgentId(), status, keyword,
                parseDate(beginTime), parseDate(endTime));
        return getDataTable(list);
    }

    /**
     * 创建工单（可从会话内创建，自动关联访客与会话）
     */
    @Log(title = "客服建单", businessType = BusinessType.INSERT, excludeParamNames = { "content", "contact" })
    @PostMapping("/ticket")
    public AjaxResult createTicket(@RequestBody KfTicket ticket)
    {
        KfAgentLoginUser loginUser = current();
        KfTicket created = ticketService.create(ticket, loginUser.getAgentId(), loginUser.getNickName());
        return success(created);
    }

    /**
     * 手动同步工单到第三方系统：只能推本人创建或本人处理的工单
     */
    @Log(title = "客服工单第三方推送", businessType = BusinessType.OTHER)
    @PostMapping("/ticket/{ticketId}/push")
    public AjaxResult pushTicket(@PathVariable Long ticketId)
    {
        KfAgentLoginUser loginUser = current();
        KfTicket ticket = ticketService.getById(ticketId);
        if (StringUtils.isNull(ticket))
        {
            throw new ServiceException("工单不存在", 404);
        }
        if (!visibleTo(ticket, loginUser.getAgentId()))
        {
            throw new ServiceException("无权推送该工单", 403);
        }
        return success(ticketService.pushToThirdParty(ticketId, loginUser.getNickName()));
    }

    /**
     * 我的工单分页列表（本人创建或本人处理）
     */
    @GetMapping("/tickets")
    public TableDataInfo tickets(KfTicket query, @RequestParam(required = false) String status)
    {
        startPage();
        if (StringUtils.isNotEmpty(status))
        {
            query.setStatus(status);
        }
        List<KfTicket> list = ticketService.selectForAgent(query, current().getAgentId());
        return getDataTable(list);
    }

    /**
     * 工单详情与流转记录
     */
    @GetMapping("/ticket/{ticketId}")
    public AjaxResult ticketDetail(@PathVariable Long ticketId)
    {
        KfTicket ticket = ticketService.getById(ticketId);
        if (StringUtils.isNull(ticket))
        {
            throw new ServiceException("工单不存在", 404);
        }
        if (!visibleTo(ticket, current().getAgentId()))
        {
            throw new ServiceException("无权查看该工单", 403);
        }
        AjaxResult ajax = AjaxResult.success();
        ajax.put("ticket", ticket);
        List<KfTicketReply> replies = ticketService.listReplies(ticketId);
        ajax.put("replies", replies);
        return ajax;
    }

    /**
     * 工单状态流转（简单流转：待处理→处理中→已解决→已关闭）
     */
    @Log(title = "客服工单状态流转", businessType = BusinessType.UPDATE)
    @PutMapping("/ticket/{ticketId}/status")
    public AjaxResult changeTicketStatus(@PathVariable Long ticketId, @RequestBody Map<String, String> body)
    {
        KfAgentLoginUser loginUser = current();
        KfTicket ticket = ticketService.getById(ticketId);
        if (StringUtils.isNull(ticket))
        {
            throw new ServiceException("工单不存在", 404);
        }
        if (!visibleTo(ticket, loginUser.getAgentId()))
        {
            throw new ServiceException("无权操作该工单", 403);
        }
        KfTicket updated = ticketService.changeStatus(ticketId,
                StringUtils.isNotNull(body) ? body.get("status") : null,
                StringUtils.isNotNull(body) ? body.get("note") : null,
                loginUser.getAgentId(), loginUser.getNickName());
        return success(updated);
    }

    private boolean visibleTo(KfTicket ticket, Long agentId)
    {
        return agentId.equals(ticket.getCreatorId()) || agentId.equals(ticket.getHandlerAgentId());
    }

    private java.util.Date parseDate(String text)
    {
        if (StringUtils.isEmpty(text))
        {
            return null;
        }
        // 客服端传 yyyy-MM-dd 或 yyyy-MM-dd HH:mm:ss 两种粒度
        String value = text.trim();
        if (value.length() == 10)
        {
            value = value + " 00:00:00";
        }
        java.util.Date date = DateUtils.parseDate(value);
        if (StringUtils.isNull(date))
        {
            throw new ServiceException("时间格式应为 yyyy-MM-dd");
        }
        return date;
    }

    private KfAgentLoginUser current()
    {
        return KfAgentContext.require();
    }
}
