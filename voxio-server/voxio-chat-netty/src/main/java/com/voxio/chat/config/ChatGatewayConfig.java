package com.voxio.chat.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.voxio.chat.session.ChatAuthService;
import com.voxio.kf.service.IKfAgentTokenService;
import com.voxio.kf.service.IKfVisitorTokenService;

/**
 * 网关内部组件装配：仅在 kf.gateway.enabled=true 时生效
 * 
 * @author voxio
 */
@Configuration
@ConditionalOnProperty(prefix = "kf.gateway", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ChatGatewayConfig
{
    @Bean
    public ChatAuthService chatAuthService(IKfAgentTokenService agentTokenService,
            IKfVisitorTokenService visitorTokenService)
    {
        return new ChatAuthService(agentTokenService, visitorTokenService);
    }
}
