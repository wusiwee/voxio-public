package com.voxio.chat.session;

/**
 * 单连接简单限流（固定窗口计数）
 * 
 * @author voxio
 */
public class SimpleRateLimiter
{
    private final int limitPerMinute;

    private long windowStart;

    private int count;

    public SimpleRateLimiter(int limitPerMinute)
    {
        this.limitPerMinute = limitPerMinute;
        this.windowStart = System.currentTimeMillis();
    }

    /**
     * 申请一个配额
     * 
     * @return 是否放行
     */
    public synchronized boolean tryAcquire()
    {
        long now = System.currentTimeMillis();
        if (now - windowStart >= 60_000L)
        {
            windowStart = now;
            count = 0;
        }
        if (count >= limitPerMinute)
        {
            return false;
        }
        count++;
        return true;
    }

    /**
     * 当前窗口还剩多少毫秒才放行（配额耗尽时才有意义）
     *
     * 客户端拿到这个值就能「等一会儿自动补发」，而不是让用户反复点发送、每次都再撞一次限流。
     *
     * @return 剩余毫秒，窗口已结束则返回 0
     */
    public synchronized long remainingMillis()
    {
        long elapsed = System.currentTimeMillis() - windowStart;
        return elapsed >= 60_000L ? 0L : 60_000L - elapsed;
    }
}
