package com.voxio.quartz.mapper;

import java.util.List;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.voxio.quartz.domain.SysJobLog;

/**
 * 调度任务日志信息 数据层
 * 
 * @author voxio
 */
public interface SysJobLogMapper extends BaseMapper<SysJobLog>
{
    /**
     * 获取quartz调度器日志的计划任务（多条件动态检索+排序，保留自定义 SQL）
     * 
     * @param jobLog 调度日志信息
     * @return 调度任务日志集合
     */
    public List<SysJobLog> selectJobLogList(SysJobLog jobLog);

    /**
     * 清空任务日志（truncate，保留自定义 SQL）
     */
    public void cleanJobLog();
}
