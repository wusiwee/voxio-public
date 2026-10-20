package com.voxio.chat.dispatch;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import com.voxio.chat.config.ChatGatewayProperties;
import com.voxio.kf.service.IKfMessageService;

import jakarta.annotation.PreDestroy;

/**
 * 投递之后那条落库任务的本地队列（阶段11 功能点11：先投递后落库，失败进本地重试队列，不引入 MQ）。
 *
 * 异步范围刻意只圈住「送达状态」（deliver_status 0→1）：消息行本身与会话序号仍在 ACK 之前同步写完 ——
 * 阶段10 的 (session_id, seq) 唯一约束、client_msg_id 幂等判断和 SYNC 补发游标都以库里真实序号为准，
 * 把序号分配挪到异步就会破坏「不重复、不乱序」这条验收底线。
 *
 * 三条实现约束：
 * 1. 同一会话的两次状态翻转保持先后 → 按 sessionId 哈希绑固定单线程通道，同会话串行、跨会话并行；
 * 2. 队列有界，写满就退回调用线程当场执行 —— 宁可这一条慢，不能把状态丢了；
 * 3. 失败退避重试（默认 200/400/800ms），仍失败记 ERROR（带会话与发送方，可人工补录）并计数。
 *
 * @author voxio
 */
@Component
public class PostDeliveryWriter
{
    private static final Logger log = LoggerFactory.getLogger("kf.gateway");

    /** 通道数：单机场景 4 条足够，同时限制住这条路径占用的数据库连接数 */
    private static final int LANES = 4;

    private final ThreadPoolExecutor[] lanes = new ThreadPoolExecutor[LANES];

    private final IKfMessageService messageService;

    private final int retryTimes;

    private final long retryDelayMs;

    private final AtomicLong executed = new AtomicLong();

    private final AtomicLong inlineFallback = new AtomicLong();

    private final AtomicLong givenUp = new AtomicLong();

    public PostDeliveryWriter(IKfMessageService messageService,
            ObjectProvider<ChatGatewayProperties> propertiesProvider)
    {
        this.messageService = messageService;
        ChatGatewayProperties properties = propertiesProvider.getIfAvailable();
        int capacity = properties == null ? 2000 : properties.getPersistQueueCapacity();
        this.retryTimes = properties == null ? 3 : properties.getPersistRetryTimes();
        this.retryDelayMs = properties == null ? 200L : properties.getPersistRetryDelayMs();
        for (int i = 0; i < LANES; i++)
        {
            // 单线程 + 有界队列：队列满时 execute 抛 RejectedExecutionException，由 submitMarkSent 退回当场执行
            lanes[i] = new ThreadPoolExecutor(1, 1, 30L, TimeUnit.SECONDS, new ArrayBlockingQueue<>(capacity));
            lanes[i].allowCoreThreadTimeOut(true);
        }
    }

    /**
     * 投递成功后登记「该会话中本发送方的消息置为已送达」
     *
     * @param sessionId 会话ID
     * @param senderType 本条消息的发送方类型
     */
    public void submitMarkSent(Long sessionId, String senderType)
    {
        Runnable task = () -> runWithRetry(sessionId, senderType);
        try
        {
            lanes[laneOf(sessionId)].execute(task);
        }
        catch (RejectedExecutionException e)
        {
            // 队列写满：当场同步执行，保证状态不丢（此时性能退化为改造前的行为，属于预期内的兜底）
            inlineFallback.incrementAndGet();
            task.run();
        }
    }

    /** 观察用计数：异步执行 / 队列满退化为同步 / 重试耗尽，随网关指标一起打点 */
    public String statistics()
    {
        return "送达状态异步落库 " + executed.get() + " 次，队列满退化为同步 " + inlineFallback.get()
                + " 次，重试耗尽 " + givenUp.get() + " 次";
    }

    private void runWithRetry(Long sessionId, String senderType)
    {
        long delay = retryDelayMs;
        for (int attempt = 1; attempt <= Math.max(1, retryTimes); attempt++)
        {
            try
            {
                messageService.markSent(sessionId, senderType);
                executed.incrementAndGet();
                return;
            }
            catch (Exception e)
            {
                if (attempt >= retryTimes)
                {
                    givenUp.incrementAndGet();
                    log.error("[送达状态落库] 会话 {} 发送方 {} 重试 {} 次仍失败，需人工核对 deliver_status：{}",
                            sessionId, senderType, attempt, e.getMessage());
                    return;
                }
                log.warn("[送达状态落库] 会话 {} 第 {} 次失败（{}），{}ms 后重试", sessionId, attempt, e.getMessage(), delay);
                try
                {
                    Thread.sleep(delay);
                }
                catch (InterruptedException ie)
                {
                    Thread.currentThread().interrupt();
                    return;
                }
                delay = delay * 2;
            }
        }
    }

    /** 同一会话固定同一条通道，保证先后顺序 */
    private static int laneOf(Long sessionId)
    {
        long sid = sessionId == null ? 0L : sessionId;
        return (int) Math.floorMod(sid * 31, LANES);
    }

    /**
     * 停机前把已排队的状态写入跑完，避免正常重启留下「已送达但库里仍标未送达」的行
     */
    @PreDestroy
    public void shutdown()
    {
        for (ThreadPoolExecutor lane : lanes)
        {
            lane.shutdown();
        }
        for (ThreadPoolExecutor lane : lanes)
        {
            try
            {
                if (!lane.awaitTermination(5, TimeUnit.SECONDS))
                {
                    lane.shutdownNow();
                }
            }
            catch (InterruptedException e)
            {
                Thread.currentThread().interrupt();
                lane.shutdownNow();
            }
        }
    }
}
