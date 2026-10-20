package com.voxio.system.mapper;

import java.util.List;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.voxio.system.domain.SysUserPost;

/**
 * 用户与岗位关联表 数据层
 * 
 * @author voxio
 */
public interface SysUserPostMapper extends BaseMapper<SysUserPost>
{
    /**
     * 批量新增用户岗位信息（多值批量插入，保留自定义 SQL）
     * 
     * @param userPostList 用户岗位列表
     * @return 结果
     */
    public int batchUserPost(List<SysUserPost> userPostList);
}
