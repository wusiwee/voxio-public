package com.voxio.web.core.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.voxio.common.config.VoxioConfig;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import lombok.RequiredArgsConstructor;

/**
 * Swagger2的接口配置
 * 
 * @author voxio
 */
@Configuration
@RequiredArgsConstructor
public class SwaggerConfig
{
    /** 系统基础配置 */
    private final VoxioConfig voxioConfig;
    
    /**
     * 自定义的 OpenAPI 对象
     */
    @Bean
    public OpenAPI customOpenApi()
    {
        return new OpenAPI().components(new Components()
            // 设置认证的请求头
            .addSecuritySchemes("apikey", securityScheme()))
            .addSecurityItem(new SecurityRequirement().addList("apikey"))
            .info(getApiInfo());
    }
    
    @Bean
    public SecurityScheme securityScheme()
    {
        return new SecurityScheme()
            .type(SecurityScheme.Type.APIKEY)
            .name("Authorization")
            .in(SecurityScheme.In.HEADER)
            .scheme("Bearer");
    }
    
    /**
     * 添加摘要信息
     */
    public Info getApiInfo()
    {
        return new Info()
            // 设置标题
            .title("VOXIO 在线客服系统 · 接口文档")
            // 描述
            .description("单商户自部署的在线客服系统服务端接口：访客接入、会话与消息、工单与配置。")
            // 作者信息
            .contact(new Contact().name(voxioConfig.getName()))
            // 版本
            .version("版本号:" + voxioConfig.getVersion());
    }
}
