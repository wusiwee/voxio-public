package com.voxio.kf.job;

import java.util.Date;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.voxio.kf.service.IKfAgentLoginLogService;
import com.voxio.kf.service.IKfSecurityService;

import lombok.RequiredArgsConstructor;

/**
 * 客服登录日志保留期清理（阶段10.4）
 *
 * 等保三级要求登录日志留存不少于六个月，所以这里只有「删掉超过保留期的」，没有「按条数截断」。
 * 参数低于 180 天一律按 180 天执行，避免有人在库里把保留期改小把合规底线改掉。
 *
 * @author voxio
 */
@Component
@RequiredArgsConstructor
public class KfLoginLogCleanupJob
{
    private static final Logger log = LoggerFactory.getLogger("kf.security");

    /** 保留天数下限（180 天 ≈ 六个月） */
    private static final int MIN_RETENTION_DAYS = 180;

    private final IKfAgentLoginLogService loginLogService;

    private final IKfSecurityService securityService;

    @Scheduled(cron = "0 30 3 * * ?")
    public void cleanup()
    {
        int days = readRetentionDays();
        Date deadline = new Date(System.currentTimeMillis() - days * 86400000L);
        int removed = loginLogService.cleanBefore(deadline);
        if (removed > 0)
        {
            log.info("客服登录日志清理完成：保留 {} 天，本次删除 {} 条", days, removed);
        }
    }

    /**
     * 当前生效的保留天数（供门禁与排查使用）
     */
    public int readRetentionDays()
    {
        int configured = MIN_RETENTION_DAYS;
        try
        {
            configured = Integer.parseInt(securityService.getConfig("login.log.retention.days",
                    String.valueOf(MIN_RETENTION_DAYS)).trim());
        }
        catch (NumberFormatException e)
        {
            log.warn("login.log.retention.days 不是数字，按 {} 天保留", MIN_RETENTION_DAYS);
            return MIN_RETENTION_DAYS;
        }
        return Math.max(configured, MIN_RETENTION_DAYS);
    }
}
