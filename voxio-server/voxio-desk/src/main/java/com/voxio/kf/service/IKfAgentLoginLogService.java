package com.voxio.kf.service;

import java.util.Date;
import java.util.List;

import com.voxio.kf.domain.KfAgentLoginLog;

/**
 * 客服登录日志 服务层
 * 
 * @author voxio
 */
public interface IKfAgentLoginLogService
{
    /**
     * 记一条登录结果。写日志本身失败绝不能把登录带崩，因此内部吞掉异常只告警。
     *
     * @param agentNo 登录账号（账号不存在时记尝试值）
     * @param agentId 客服ID，账号不存在时传 null
     * @param status  {@link KfAgentLoginLog#STATUS_SUCCESS} / {@link KfAgentLoginLog#STATUS_FAIL}
     * @param msg     结果说明
     * @param ip      登录IP
     * @param ua      客户端标识
     */
    void record(String agentNo, Long agentId, String status, String msg, String ip, String ua);

    /**
     * 登录日志列表（管理后台只读）
     *
     * @param keyword 账号或IP模糊
     * @param status  登录结果，空为全部
     * @param begin   起始时间（yyyy-MM-dd HH:mm:ss，由 KfQuerySupport 补齐时分秒）
     * @param end     截止时间
     * @return 按时间倒序的日志
     */
    List<KfAgentLoginLog> selectLogList(String keyword, String status, String begin, String end);

    /**
     * 删除指定时间之前的日志
     *
     * @param deadline 早于该时间的记录被清理
     * @return 清理条数
     */
    int cleanBefore(Date deadline);
}
