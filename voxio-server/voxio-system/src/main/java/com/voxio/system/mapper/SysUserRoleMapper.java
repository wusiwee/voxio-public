package com.voxio.system.mapper;

import java.util.List;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.voxio.system.domain.SysUserRole;

/**
 * 用户与角色关联表 数据层
 * 
 * @author voxio
 */
public interface SysUserRoleMapper extends BaseMapper<SysUserRole>
{
    /**
     * 批量新增用户角色信息（多值批量插入，保留自定义 SQL）
     * 
     * @param userRoleList 用户角色列表
     * @return 结果
     */
    public int batchUserRole(List<SysUserRole> userRoleList);
}
