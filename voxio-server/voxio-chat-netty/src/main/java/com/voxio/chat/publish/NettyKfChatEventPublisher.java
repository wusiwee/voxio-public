package com.voxio.chat.publish;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import com.voxio.chat.metrics.GatewayMetrics;
import com.voxio.chat.protocol.ChatEnvelope;
import com.voxio.chat.protocol.ChatPrincipal;
import com.voxio.chat.session.ConnectionRegistry;
import com.voxio.kf.event.KfChatEvent;
import com.voxio.kf.event.KfChatEventPublisher;

import lombok.RequiredArgsConstructor;

/**
 * 业务事件推送实现：把 voxio-desk 产生的会话事件写入对应连接
 * 
 * 声明为 @Primary，保证网关模块存在时业务服务注入的是本实现而非 kf 侧兜底空实现。
 * 
 * @author voxio
 */
@Primary
@Component
@RequiredArgsConstructor
public class NettyKfChatEventPublisher implements KfChatEventPublisher
{
    private final ConnectionRegistry registry;

    private final GatewayMetrics metrics;

    @Override
    public void publishToAgent(Long agentId, KfChatEvent event)
    {
        send(ChatPrincipal.key(ChatPrincipal.TYPE_AGENT, agentId), event);
    }

    @Override
    public void publishToVisitor(Long visitorId, KfChatEvent event)
    {
        send(ChatPrincipal.key(ChatPrincipal.TYPE_VISITOR, visitorId), event);
    }

    @Override
    public boolean isAgentOnline(Long agentId)
    {
        return registry.isAgentOnline(agentId);
    }

    @Override
    public boolean isVisitorOnline(Long visitorId)
    {
        return registry.isVisitorOnline(visitorId);
    }

    private void send(String key, KfChatEvent event)
    {
        ChatEnvelope envelope = new ChatEnvelope();
        envelope.setCmd(event.getCmd());
        envelope.setSid(event.getSessionId());
        envelope.setFrom(new ChatEnvelope.Party(ChatPrincipal.TYPE_SYSTEM, 0L));
        envelope.setTs(System.currentTimeMillis());
        envelope.getBody().putAll(event.getPayload());
        if (registry.send(key, envelope))
        {
            metrics.onMessageOut();
        }
    }
}
