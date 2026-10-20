package com.voxio.kf.service;

import java.util.Date;
import java.util.List;

import com.voxio.kf.domain.KfAuditLog;

/**
 * 业务审计日志 服务接口
 *
 * 只有「写一条」和「查一批」两个能力，外加只给定时任务用的按保留期清理；
 * 没有任何面向接口的删除或修改入口。
 *
 * @author voxio
 */
public interface IKfAuditLogService
{
    /**
     * 记一条审计留痕。写失败只告警，不影响业务本身（留痕不能变成可用性故障）
     *
     * @param auditLog 审计记录
     */
    void record(KfAuditLog auditLog);

    /**
     * 查询审计留痕（管理后台，只读）
     *
     * @param keyword 操作人ID / 对象标识 / IP 关键字
     * @param userType 操作人类型（admin / agent）
     * @param operation 操作动作模糊匹配
     * @param begin 起始时间（含时分秒）
     * @param end 截止时间（含时分秒）
     * @return 审计记录，按时间倒序
     */
    List<KfAuditLog> selectLogList(String keyword, String userType, String operation, String begin, String end);

    /**
     * 清理早于保留期的留痕，仅供 KfAuditRetentionJob 调用
     *
     * @param deadline 截止时间
     * @return 删除条数
     */
    int cleanBefore(Date deadline);
}
