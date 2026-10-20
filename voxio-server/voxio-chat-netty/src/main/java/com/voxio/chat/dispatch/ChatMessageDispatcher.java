package com.voxio.chat.dispatch;

import java.util.Date;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import com.voxio.chat.config.ChatGatewayProperties;
import com.voxio.chat.metrics.GatewayMetrics;
import com.voxio.chat.protocol.ChatCmd;
import com.voxio.chat.protocol.ChatEnvelope;
import com.voxio.chat.protocol.ChatError;
import com.voxio.chat.protocol.ChatPrincipal;
import com.voxio.chat.session.ConnectionRegistry;
import com.voxio.common.exception.ServiceException;
import com.voxio.kf.domain.KfMessage;
import com.voxio.kf.domain.KfSession;
import com.voxio.kf.service.IKfMessageService;
import com.voxio.kf.service.IKfSessionService;

/**
 * 消息路由：仅做协议编排、校验与投递，落库与查询全部委托 voxio-desk 业务服务
 * 
 * @author voxio
 */
@Component
public class ChatMessageDispatcher
{
    private static final Logger log = LoggerFactory.getLogger("kf.gateway");

    private final ConnectionRegistry registry;

    private final GatewayMetrics metrics;

    private final IKfMessageService messageService;

    private final IKfSessionService sessionService;

    private final int maxContentLength;

    private final int syncLimit;

    /** 投递后的状态写入是否走本地队列（阶段11） */
    private final boolean persistAsync;

    private final PostDeliveryWriter postDelivery;

    public ChatMessageDispatcher(ConnectionRegistry registry, GatewayMetrics metrics,
            IKfMessageService messageService, IKfSessionService sessionService,
            PostDeliveryWriter postDelivery, ObjectProvider<ChatGatewayProperties> propertiesProvider)
    {
        this.registry = registry;
        this.metrics = metrics;
        this.messageService = messageService;
        this.sessionService = sessionService;
        this.postDelivery = postDelivery;
        ChatGatewayProperties properties = propertiesProvider.getIfAvailable();
        this.maxContentLength = properties == null ? 2000 : properties.getMaxContentLength();
        this.syncLimit = properties == null ? 200 : properties.getSyncLimit();
        this.persistAsync = properties == null || properties.isPersistAsync();
    }

    /**
     * 处理鉴权后的业务帧
     * 
     * @param principal 连接主体
     * @param envelope 消息信封
     */
    public void dispatch(ChatPrincipal principal, ChatEnvelope envelope)
    {
        String cmd = envelope.getCmd();
        if (ChatCmd.CHAT.equals(cmd))
        {
            handleChat(principal, envelope);
        }
        else if (ChatCmd.READ.equals(cmd))
        {
            handleRead(principal, envelope);
        }
        else if (ChatCmd.SYNC.equals(cmd))
        {
            handleSync(principal, envelope);
        }
        else if (ChatCmd.TYPING.equals(cmd))
        {
            handleTyping(principal, envelope);
        }
        else
        {
            sendError(principal, envelope, ChatError.PROTOCOL_ERROR, "不支持的命令：" + cmd);
        }
    }

    private void handleChat(ChatPrincipal principal, ChatEnvelope envelope)
    {
        KfSession session = resolveSession(principal, envelope);
        if (session == null)
        {
            sendError(principal, envelope, ChatError.SESSION_INVALID, "会话不存在或无权访问");
            return;
        }
        if (KfSession.STATUS_ENDED.equals(session.getStatus()))
        {
            sendError(principal, envelope, ChatError.SESSION_INVALID, "会话已结束");
            return;
        }
        Long sessionId = session.getSessionId();
        String content = envelope.bodyText("content");
        // 本系统只支持纯文本消息：没有上传接口，也就没有图片/文件消息
        if (!isBlank(envelope.bodyText("mediaUrl")) || !isBlank(envelope.bodyText("fileName")))
        {
            sendError(principal, envelope, ChatError.PROTOCOL_ERROR, "仅支持文本消息");
            return;
        }
        if (isBlank(content))
        {
            sendError(principal, envelope, ChatError.PROTOCOL_ERROR, "消息内容不能为空");
            return;
        }
        if (content != null && content.length() > maxContentLength)
        {
            sendError(principal, envelope, ChatError.MESSAGE_TOO_LARGE, "消息内容超过 " + maxContentLength + " 字");
            return;
        }

        String clientMsgId = envelope.getMid();
        if (isBlank(clientMsgId))
        {
            clientMsgId = UUID.randomUUID().toString().replace("-", "");
        }
        KfMessage duplicated = messageService.findByClientMsgId(sessionId, clientMsgId);
        if (duplicated != null)
        {
            ackSend(principal, envelope, sessionId, duplicated, true);
            return;
        }

        String senderType = senderType(principal);
        KfMessage message;
        try
        {
            // 会话侧的未读与首次响应时间在这一条语句里一起写（阶段11），下面不再单独发 UPDATE
            message = messageService.appendRealtimeMessage(sessionId, senderType, principal.getId(),
                    msgType(envelope), content, clientMsgId);
        }
        catch (ServiceException e)
        {
            sendError(principal, envelope, ChatError.PROTOCOL_ERROR, e.getMessage());
            return;
        }

        ackSend(principal, envelope, sessionId, message, false);

        ChatEnvelope push = toPush(message, senderType, principal.getId());
        String peerKey = peerKey(principal, session);
        if (peerKey != null && registry.send(peerKey, push))
        {
            // 投递已经发生，送达状态不再挡在这条消息的后面：交给本地队列按会话保序落库，失败退避重试
            if (persistAsync)
            {
                postDelivery.submitMarkSent(sessionId, senderType);
            }
            else
            {
                messageService.markSent(sessionId, senderType);
            }
            metrics.onMessageOut();
        }
        else
        {
            // 对端不在线：消息保留为已落库未送达，重连后由 SYNC 补发
            metrics.onOfflineStored();
        }
    }

    private void handleRead(ChatPrincipal principal, ChatEnvelope envelope)
    {
        KfSession session = resolveSession(principal, envelope);
        if (session == null)
        {
            sendError(principal, envelope, ChatError.SESSION_INVALID, "会话不存在或无权访问");
            return;
        }
        Integer untilSeq = envelope.bodyInt("untilSeq");
        String readerType = senderType(principal);
        // 会话服务内部会同步标记对方消息已读并清零本方未读
        sessionService.markRead(session.getSessionId(), readerType, untilSeq);

        ChatEnvelope notify = new ChatEnvelope();
        notify.setCmd(ChatCmd.READ_NOTIFY);
        notify.setSid(session.getSessionId());
        notify.setFrom(party(principal));
        notify.setTs(System.currentTimeMillis());
        notify.getBody().put("readerType", readerType);
        notify.getBody().put("readerId", principal.getId());
        notify.getBody().put("untilSeq", untilSeq);
        String peerKey = peerKey(principal, session);
        if (peerKey != null && registry.send(peerKey, notify))
        {
            metrics.onMessageOut();
        }
    }

    private void handleSync(ChatPrincipal principal, ChatEnvelope envelope)
    {
        KfSession session = resolveSession(principal, envelope);
        if (session == null)
        {
            sendError(principal, envelope, ChatError.SESSION_INVALID, "会话不存在或无权访问");
            return;
        }
        Integer sinceSeq = envelope.bodyInt("sinceSeq");
        int from = sinceSeq == null ? 0 : sinceSeq;
        List<KfMessage> messages = messageService.listAfter(session.getSessionId(), from, syncLimit);
        int delivered = 0;
        for (KfMessage message : messages)
        {
            if (registry.send(principal.key(), toPush(message, message.getSenderType(), message.getSenderId())))
            {
                delivered++;
            }
        }
        metrics.onMessageOut();
        if (delivered > 0)
        {
            // 补发即视为送达：只翻转对方 authored 的消息，本方消息的送达状态由实时投递决定
            String peerSenderType = principal.isAgent() ? KfMessage.SENDER_VISITOR : KfMessage.SENDER_AGENT;
            messageService.markSent(session.getSessionId(), peerSenderType);
        }

        ChatEnvelope ack = baseAck(principal, envelope, session.getSessionId());
        ack.getBody().put("action", "sync");
        ack.getBody().put("count", delivered);
        ack.getBody().put("sinceSeq", from);
        ack.getBody().put("maxSeq", messages.isEmpty() ? from : messages.get(messages.size() - 1).getSeq());
        registry.send(principal.key(), ack);
    }

    private void handleTyping(ChatPrincipal principal, ChatEnvelope envelope)
    {
        KfSession session = resolveSession(principal, envelope);
        if (session == null)
        {
            return;
        }
        ChatEnvelope notify = new ChatEnvelope();
        notify.setCmd(ChatCmd.TYPING);
        notify.setSid(session.getSessionId());
        notify.setFrom(party(principal));
        notify.setTs(System.currentTimeMillis());
        notify.getBody().put("typing", envelope.getBody().get("typing"));
        String peerKey = peerKey(principal, session);
        if (peerKey != null)
        {
            registry.send(peerKey, notify);
        }
    }

    /**
     * 会话归属校验：访客只能访问自己的会话，客服只能访问自己接待或未分配的会话
     */
    private KfSession resolveSession(ChatPrincipal principal, ChatEnvelope envelope)
    {
        Long sessionId = envelope.getSid();
        if (sessionId == null)
        {
            sessionId = envelope.bodyLong("sessionId");
        }
        if (sessionId == null && principal.isVisitor())
        {
            sessionId = principal.getSessionId();
        }
        if (sessionId == null)
        {
            return null;
        }
        KfSession session = sessionService.getSession(sessionId);
        if (session == null)
        {
            return null;
        }
        if (principal.isVisitor())
        {
            return sessionId.equals(principal.getSessionId()) || principal.getId().equals(session.getVisitorId())
                    ? session : null;
        }
        if (session.getAgentId() == null || session.getAgentId().equals(principal.getId()))
        {
            return session;
        }
        log.warn("[网关路由] 客服 {} 无权操作会话 {}", principal.getId(), sessionId);
        return null;
    }

    private String peerKey(ChatPrincipal principal, KfSession session)
    {
        if (principal.isAgent())
        {
            return ChatPrincipal.key(ChatPrincipal.TYPE_VISITOR, session.getVisitorId());
        }
        Long agentId = session.getAgentId();
        if (agentId == null)
        {
            return null;
        }
        return ChatPrincipal.key(ChatPrincipal.TYPE_AGENT, agentId);
    }

    private ChatEnvelope toPush(KfMessage message, String senderType, Long senderId)
    {
        ChatEnvelope push = new ChatEnvelope();
        push.setCmd(ChatCmd.CHAT);
        push.setMid(String.valueOf(message.getMsgId()));
        push.setSid(message.getSessionId());
        push.setType(message.getMsgType());
        push.setFrom(new ChatEnvelope.Party(partyTypeOfSender(senderType), senderId));
        push.setTs(timeMillis(message.getCreateTime()));
        push.getBody().put("msgId", message.getMsgId());
        push.getBody().put("seq", message.getSeq());
        push.getBody().put("clientMsgId", message.getClientMsgId());
        push.getBody().put("content", message.getContent());
        push.getBody().put("senderType", senderType);
        push.getBody().put("createTime", timeMillis(message.getCreateTime()));
        return push;
    }

    private void ackSend(ChatPrincipal principal, ChatEnvelope envelope, Long sessionId, KfMessage message,
            boolean duplicated)
    {
        ChatEnvelope ack = baseAck(principal, envelope, sessionId);
        ack.setMid(message.getClientMsgId());
        ack.getBody().put("action", "send");
        ack.getBody().put("msgId", message.getMsgId());
        ack.getBody().put("seq", message.getSeq());
        ack.getBody().put("deliverStatus", message.getDeliverStatus());
        ack.getBody().put("duplicated", duplicated);
        ack.getBody().put("createTime", timeMillis(message.getCreateTime()));
        registry.send(principal.key(), ack);
    }

    private ChatEnvelope baseAck(ChatPrincipal principal, ChatEnvelope envelope, Long sessionId)
    {
        ChatEnvelope ack = new ChatEnvelope();
        ack.setCmd(ChatCmd.ACK);
        ack.setSeq(envelope.getSeq());
        ack.setSid(sessionId);
        ack.setFrom(new ChatEnvelope.Party(ChatPrincipal.TYPE_SYSTEM, 0L));
        ack.setTs(System.currentTimeMillis());
        return ack;
    }

    private void sendError(ChatPrincipal principal, ChatEnvelope envelope, int code, String msg)
    {
        ChatEnvelope error = new ChatEnvelope();
        error.setCmd(ChatCmd.ERROR);
        error.setSeq(envelope == null ? null : envelope.getSeq());
        error.setSid(envelope == null ? null : envelope.getSid());
        // 回带被拒那条消息的 clientMsgId：两端都靠它把失败落到具体气泡上。不带的话，客户端只能等
        // 15 秒 ACK 超时把气泡翻成「发送超时」，客服看到的是网络问题，真实原因（超长/会话已结束）
        // 只在一条转瞬即逝的提示里，还会顺手点重发——那条注定再被拒一次。
        error.setMid(envelope == null ? null : envelope.getMid());
        error.setTs(System.currentTimeMillis());
        error.getBody().put("code", code);
        error.getBody().put("msg", msg);
        registry.send(principal.key(), error);
        metrics.onError();
        log.warn("[网关路由] {} 消息处理失败 code={} msg={}", principal.key(), code, msg);
    }

    /** 客户端只能发文本消息：图片/文件的消息类型常量与落库字段都不在本系统内 */
    private String msgType(ChatEnvelope envelope)
    {
        return KfMessage.TYPE_TEXT;
    }

    private String senderType(ChatPrincipal principal)
    {
        return principal.isAgent() ? KfMessage.SENDER_AGENT : KfMessage.SENDER_VISITOR;
    }

    private String partyTypeOfSender(String senderType)
    {
        if (KfMessage.SENDER_AGENT.equals(senderType))
        {
            return ChatPrincipal.TYPE_AGENT;
        }
        if (KfMessage.SENDER_SYSTEM.equals(senderType))
        {
            return ChatPrincipal.TYPE_SYSTEM;
        }
        return ChatPrincipal.TYPE_VISITOR;
    }

    private ChatEnvelope.Party party(ChatPrincipal principal)
    {
        return new ChatEnvelope.Party(principal.getType(), principal.getId());
    }

    private Long timeMillis(Date date)
    {
        return date == null ? System.currentTimeMillis() : date.getTime();
    }

    private boolean isBlank(String value)
    {
        return value == null || value.trim().isEmpty();
    }
}
