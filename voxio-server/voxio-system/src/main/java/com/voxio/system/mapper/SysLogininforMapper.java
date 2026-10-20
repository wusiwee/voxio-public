package com.voxio.system.mapper;

import java.util.List;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.voxio.system.domain.SysLogininfor;

/**
 * 系统访问日志情况信息 数据层
 * 
 * @author voxio
 */
public interface SysLogininforMapper extends BaseMapper<SysLogininfor>
{
    /**
     * 查询系统登录日志集合（含模糊与时间范围检索，保留自定义 SQL）
     * 
     * @param logininfor 访问日志对象
     * @return 登录记录集合
     */
    public List<SysLogininfor> selectLogininforList(SysLogininfor logininfor);

    /**
     * 清空系统登录日志（truncate，保留自定义 SQL）
     * 
     * @return 结果
     */
    public int cleanLogininfor();
}
