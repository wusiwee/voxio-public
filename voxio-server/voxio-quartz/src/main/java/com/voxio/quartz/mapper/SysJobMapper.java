package com.voxio.quartz.mapper;

import java.util.List;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.voxio.quartz.domain.SysJob;

/**
 * 调度任务信息 数据层
 * 
 * @author voxio
 */
public interface SysJobMapper extends BaseMapper<SysJob>
{
    /**
     * 查询调度任务日志集合（多条件动态检索，保留自定义 SQL）
     * 
     * @param job 调度信息
     * @return 操作日志集合
     */
    public List<SysJob> selectJobList(SysJob job);
}
