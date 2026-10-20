package com.voxio.system.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.voxio.common.core.domain.entity.SysDictData;

/**
 * 字典表 数据层
 * 
 * @author voxio
 */
public interface SysDictDataMapper extends BaseMapper<SysDictData>
{
    /**
     * 根据条件分页查询字典数据（含模糊检索，保留自定义 SQL）
     * 
     * @param dictData 字典数据信息
     * @return 字典数据集合信息
     */
    public List<SysDictData> selectDictDataList(SysDictData dictData);

    /**
     * 按字典类型批量更新字典数据的类型编码（字典类型改名时同步，保留自定义 SQL）
     * 
     * @param oldDictType 原类型编码
     * @param newDictType 新类型编码
     * @return 结果
     */
    public int updateDictDataType(@Param("oldDictType") String oldDictType, @Param("newDictType") String newDictType);
}
