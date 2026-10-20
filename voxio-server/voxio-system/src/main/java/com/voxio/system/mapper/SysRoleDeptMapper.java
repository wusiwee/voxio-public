package com.voxio.system.mapper;

import java.util.List;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.voxio.system.domain.SysRoleDept;

/**
 * 角色与部门关联表 数据层
 * 
 * @author voxio
 */
public interface SysRoleDeptMapper extends BaseMapper<SysRoleDept>
{
    /**
     * 批量新增角色部门信息（多值批量插入，保留自定义 SQL）
     * 
     * @param roleDeptList 角色部门列表
     * @return 结果
     */
    public int batchRoleDept(List<SysRoleDept> roleDeptList);
}
