package com.voxio.kf.controller;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.voxio.common.annotation.Anonymous;
import com.voxio.common.constant.Constants;

import com.voxio.common.core.controller.BaseController;
import com.voxio.common.core.domain.AjaxResult;
import com.voxio.common.exception.ServiceException;
import com.voxio.common.utils.StringUtils;
import com.voxio.common.utils.ip.IpUtils;
import com.voxio.kf.domain.KfAgent;
import com.voxio.kf.domain.KfMessage;
import com.voxio.kf.domain.KfSession;
import com.voxio.kf.domain.KfVisitorLoginUser;
import com.voxio.kf.service.IKfAgentService;
import com.voxio.kf.service.IKfMessageService;
import com.voxio.kf.service.IKfSessionService;
import com.voxio.kf.service.IKfVisitorTokenService;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

/**
 * 访客端会话接口（创建/恢复会话、拉取历史、结束会话）
 * 
 * 聊天收发走 Netty 网关，本处只负责会话建立、令牌签发与历史补拉。
 * 
 * @author voxio
 */
@Anonymous
@RestController
@RequestMapping("/kf/open/session")
@RequiredArgsConstructor
public class KfOpenSessionController extends BaseController
{
    private final IKfSessionService sessionService;

    private final IKfMessageService messageService;

    private final IKfAgentService agentService;

    private final IKfVisitorTokenService visitorTokenService;

    /**
     * 创建或恢复会话，返回访客令牌
     */
    @PostMapping
    public AjaxResult create(@RequestBody Map<String, String> body, HttpServletRequest request)
    {
        String visitorNo = body.get("visitorNo");
        String ip = IpUtils.getIpAddr();
        KfSession session = sessionService.createOrResume(visitorNo, body.get("nickname"), ip,
                request.getHeader("User-Agent"), body.get("referrer"), body.get("sourcePage"));

        KfVisitorLoginUser loginUser = new KfVisitorLoginUser();
        loginUser.setVisitorId(session.getVisitorId());
        loginUser.setVisitorNo(visitorNo);
        loginUser.setSessionId(session.getSessionId());
        loginUser.setIpaddr(ip);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("sessionId", session.getSessionId());
        data.put("sessionNo", session.getSessionNo());
        data.put("status", session.getStatus());
        data.put("visitorToken", visitorTokenService.createToken(loginUser));
        data.put("position", sessionService.queuePosition(session));
        if (StringUtils.isNotNull(session.getAgentId()))
        {
            // 只要昵称：整行 KfAgent 会把口令哈希与加密联系方式一起读出来再逐行解密
            KfAgent agent = agentService.getOne(new LambdaQueryWrapper<KfAgent>()
                    .select(KfAgent::getNickName).eq(KfAgent::getAgentId, session.getAgentId()), false);
            data.put("agentNick", StringUtils.isNotNull(agent) ? agent.getNickName() : null);
        }
        return success(data);
    }

    /**
     * 拉取会话历史（向前翻页），拉取即视为读完
     */
    @GetMapping("/messages")
    public AjaxResult messages(@RequestHeader(value = "Authorization", required = false) String auth,
            @RequestParam(required = false) Long sessionId,
            @RequestParam(required = false) Integer beforeSeq,
            @RequestParam(defaultValue = "30") int limit)
    {
        KfVisitorLoginUser loginUser = requireVisitor(auth);
        KfSession session = requireAccessibleSession(loginUser, sessionId);
        Long sid = session.getSessionId();
        List<KfMessage> list = messageService.listBefore(sid, beforeSeq, Math.min(Math.max(limit, 1), 100));
        sessionService.markRead(sid, KfMessage.SENDER_VISITOR, session.getLastSeq());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("sessionId", sid);
        // 状态直接复用上面已经读出来的会话，不再回查一次主键
        data.put("status", session.getStatus());
        data.put("messages", list);
        return success(data);
    }

    /**
     * 访客结束会话
     * 
     * 令牌保留至自然过期：结束后访客还要用同一身份提交离线留言。
     */
    @PostMapping("/end")
    public AjaxResult end(@RequestHeader(value = "Authorization", required = false) String auth,
            @RequestBody(required = false) Map<String, String> body)
    {
        KfVisitorLoginUser loginUser = requireVisitor(auth);
        Long sessionId = StringUtils.isNotNull(body) && StringUtils.isNotEmpty(body.get("sessionId"))
                ? Long.valueOf(body.get("sessionId")) : null;
        Long sid = resolveSessionId(loginUser, sessionId);
        sessionService.endSession(sid, IKfSessionService.END_BY_VISITOR, null,
                StringUtils.isNotNull(body) ? body.get("reason") : null);
        KfSession session = sessionService.getSession(sid);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("sessionId", sid);
        data.put("status", session.getStatus());
        return success(data);
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

    private Long resolveSessionId(KfVisitorLoginUser loginUser, Long sessionId)
    {
        return requireAccessibleSession(loginUser, sessionId).getSessionId();
    }

    /**
     * 取访客有权访问的会话（一次主键读同时完成归属校验，调用方要状态时不必再回查）
     */
    private KfSession requireAccessibleSession(KfVisitorLoginUser loginUser, Long sessionId)
    {
        Long sid = StringUtils.isNotNull(sessionId) ? sessionId : loginUser.getSessionId();
        if (StringUtils.isNull(sid))
        {
            KfSession active = sessionService.findActiveByVisitor(loginUser.getVisitorId());
            if (StringUtils.isNull(active))
            {
                throw new ServiceException("当前没有进行中的会话");
            }
            return active;
        }
        KfSession session = sessionService.getSession(sid);
        if (StringUtils.isNull(session))
        {
            throw new ServiceException("会话不存在");
        }
        if (!session.getVisitorId().equals(loginUser.getVisitorId()))
        {
            throw new ServiceException("无权访问该会话", 403);
        }
        return session;
    }
}
