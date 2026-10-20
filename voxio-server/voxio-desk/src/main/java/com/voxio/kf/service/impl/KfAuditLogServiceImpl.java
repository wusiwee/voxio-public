package com.voxio.kf.service.impl;

import java.util.Date;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.voxio.common.utils.StringUtils;
import com.voxio.kf.domain.KfAuditLog;
import com.voxio.kf.mapper.KfAuditLogMapper;
import com.voxio.kf.service.IKfAuditLogService;

import lombok.RequiredArgsConstructor;

/**
 * 业务审计日志 服务实现
 *
 * @author voxio
 */
@Service
@RequiredArgsConstructor
public class KfAuditLogServiceImpl implements IKfAuditLogService
{
    private static final Logger log = LoggerFactory.getLogger("kf.security");

    /** 列宽对齐 kf_audit_log：动作 100、对象类型 50、对象标识 64、IP 50、UA 512、结果 200 */
    private static final int MAX_OPERATION = 100;

    private static final int MAX_TARGET_TYPE = 50;

    private static final int MAX_TARGET_ID = 64;

    private static final int MAX_RESULT = 200;

    private static final int MAX_UA = 500;

    private final KfAuditLogMapper auditLogMapper;

    @Override
    public void record(KfAuditLog row)
    {
        try
        {
            row.setOperation(cut(row.getOperation(), MAX_OPERATION));
            row.setTargetType(cut(row.getTargetType(), MAX_TARGET_TYPE));
            row.setTargetId(cut(row.getTargetId(), MAX_TARGET_ID));
            row.setIp(cut(row.getIp(), 50));
            row.setUa(cut(row.getUa(), MAX_UA));
            row.setResult(cut(row.getResult(), MAX_RESULT));
            if (StringUtils.isNotEmpty(row.getRequestParams()) && row.getRequestParams().length() > 2000)
            {
                row.setRequestParams(row.getRequestParams().substring(0, 2000));
            }
            if (row.getCreateTime() == null)
            {
                row.setCreateTime(new Date());
            }
            auditLogMapper.insert(row);
        }
        catch (Exception e)
        {
            // 留痕写失败不能把业务本身带崩；这里丢一条审计，本地日志必须留下线索
            log.warn("业务审计留痕写入失败：{}", e.getMessage());
        }
    }

    @Override
    public List<KfAuditLog> selectLogList(String keyword, String userType, String operation, String begin, String end)
    {
        LambdaQueryWrapper<KfAuditLog> wrapper = Wrappers.lambdaQuery();
        if (StringUtils.isNotEmpty(keyword))
        {
            wrapper.and(w -> w.like(KfAuditLog::getTargetId, keyword)
                    .or().like(KfAuditLog::getIp, keyword)
                    .or().like(KfAuditLog::getUserId, keyword));
        }
        wrapper.eq(StringUtils.isNotEmpty(userType), KfAuditLog::getUserType, userType);
        wrapper.like(StringUtils.isNotEmpty(operation), KfAuditLog::getOperation, operation);
        wrapper.ge(StringUtils.isNotEmpty(begin), KfAuditLog::getCreateTime, begin);
        wrapper.le(StringUtils.isNotEmpty(end), KfAuditLog::getCreateTime, end);
        wrapper.orderByDesc(KfAuditLog::getLogId);
        return auditLogMapper.selectList(wrapper);
    }

    @Override
    public int cleanBefore(Date deadline)
    {
        // 仅供 KfAuditRetentionJob 调用：只删超过保留期的，且保留期下限 180 天由任务侧兜住
        LambdaQueryWrapper<KfAuditLog> wrapper = Wrappers.lambdaQuery();
        wrapper.lt(KfAuditLog::getCreateTime, deadline);
        return auditLogMapper.delete(wrapper);
    }

    private String cut(String value, int max)
    {
        if (StringUtils.isEmpty(value))
        {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
