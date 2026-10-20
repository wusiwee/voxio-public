package com.voxio.kf.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 事件投递器缺省实现
 * 
 * 未引入 voxio-chat-netty 模块时（例如仅跑管理后台），提供空实现保证业务层可注入。
 * 
 * @author voxio
 */
@Configuration
public class KfEventFallbackConfig
{
    private static final Logger log = LoggerFactory.getLogger(KfEventFallbackConfig.class);

    @Bean
    @ConditionalOnMissingBean(KfChatEventPublisher.class)
    public KfChatEventPublisher noopKfChatEventPublisher()
    {
        return new KfChatEventPublisher()
        {
            @Override
            public void publishToAgent(Long agentId, KfChatEvent event)
            {
                log.debug("网关未启用，丢弃事件 {} -> agent:{}", event.getCmd(), agentId);
            }

            @Override
            public void publishToVisitor(Long visitorId, KfChatEvent event)
            {
                log.debug("网关未启用，丢弃事件 {} -> visitor:{}", event.getCmd(), visitorId);
            }

            @Override
            public boolean isAgentOnline(Long agentId)
            {
                return false;
            }

            @Override
            public boolean isVisitorOnline(Long visitorId)
            {
                return false;
            }
        };
    }
}
