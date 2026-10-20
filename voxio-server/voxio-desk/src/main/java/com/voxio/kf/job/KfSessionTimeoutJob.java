package com.voxio.kf.job;

import java.util.Date;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.voxio.common.utils.StringUtils;
import com.voxio.kf.domain.KfSession;
import com.voxio.kf.mapper.KfSessionMapper;
import com.voxio.kf.service.IKfConfigService;
import com.voxio.kf.service.IKfSessionService;

import lombok.RequiredArgsConstructor;

/**
 * 会话无活动自动结束
 * 
 * 消费 kf.session.timeout.minutes：排队中/接待中的会话超过该分钟数没有任何消息即以系统身份结束（end_by=3）。
 * 参数为 0 或未配置时不做任何处理；单商户单实例下用 Spring 调度即可，不进若依 quartz。
 * 
 * @author voxio
 */
@Component
@RequiredArgsConstructor
public class KfSessionTimeoutJob
{
    private static final Logger log = LoggerFactory.getLogger("kf.session");

    /** 单次最多结束条数，避免重启后一次性处理全部积压 */
    private static final int BATCH_LIMIT = 50;

    private final KfSessionMapper sessionMapper;

    private final IKfSessionService sessionService;

    private final IKfConfigService configService;

    @Scheduled(initialDelay = 20000, fixedDelay = 60000)
    public void closeIdleSessions()
    {
        int minutes = configService.selectIntByKey("kf.session.timeout.minutes", 0);
        if (minutes <= 0)
        {
            return;
        }
        Date deadline = new Date(System.currentTimeMillis() - minutes * 60000L);
        List<KfSession> idle = sessionMapper.selectIdleSessions(deadline, BATCH_LIMIT);
        if (idle.isEmpty())
        {
            return;
        }
        String reason = StringUtils.format("超过 {} 分钟无活动，系统自动结束", minutes);
        for (KfSession session : idle)
        {
            try
            {
                sessionService.endSession(session.getSessionId(), IKfSessionService.END_BY_SYSTEM, null, reason);
                log.info("[会话超时] 会话 {} 无活动超过 {} 分钟，已自动结束", session.getSessionNo(), minutes);
            }
            catch (Exception e)
            {
                log.warn("[会话超时] 会话 {} 自动结束失败：{}", session.getSessionNo(), e.getMessage());
            }
        }
    }
}
