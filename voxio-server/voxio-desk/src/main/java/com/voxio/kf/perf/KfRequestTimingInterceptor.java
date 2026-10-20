package com.voxio.kf.perf;

import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 接口耗时埋点（阶段11 功能点 7）
 *
 * 超过阈值的请求记一条 WARN，字段含方法、URI、服务端耗时与状态码；
 * 埋点自身只有一次 nanoTime 与一次比较，未超阈值不产生任何日志行。
 *
 * 阈值默认 500ms，可用 {@code kf.perf.slow-request-ms} 调整，配合 Druid 的 slow-sql 日志
 * 能把「慢」定位到接口整体还是某条 SQL。
 *
 * 刻意不把耗时写进响应头：JSON 由消息转换器写出时会 flush 缓冲区，响应在 afterCompletion
 * 之前就已提交，那时设的头传不出去；而为了让一个观测头改成包装整个输出流，代价大于收益。
 *
 * @author voxio
 */
public class KfRequestTimingInterceptor implements HandlerInterceptor
{
    private static final Logger log = LoggerFactory.getLogger("kf-perf");

    /** 请求起始时间（纳秒）在属性里的键 */
    private static final String START_ATTR = "kf.request.start.nanoTime";

    /** 超过该毫秒数记一条慢接口日志 */
    private final long slowThresholdMs;

    public KfRequestTimingInterceptor(long slowThresholdMs)
    {
        this.slowThresholdMs = slowThresholdMs;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
    {
        request.setAttribute(START_ATTR, System.nanoTime());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex)
    {
        Object start = request.getAttribute(START_ATTR);
        if (!(start instanceof Long startNanos))
        {
            return;
        }
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
        if (elapsedMs >= slowThresholdMs)
        {
            log.warn("慢接口 {} {} 服务端耗时 {}ms（阈值 {}ms，状态 {}）", request.getMethod(), request.getRequestURI(),
                    elapsedMs, slowThresholdMs, response.getStatus());
        }
    }
}
