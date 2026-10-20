package com.voxio.chat.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

/**
 * 网关配置（application.yml 中 kf.gateway.* 段）
 * 
 * @author voxio
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "kf.gateway")
public class ChatGatewayProperties
{
    /** 是否启用网关 */
    private boolean enabled = true;

    /** WebSocket 监听端口（浏览器接入） */
    private int wsPort = 10097;

    /** TCP 监听端口（原生客户端接入） */
    private int tcpPort = 10098;

    /** WebSocket 握手路径 */
    private String wsPath = "/ws";

    /** 读空闲多少秒后判定掉线并断开 */
    private int readerIdleSeconds = 90;

    /** 单帧最大字节数 */
    private int maxFrameBytes = 65536;

    /** 鉴权前等待 AUTH 的超时秒数 */
    private int authTimeoutSeconds = 10;

    /** 访客每分钟消息上限 */
    private int visitorRatePerMinute = 20;

    /** 客服每分钟消息上限 */
    private int agentRatePerMinute = 60;

    /** 单次 SYNC 最大补发条数 */
    private int syncLimit = 200;

    /** 单条文本消息最大字符数 */
    private int maxContentLength = 2000;

    /**
     * 业务线程数：消息处理包含数据库往返，线程数越大吞吐越高，
     * 上限不应超过 Druid maxActive，否则多出来只是在连接池排队。
     */
    private int bizThreads = 16;

    /**
     * 投递完成后的状态写入是否走本地队列（阶段11）。
     *
     * true（交付默认）：先投递再翻转 deliver_status，落库失败进本地重试队列；
     * false：改造前的同步行为，留着给排障对照与验收脚本按需要回退。
     */
    private boolean persistAsync = true;

    /** 状态写入队列的容量（每条通道各自计算），写满退回同步执行 */
    private int persistQueueCapacity = 2000;

    /** 状态写入失败重试次数 */
    private int persistRetryTimes = 3;

    /** 重试基础间隔（毫秒），按 2 倍退避 */
    private long persistRetryDelayMs = 200L;
}
