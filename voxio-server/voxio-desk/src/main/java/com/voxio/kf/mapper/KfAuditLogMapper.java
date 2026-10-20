package com.voxio.kf.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.voxio.kf.domain.KfAuditLog;

/**
 * 业务审计日志 数据层
 *
 * 只增不删：这里只继承 BaseMapper 用其 insert 与查询能力，
 * 全仓库不存在针对 kf_audit_log 的 delete/update 语句（门禁 accept-phase10 J17 扫源码断言）。
 *
 * @author voxio
 */
public interface KfAuditLogMapper extends BaseMapper<KfAuditLog>
{
}
