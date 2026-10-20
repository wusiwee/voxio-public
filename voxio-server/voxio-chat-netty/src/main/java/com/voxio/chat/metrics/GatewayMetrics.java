package com.voxio.chat.metrics;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.voxio.chat.session.ConnectionRegistry;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;

/**
 * 网关基础指标
 * 
 * 只记录连接数、消息数、错误数、加密拒绝数并定时打印，不引入监控平台。
 * 
 * @author voxio
 */
@Component
@RequiredArgsConstructor
public class GatewayMetrics
{
    private static final Logger log = LoggerFactory.getLogger("kf.gateway.metrics");

    private final ConnectionRegistry registry;

    private final AtomicLong connections = new AtomicLong();

    private final AtomicLong messagesIn = new AtomicLong();

    private final AtomicLong messagesOut = new AtomicLong();

    private final AtomicLong authFailures = new AtomicLong();

    private final AtomicLong rateLimited = new AtomicLong();

    private final AtomicLong errors = new AtomicLong();

    private final AtomicLong offlineStored = new AtomicLong();

    private final AtomicLong cryptoRejected = new AtomicLong();

    private ScheduledExecutorService scheduler;

    @PostConstruct
    public void start()
    {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "kf-gateway-metrics");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(this::report, 60, 60, TimeUnit.SECONDS);
    }

    @PreDestroy
    public void stop()
    {
        if (scheduler != null)
        {
            scheduler.shutdownNow();
        }
    }

    public void onConnect()
    {
        connections.incrementAndGet();
    }

    public void onMessageIn()
    {
        messagesIn.incrementAndGet();
    }

    public void onMessageOut()
    {
        messagesOut.incrementAndGet();
    }

    public void onAuthFailure()
    {
        authFailures.incrementAndGet();
    }

    public void onRateLimited()
    {
        rateLimited.incrementAndGet();
    }

    public void onError()
    {
        errors.incrementAndGet();
    }

    public void onOfflineStored()
    {
        offlineStored.incrementAndGet();
    }

    public void onCryptoRejected()
    {
        cryptoRejected.incrementAndGet();
    }

    public String snapshot()
    {
        return String.format("active=%d total=%d in=%d out=%d authFail=%d limited=%d offline=%d error=%d cryptoRejected=%d",
                registry.activeCount(), connections.get(), messagesIn.get(), messagesOut.get(), authFailures.get(),
                rateLimited.get(), offlineStored.get(), errors.get(), cryptoRejected.get());
    }

    private void report()
    {
        log.info("[网关指标] {}", snapshot());
    }
}
