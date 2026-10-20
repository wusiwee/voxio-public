package com.voxio.system.mapper;

import java.util.List;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.voxio.system.domain.SysOperLog;

/**
 * 操作日志 数据层
 * 
 * @author voxio
 */
public interface SysOperLogMapper extends BaseMapper<SysOperLog>
{
    /**
     * 查询系统操作日志集合（含模糊与时间范围检索，保留自定义 SQL）
     * 
     * @param operLog 操作日志对象
     * @return 操作日志集合
     */
    public List<SysOperLog> selectOperLogList(SysOperLog operLog);

    /**
     * 清空操作日志（truncate，保留自定义 SQL）
     */
    public void cleanOperLog();
}
