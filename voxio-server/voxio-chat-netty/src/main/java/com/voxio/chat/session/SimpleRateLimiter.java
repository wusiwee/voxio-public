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
}
