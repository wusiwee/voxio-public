package com.voxio.kf.controller;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.voxio.common.annotation.Anonymous;
import com.voxio.common.constant.Constants;
import com.voxio.common.core.controller.BaseController;
import com.voxio.common.core.domain.AjaxResult;
import com.voxio.common.exception.ServiceException;
import com.voxio.common.utils.StringUtils;
import com.voxio.kf.domain.KfAgent;
import com.voxio.kf.domain.KfVisitorLoginUser;
import com.voxio.kf.event.KfChatEventPublisher;
import com.voxio.kf.service.IKfAgentService;
import com.voxio.kf.service.IKfConfigService;
import com.voxio.kf.service.IKfOfflineMessageService;
import com.voxio.kf.service.IKfSecurityService;
import com.voxio.kf.service.IKfSessionService;
import com.voxio.kf.service.IKfVisitorTokenService;

import lombok.RequiredArgsConstructor;

/**
 * 访客端入口接口：接入状态、离线留言
 * 
 * 全部挂在 /kf/open/** 下并用访客令牌校验身份，不进入若依权限体系。
 * 
 * @author voxio
 */
@Anonymous
@RestController
@RequestMapping("/kf/open")
@RequiredArgsConstructor
public class KfOpenVisitorController extends BaseController
{
    private final IKfConfigService configService;

    private final IKfAgentService agentService;

    private final IKfOfflineMessageService offlineMessageService;

    private final IKfSessionService sessionService;

    private final IKfVisitorTokenService visitorTokenService;

    private final KfChatEventPublisher eventPublisher;


    private final IKfSecurityService securityService;

    /**
     * 加密策略公开部分：开关、算法、服务端 SM2 公钥与密钥版本
     *
     * 公钥本就是用来公开的，客户端拿它加密会话密钥；私钥与主密钥不在此接口出现，
     * 也不在 /kf/open/config 的白名单里（那张表白名单只放文案与开关类参数）。
     */
    @GetMapping("/security")
    public AjaxResult security()
    {
        return success(securityService.publicStatus());
    }

    /**
     * 入口状态：是否在服务时间、是否有在线客服、排队人数、能否聊天/留言
     */
    @GetMapping("/status")
    public AjaxResult status()
    {
        List<KfAgent> liveAgents = listLiveAgents();
        boolean inServiceTime = offlineMessageService.isInServiceTime();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("inServiceTime", inServiceTime);
        data.put("onlineAgents", liveAgents.size());
        data.put("idleAgents", countIdleAgents(liveAgents));
        data.put("canChat", !liveAgents.isEmpty());
        data.put("queuedCount", sessionService.countQueued());
        data.put("canOffline", offlineMessageService.isOfflineAllowed(liveAgents.size()));
        data.put("config", configService.selectPublicConfig());
        return success(data);
    }

    /**
     * 提交离线留言（无客服在线或非服务时间）
     */
    @PostMapping("/offline")
    public AjaxResult offline(@RequestHeader(value = "Authorization", required = false) String auth,
            @RequestBody Map<String, String> body)
    {
        KfVisitorLoginUser loginUser = requireVisitor(auth);
        if (!configService.selectBooleanByKey("kf.offline.enabled", true))
        {
            throw new ServiceException("当前未开放留言", 403);
        }
        Long sessionId = StringUtils.isNotNull(body) && StringUtils.isNotEmpty(body.get("sessionId"))
                ? Long.valueOf(body.get("sessionId")) : loginUser.getSessionId();
        Long msgId = offlineMessageService.submit(loginUser.getVisitorId(), sessionId,
                StringUtils.isNotNull(body) ? body.get("contact") : null,
                StringUtils.isNotNull(body) ? body.get("content") : null);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("msgId", msgId);
        data.put("tip", configService.selectConfigByKey("kf.offline.reply.tip"));
        return success(data);
    }

    /**
     * 在线客服清单：以网关连接为准，避免服务重启后 DB 残留在线标记
     *
     * 这里只统计人数与余量，所以只取判定要用的四列；整行实体要把手机号/邮箱解密一遍才出得来，白给。
     */
    private List<KfAgent> listLiveAgents()
    {
        List<KfAgent> agents = agentService.list(new LambdaQueryWrapper<KfAgent>()
                .select(KfAgent::getAgentId, KfAgent::getOnlineStatus, KfAgent::getCurrentLoad, KfAgent::getAcceptMax)
                .eq(KfAgent::getStatus, "0")
                .in(KfAgent::getOnlineStatus, IKfAgentService.STATUS_ONLINE, IKfAgentService.STATUS_BUSY)
                .eq(KfAgent::getDelFlag, "0"));
        return agents.stream().filter(a -> eventPublisher.isAgentOnline(a.getAgentId())).toList();
    }

    /**
     * 还有余量的客服数，判定口径与 KfAgentMapper.selectAvailableAgent 一致，
     * 访客端据此如实告知「空闲」还是「需要排队」
     */
    private int countIdleAgents(List<KfAgent> liveAgents)
    {
        int globalMax = configService.selectIntByKey("kf.assign.maxPerAgent", 5);
        int idle = 0;
        for (KfAgent agent : liveAgents)
        {
            if (IKfAgentService.STATUS_ONLINE.equals(agent.getOnlineStatus())
                    && StringUtils.nvl(agent.getCurrentLoad(), 0) < Math.min(StringUtils.nvl(agent.getAcceptMax(), globalMax), globalMax))
            {
                idle++;
            }
        }
        return idle;
    }

    private KfVisitorLoginUser requireVisitor(String auth)
    {
        String token = StringUtils.isNotEmpty(auth) && auth.startsWith(Constants.TOKEN_PREFIX)
                ? auth.substring(Constants.TOKEN_PREFIX.length()) : auth;
        KfVisitorLoginUser loginUser = visitorTokenService.getLoginUser(token);
        if (StringUtils.isNull(loginUser))
        {
            throw new ServiceException("访客身份已失效，请重新发起咨询", 401);
        }
        return loginUser;
    }
}
