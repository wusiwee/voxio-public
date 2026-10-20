package com.voxio.kf.mapper;

import java.util.Date;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

/**
 * 审计留痕留存期数据层（阶段10.5 P0-3）
 *
 * 只有一条按时间删除的语句，且唯一调用方是 KfAuditRetentionJob。放在 kf 模块是因为
 * 全项目的 @Scheduled 基座（KfScheduleConfig）在这里，而 voxio-desk 不依赖 voxio-system，
 * 为此给 kf 加一条模块依赖不值得；两张 sys_* 日志表按 oper_time / login_time 归档，不读不改。
 *
 * @author voxio
 */
public interface KfAuditRetentionMapper
{
    @Delete("delete from sys_oper_log where oper_time < #{deadline}")
    int deleteOperLogBefore(@Param("deadline") Date deadline);

    @Delete("delete from sys_logininfor where login_time < #{deadline}")
    int deleteLogininforBefore(@Param("deadline") Date deadline);
}
