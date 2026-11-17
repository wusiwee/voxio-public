package com.voxio.chat.server;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import com.voxio.chat.codec.TcpCodec;
import com.voxio.chat.codec.WebSocketCodec;
import com.voxio.chat.config.ChatGatewayProperties;
import com.voxio.chat.crypto.ChatCryptoService;
import com.voxio.chat.dispatch.ChatMessageDispatcher;
import com.voxio.chat.handler.ChatConnectionHandler;
import com.voxio.chat.metrics.GatewayMetrics;
import com.voxio.chat.session.ChatAuthService;
import com.voxio.chat.session.ConnectionRegistry;
import com.voxio.kf.service.IKfConfigService;
import com.voxio.kf.service.IKfSessionService;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.util.concurrent.EventExecutorGroup;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.util.concurrent.DefaultEventExecutorGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.timeout.IdleStateHandler;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;

/**
 * 聊天网关进程内启动器：同一 JVM 内监听 WebSocket（浏览器接入）与 TCP（原生客户端接入）
 * 
 * @author voxio
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "kf.gateway", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ChatServer implements SmartLifecycle
{
    private static final Logger log = LoggerFactory.getLogger("kf.gateway");

    /** 访客消息上限低于这个值就提醒一次：多半是验收/压测改小忘了还原，被误伤的是真实访客 */
    private static final int LOW_RATE_WARN_BELOW = 10;

    private final AtomicBoolean lowRateWarned = new AtomicBoolean();

    private final ChatGatewayProperties properties;

    private final ConnectionRegistry registry;

    private final GatewayMetrics metrics;

    private final IKfSessionService sessionService;

    private final ChatMessageDispatcher dispatcher;

    private final ChatAuthService authService;

    private final ChatCryptoService cryptoService;

    private final IKfConfigService configService;

    private volatile boolean running;

    private EventLoopGroup bossGroup;

    private EventLoopGroup workerGroup;

    /**
     * 业务线程组：消息处理要访问 MySQL/Redis，放到 IO 线程之外执行；
     * 同一条连接固定落在同一个业务线程上，保证同连接消息不乱序。
     */
    private EventExecutorGroup bizGroup;

    private Channel tcpChannel;

    private Channel wsChannel;

    @Override
    public void start()
    {
        if (running)
        {
            return;
        }
        int workers = Math.max(2, Runtime.getRuntime().availableProcessors());
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup(workers);
        bizGroup = new DefaultEventExecutorGroup(Math.max(1, properties.getBizThreads()));
        try
        {
            tcpChannel = bind("tcp", new ChannelInitializer<SocketChannel>()
            {
                @Override
                protected void initChannel(SocketChannel ch)
                {
                    ch.pipeline().addLast(new IdleStateHandler(properties.getReaderIdleSeconds(), 0, 0,
                            TimeUnit.SECONDS));
                    ch.pipeline().addLast(new io.netty.handler.codec.LengthFieldBasedFrameDecoder(
                            properties.getMaxFrameBytes(), 0, 4, 0, 4));
                    ch.pipeline().addLast(new TcpCodec.Decoder(), new TcpCodec.Encoder());
                    ch.pipeline().addLast(bizGroup, newHandler());
                }
            }, properties.getTcpPort());
            wsChannel = bind("ws", new ChannelInitializer<SocketChannel>()
            {
                @Override
                protected void initChannel(SocketChannel ch)
                {
                    ch.pipeline().addLast(new HttpServerCodec());
                    ch.pipeline().addLast(new HttpObjectAggregator(properties.getMaxFrameBytes()));
                    ch.pipeline().addLast(new WebSocketServerProtocolHandler(properties.getWsPath(), null, true,
                            properties.getMaxFrameBytes()));
                    ch.pipeline().addLast(new IdleStateHandler(properties.getReaderIdleSeconds(), 0, 0,
                            TimeUnit.SECONDS));
                    ch.pipeline().addLast(new WebSocketCodec.Decoder(), new WebSocketCodec.Encoder());
                    ch.pipeline().addLast(bizGroup, newHandler());
                }
            }, properties.getWsPort());
            running = true;
            log.info("[网关启动] WebSocket ws://0.0.0.0:{}{}  TCP 0.0.0.0:{}  心跳读空闲 {}s  限流 访客{}条/分钟 客服{}条/分钟",
                    properties.getWsPort(), properties.getWsPath(), properties.getTcpPort(),
                    properties.getReaderIdleSeconds(), properties.getVisitorRatePerMinute(),
                    properties.getAgentRatePerMinute());
        }
        catch (Exception e)
        {
            shutdownGroups();
            throw new IllegalStateException("聊天网关启动失败：" + e.getMessage(), e);
        }
    }

    private Channel bind(String name, ChannelInitializer<SocketChannel> initializer, int port) throws InterruptedException
    {
        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .option(ChannelOption.SO_BACKLOG, 1024)
                .childOption(ChannelOption.SO_KEEPALIVE, true)
                .childOption(ChannelOption.TCP_NODELAY, true)
                .childHandler(initializer);
        Channel channel = bootstrap.bind(port).sync().channel();
        log.info("[网关启动] {} 监听端口 {} 就绪", name, port);
        return channel;
    }

    private ChatConnectionHandler newHandler()
    {
        // 访客限流以库内参数 kf.rate.limit.perMinute 为准（参数配置/业务设置可改），网关配置项只作兜底
        int visitorRate = configService.selectIntByKey("kf.rate.limit.perMinute", 0);
        if (visitorRate <= 0)
        {
            visitorRate = properties.getVisitorRatePerMinute();
        }
        // 每条连接建好时读一次参数，所以这个值一旦被改小（压测/验收临时改过忘了还原），线上访客会立刻
        // 「发两句就提示消息发送过快」。它又不在访客端公开配置里，肉眼查不到，只能在网关侧留一句话。
        if (visitorRate < LOW_RATE_WARN_BELOW && lowRateWarned.compareAndSet(false, true))
        {
            log.warn("[网关限流] 访客消息上限 kf.rate.limit.perMinute={} 已低于正常聊水量级（交付默认 20 条/分钟），"
                    + "真实访客大概率会被误伤。改回后需刷新参数缓存，且只对新建连接生效（访客要重进会话）", visitorRate);
        }
        return new ChatConnectionHandler(registry, authService, dispatcher, metrics, sessionService, cryptoService,
                properties.getAuthTimeoutSeconds(), properties.getReaderIdleSeconds(),
                properties.getAgentRatePerMinute(), visitorRate);
    }

    @Override
    public void stop()
    {
        running = false;
        closeChannels();
        shutdownGroups();
        log.info("[网关停止] 已释放监听端口，最终指标 {}", metrics.snapshot());
    }

    @PreDestroy
    public void destroy()
    {
        if (running)
        {
            stop();
        }
    }

    private void closeChannels()
    {
        if (tcpChannel != null)
        {
            tcpChannel.close().syncUninterruptibly();
            tcpChannel = null;
        }
        if (wsChannel != null)
        {
            wsChannel.close().syncUninterruptibly();
            wsChannel = null;
        }
    }

    private void shutdownGroups()
    {
        if (bossGroup != null)
        {
            bossGroup.shutdownGracefully();
            bossGroup = null;
        }
        if (workerGroup != null)
        {
            workerGroup.shutdownGracefully();
            workerGroup = null;
        }
        if (bizGroup != null)
        {
            bizGroup.shutdownGracefully();
            bizGroup = null;
        }
    }

    @Override
    public void stop(Runnable callback)
    {
        stop();
        callback.run();
    }

    @Override
    public boolean isRunning()
    {
        return running;
    }

    @Override
    public boolean isAutoStartup()
    {
        return true;
    }

    /**
     * 晚于 Web 容器启动，保证业务服务与连接池已就绪
     */
    @Override
    public int getPhase()
    {
        return Integer.MAX_VALUE - 1000;
    }
}
