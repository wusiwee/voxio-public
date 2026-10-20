package com.voxio.system.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.voxio.common.core.domain.entity.SysDictType;

/**
 * 字典表 数据层
 * 
 * @author voxio
 */
public interface SysDictTypeMapper extends BaseMapper<SysDictType>
{
    /**
     * 根据条件分页查询字典类型（含模糊与时间范围检索，保留自定义 SQL）
     * 
     * @param dictType 字典类型信息
     * @return 字典类型集合信息
     */
    public List<SysDictType> selectDictTypeList(SysDictType dictType);

    /**
     * 校验字典类型是否唯一（limit 1，保留自定义 SQL）
     * 
     * @param dictType 字典类型
     * @return 结果
     */
    public SysDictType checkDictTypeUnique(@Param("dictType") String dictType);
}
