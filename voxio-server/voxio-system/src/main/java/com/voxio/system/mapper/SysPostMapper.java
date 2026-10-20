package com.voxio.system.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.voxio.system.domain.SysPost;

/**
 * 岗位信息 数据层
 * 
 * @author voxio
 */
public interface SysPostMapper extends BaseMapper<SysPost>
{
    /**
     * 查询岗位数据集合（含模糊检索，保留自定义 SQL）
     * 
     * @param post 岗位信息
     * @return 岗位数据集合
     */
    public List<SysPost> selectPostList(SysPost post);

    /**
     * 根据用户ID获取岗位选择框列表（多表关联，保留自定义 SQL）
     * 
     * @param userId 用户ID
     * @return 选中岗位ID列表
     */
    public List<Long> selectPostListByUserId(@Param("userId") Long userId);

    /**
     * 查询用户所属岗位组（多表关联，保留自定义 SQL）
     * 
     * @param userName 用户名
     * @return 结果
     */
    public List<SysPost> selectPostsByUserName(@Param("userName") String userName);

    /**
     * 校验岗位名称唯一（limit 1，保留自定义 SQL）
     * 
     * @param postName 岗位名称
     * @return 结果
     */
    public SysPost checkPostNameUnique(@Param("postName") String postName);

    /**
     * 校验岗位编码唯一（limit 1，保留自定义 SQL）
     * 
     * @param postCode 岗位编码
     * @return 结果
     */
    public SysPost checkPostCodeUnique(@Param("postCode") String postCode);
}
