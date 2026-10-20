package com.voxio.system.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.voxio.system.domain.SysConfig;

/**
 * 参数配置 数据层
 * 
 * @author voxio
 */
public interface SysConfigMapper extends BaseMapper<SysConfig>
{
    /**
     * 校验参数键名是否唯一（limit 1，保留自定义 SQL）
     * 
     * @param configKey 参数键名
     * @return 参数配置信息
     */
    public SysConfig checkConfigKeyUnique(@Param("configKey") String configKey);

    /**
     * 查询参数配置列表（含 params 时间范围等动态条件，保留自定义 SQL）
     * 
     * @param config 参数配置信息
     * @return 参数配置集合
     */
    public List<SysConfig> selectConfigList(SysConfig config);
}
