package com.voxio.kf.controller;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.voxio.common.annotation.Anonymous;
import com.voxio.common.annotation.Log;
import com.voxio.common.core.controller.BaseController;
import com.voxio.common.core.domain.AjaxResult;
import com.voxio.common.enums.BusinessType;
import com.voxio.common.exception.ServiceException;
import com.voxio.common.utils.StringUtils;
import com.voxio.kf.domain.KfAgent;
import com.voxio.kf.domain.KfAgentLoginUser;
import com.voxio.kf.domain.KfMessage;
import com.voxio.kf.domain.KfSession;
import com.voxio.kf.domain.dto.KfAgentOnlineView;
import com.voxio.kf.security.KfAgentContext;
import com.voxio.kf.service.IKfAgentService;
import com.voxio.kf.service.IKfMessageService;
import com.voxio.kf.service.IKfSessionService;

import lombok.RequiredArgsConstructor;

/**
 * 客服端会话接口（网页会话监控使用，需客服令牌）
 * 
 * @author voxio
 */
@Anonymous
@RestController
@RequestMapping("/kf/agent-app")
@RequiredArgsConstructor
public class KfAgentSessionController extends BaseController
{
    private final IKfSessionService sessionService;

    private final IKfMessageService messageService;

    private final IKfAgentService agentService;

    /**
     * 我的会话列表，status 为空返回进行中（排队+接待）
     */
    @GetMapping("/sessions")
    public AjaxResult sessions(@RequestParam(required = false) String status)
    {
        Long agentId = KfAgentContext.require().getAgentId();
        return success(sessionService.listAgentSessionView(agentId, status));
    }

    /**
     * 会话历史消息（向前翻页），拉取即视为读完
     */
    @GetMapping("/sessions/{sessionId}/messages")
    public AjaxResult messages(@PathVariable Long sessionId,
            @RequestParam(required = false) Integer beforeSeq,
            @RequestParam(defaultValue = "30") int limit)
    {
        KfSession session = requireOwnedSession(sessionId);
        List<KfMessage> list = messageService.listBefore(sessionId, beforeSeq, Math.min(Math.max(limit, 1), 100));
        sessionService.markRead(sessionId, KfMessage.SENDER_AGENT, session.getLastSeq());
        return success(list);
    }

    /**
     * 客服结束会话
     */
    @Log(title = "客服结束会话", businessType = BusinessType.UPDATE)
    @PostMapping("/sessions/{sessionId}/end")
    public AjaxResult end(@PathVariable Long sessionId, @RequestBody(required = false) Map<String, String> body)
    {
        requireOwnedSession(sessionId);
        sessionService.endSession(sessionId, IKfSessionService.END_BY_AGENT, currentAgentId(),
                StringUtils.isNotNull(body) ? body.get("reason") : null);
        return success();
    }

    /**
     * 一键接入排队会话：客服自己接手排队访客，不需要主管在后台指派
     *
     * 之前客服端只有「结束」一个动作，队列里等着的访客只能等自动分配或等主管动手；
     * 这条接口和审计（@Log）一起补上，"谁在什么时候接了哪条会话"两侧都留痕。
     * 会话标识放在路径上而不是让服务端挑队首，正是为了让留痕里带得上对象标识。
     */
    @Log(title = "客服一键接入", businessType = BusinessType.UPDATE)
    @PostMapping("/sessions/{sessionId}/accept")
    public AjaxResult accept(@PathVariable Long sessionId)
    {
        KfAgentLoginUser loginUser = KfAgentContext.require();
        KfSession session = sessionService.acceptSession(sessionId, loginUser.getAgentId(), loginUser.getNickName());
        return success(session);
    }

    /**
     * 在线客服列表（账号、昵称、在线态与实时负载）
     */
    @GetMapping("/agents/online")
    public AjaxResult onlineAgents()
    {
        // 只取面板要用的列：整行实体里带口令哈希与加密联系方式，
        // 逐行解密（SM4-GCM）纯属浪费，也不该让口令字段靠事后置空来防泄漏。
        // 返回体用视图投影，而不是"实体只填六个字段、其余序列化成一堆 null 键"。
        List<KfAgent> list = agentService.list(new LambdaQueryWrapper<KfAgent>()
                .select(KfAgent::getAgentId, KfAgent::getAgentNo, KfAgent::getNickName,
                        KfAgent::getOnlineStatus, KfAgent::getCurrentLoad, KfAgent::getAcceptMax)
                .eq(KfAgent::getStatus, "0")
                .in(KfAgent::getOnlineStatus, IKfAgentService.STATUS_ONLINE, IKfAgentService.STATUS_BUSY)
                .orderByAsc(KfAgent::getCurrentLoad));
        return success(list.stream().map(KfAgentOnlineView::from).toList());
    }

    private Long currentAgentId()
    {
        return KfAgentContext.require().getAgentId();
    }

    private KfSession requireOwnedSession(Long sessionId)
    {
        KfSession session = sessionService.getSession(sessionId);
        if (StringUtils.isNull(session))
        {
            throw new ServiceException("会话不存在");
        }
        if (!currentAgentId().equals(session.getAgentId()))
        {
            throw new ServiceException("该会话不属于当前客服", 403);
        }
        return session;
    }
}
