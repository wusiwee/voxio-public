package com.voxio.system.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.voxio.common.core.domain.entity.SysRole;

/**
 * 角色表 数据层
 * 
 * @author voxio
 */
public interface SysRoleMapper extends BaseMapper<SysRole>
{
    /**
     * 根据条件分页查询角色数据（多表关联 + 数据权限，保留自定义 SQL）
     * 
     * @param role 角色信息
     * @return 角色数据集合信息
     */
    public List<SysRole> selectRoleList(SysRole role);

    /**
     * 根据用户ID查询角色（多表关联，保留自定义 SQL）
     * 
     * @param userId 用户ID
     * @return 角色列表
     */
    public List<SysRole> selectRolePermissionByUserId(@Param("userId") Long userId);

    /**
     * 根据用户ID获取角色选择框列表（多表关联，保留自定义 SQL）
     * 
     * @param userId 用户ID
     * @return 选中角色ID列表
     */
    public List<Long> selectRoleListByUserId(@Param("userId") Long userId);

    /**
     * 根据用户名查询角色（多表关联，保留自定义 SQL）
     * 
     * @param userName 用户名
     * @return 角色列表
     */
    public List<SysRole> selectRolesByUserName(@Param("userName") String userName);

    /**
     * 校验角色名称是否唯一（limit 1，保留自定义 SQL）
     * 
     * @param roleName 角色名称
     * @return 角色信息
     */
    public SysRole checkRoleNameUnique(@Param("roleName") String roleName);

    /**
     * 校验角色权限是否唯一（limit 1，保留自定义 SQL）
     * 
     * @param roleKey 角色权限
     * @return 角色信息
     */
    public SysRole checkRoleKeyUnique(@Param("roleKey") String roleKey);

    /**
     * 通过角色ID删除角色（逻辑删除 del_flag，保留自定义 SQL）
     * 
     * @param roleId 角色ID
     * @return 结果
     */
    public int deleteRoleById(@Param("roleId") Long roleId);

    /**
     * 批量删除角色信息（逻辑删除 del_flag，保留自定义 SQL）
     * 
     * @param roleIds 需要删除的角色ID
     * @return 结果
     */
    public int deleteRoleByIds(@Param("roleIds") Long[] roleIds);
}
