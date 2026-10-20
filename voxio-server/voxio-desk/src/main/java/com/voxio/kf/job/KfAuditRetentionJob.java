package com.voxio.kf.job;

import java.util.Date;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.voxio.kf.mapper.KfAuditRetentionMapper;
import com.voxio.kf.service.IKfAuditLogService;
import com.voxio.kf.service.IKfSecurityService;

import lombok.RequiredArgsConstructor;

/**
 * 审计留痕留存期清理（阶段10.5 P0-3）
 *
 * 与客服登录日志同一套口径：等保三级要求审计日志留存不少于六个月，所以这里只有「删掉超过保留期的」，
 * 没有「人工清空」——操作日志与登录日志的删除/清空 HTTP 入口已在本阶段摘除，应用侧不再提供抹除手段。
 * 参数低于 180 天一律按 180 天执行，避免有人在库里把保留期改小把合规底线改掉。
 *
 * @author voxio
 */
@Component
@RequiredArgsConstructor
public class KfAuditRetentionJob
{
    private static final Logger log = LoggerFactory.getLogger("kf.security");

    /** 留存天数下限（180 天 ≈ 六个月） */
    private static final int MIN_RETENTION_DAYS = 180;

    private final KfAuditRetentionMapper retentionMapper;

    private final IKfAuditLogService auditLogService;

    private final IKfSecurityService securityService;

    @Scheduled(cron = "0 40 3 * * ?")
    public void cleanup()
    {
        int days = readRetentionDays();
        Date deadline = new Date(System.currentTimeMillis() - days * 86400000L);
        int oper = retentionMapper.deleteOperLogBefore(deadline);
        int login = retentionMapper.deleteLogininforBefore(deadline);
        int audit = auditLogService.cleanBefore(deadline);
        if (oper > 0 || login > 0 || audit > 0)
        {
            log.info("审计留痕清理完成：保留 {} 天，操作日志 {} 条，登录日志 {} 条，业务审计 {} 条", days, oper, login, audit);
        }
    }

    /**
     * 当前生效的留存天数（供门禁与排查使用）
     */
    public int readRetentionDays()
    {
        int configured = MIN_RETENTION_DAYS;
        try
        {
            configured = Integer.parseInt(securityService.getConfig("audit.log.retention.days",
                    String.valueOf(MIN_RETENTION_DAYS)).trim());
        }
        catch (NumberFormatException e)
        {
            log.warn("audit.log.retention.days 不是数字，按 {} 天保留", MIN_RETENTION_DAYS);
            return MIN_RETENTION_DAYS;
        }
        return Math.max(configured, MIN_RETENTION_DAYS);
    }
}
