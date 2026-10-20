package com.voxio.kf.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.voxio.kf.perf.KfRequestTimingInterceptor;
import com.voxio.kf.security.KfAgentAuthInterceptor;
import com.voxio.kf.service.IKfAgentTokenService;

import lombok.RequiredArgsConstructor;

/**
 * 客服模块 Web 配置：注册客服端令牌拦截器
 * 
 * @author voxio
 */
@Configuration
@RequiredArgsConstructor
public class KfWebMvcConfig implements WebMvcConfigurer
{
    private final IKfAgentTokenService agentTokenService;

    /** 慢接口阈值（毫秒），阶段11 功能点7 */
    @Value("${kf.perf.slow-request-ms:500}")
    private long slowRequestMs;

    @Override
    public void addInterceptors(InterceptorRegistry registry)
    {
        registry.addInterceptor(new KfAgentAuthInterceptor(agentTokenService)).addPathPatterns("/kf/agent-app/**");
        // 耗时埋点覆盖全部客服接口；放在鉴权拦截器之后，慢日志里不会混进握手前的排队时间
        registry.addInterceptor(new KfRequestTimingInterceptor(slowRequestMs)).addPathPatterns("/kf/**");
    }
}
