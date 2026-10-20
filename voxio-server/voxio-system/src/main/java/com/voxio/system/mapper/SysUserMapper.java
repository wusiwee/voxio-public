package com.voxio.system.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.voxio.common.core.domain.entity.SysUser;

/**
 * 用户表 数据层
 * 
 * @author voxio
 */
public interface SysUserMapper extends BaseMapper<SysUser>
{
    /**
     * 根据条件分页查询用户列表（多表关联 + 数据权限，保留自定义 SQL）
     * 
     * @param sysUser 用户信息
     * @return 用户信息集合信息
     */
    public List<SysUser> selectUserList(SysUser sysUser);

    /**
     * 根据条件分页查询已配用户角色列表（多表关联，保留自定义 SQL）
     * 
     * @param user 用户信息
     * @return 用户信息集合信息
     */
    public List<SysUser> selectAllocatedList(SysUser user);

    /**
     * 根据条件分页查询未分配用户角色列表（多表关联，保留自定义 SQL）
     * 
     * @param user 用户信息
     * @return 用户信息集合信息
     */
    public List<SysUser> selectUnallocatedList(SysUser user);

    /**
     * 通过用户名查询用户（带部门/角色关联结果，保留自定义 SQL）
     * 
     * @param userName 用户名
     * @return 用户对象信息
     */
    public SysUser selectUserByUserName(@Param("userName") String userName);

    /**
     * 通过用户ID查询用户（带部门/角色关联结果，保留自定义 SQL）
     * 
     * @param userId 用户ID
     * @return 用户对象信息
     */
    public SysUser selectUserById(@Param("userId") Long userId);

    /**
     * 修改用户信息（deptId!=0 等条件语义特殊，保留自定义 SQL）
     * 
     * @param user 用户信息
     * @return 结果
     */
    public int updateUser(SysUser user);

    /**
     * 通过用户ID删除用户（逻辑删除 del_flag，保留自定义 SQL）
     * 
     * @param userId 用户ID
     * @return 结果
     */
    public int deleteUserById(@Param("userId") Long userId);

    /**
     * 批量删除用户信息（逻辑删除 del_flag，保留自定义 SQL）
     * 
     * @param userIds 需要删除的用户ID
     * @return 结果
     */
    public int deleteUserByIds(@Param("userIds") Long[] userIds);

    /**
     * 校验用户名称是否唯一（limit 1，保留自定义 SQL）
     * 
     * @param userName 用户名称
     * @return 结果
     */
    public SysUser checkUserNameUnique(@Param("userName") String userName);
}
