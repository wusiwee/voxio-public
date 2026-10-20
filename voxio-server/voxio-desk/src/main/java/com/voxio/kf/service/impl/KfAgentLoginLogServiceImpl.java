package com.voxio.kf.service.impl;

import java.util.Date;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.voxio.common.utils.StringUtils;
import com.voxio.kf.domain.KfAgentLoginLog;
import com.voxio.kf.mapper.KfAgentLoginLogMapper;
import com.voxio.kf.service.IKfAgentLoginLogService;

import lombok.RequiredArgsConstructor;

/**
 * 客服登录日志 服务实现
 * 
 * @author voxio
 */
@Service
@RequiredArgsConstructor
public class KfAgentLoginLogServiceImpl implements IKfAgentLoginLogService
{
    private static final Logger log = LoggerFactory.getLogger("kf.security");

    /** 列宽对齐 kf_agent_login_log：账号 30、说明 200、UA 512（留 12 字余量） */
    private static final int MAX_AGENT_NO = 30;

    private static final int MAX_MSG = 200;

    private static final int MAX_UA = 500;

    private final KfAgentLoginLogMapper loginLogMapper;

    @Override
    public void record(String agentNo, Long agentId, String status, String msg, String ip, String ua)
    {
        try
        {
            KfAgentLoginLog row = new KfAgentLoginLog();
            row.setAgentNo(cut(agentNo, MAX_AGENT_NO));
            row.setAgentId(agentId);
            row.setStatus(status);
            row.setMsg(cut(msg, MAX_MSG));
            row.setLoginIp(cut(ip, 50));
            row.setLoginUa(cut(ua, MAX_UA));
            row.setLoginTime(new Date());
            loginLogMapper.insert(row);
        }
        catch (Exception e)
        {
            // 留痕失败不能把登录本身带崩：账号锁定与令牌签发已经走完，这里只丢一条审计记录
            log.warn("客服登录日志写入失败：{}", e.getMessage());
        }
    }

    @Override
    public List<KfAgentLoginLog> selectLogList(String keyword, String status, String begin, String end)
    {
        LambdaQueryWrapper<KfAgentLoginLog> wrapper = Wrappers.lambdaQuery();
        if (StringUtils.isNotEmpty(keyword))
        {
            wrapper.and(w -> w.like(KfAgentLoginLog::getAgentNo, keyword)
                    .or().like(KfAgentLoginLog::getLoginIp, keyword));
        }
        wrapper.eq(StringUtils.isNotEmpty(status), KfAgentLoginLog::getStatus, status);
        wrapper.ge(StringUtils.isNotEmpty(begin), KfAgentLoginLog::getLoginTime, begin);
        wrapper.le(StringUtils.isNotEmpty(end), KfAgentLoginLog::getLoginTime, end);
        wrapper.orderByDesc(KfAgentLoginLog::getLogId);
        return loginLogMapper.selectList(wrapper);
    }

    @Override
    public int cleanBefore(Date deadline)
    {
        LambdaQueryWrapper<KfAgentLoginLog> wrapper = Wrappers.lambdaQuery();
        wrapper.lt(KfAgentLoginLog::getLoginTime, deadline);
        return loginLogMapper.delete(wrapper);
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
