package com.voxio.kf.service;

import java.util.List;
import java.util.Map;

import com.baomidou.mybatisplus.spring.service.IService;
import com.voxio.kf.domain.KfConfig;

/**
 * 客服业务参数 服务层
 * 
 * @author voxio
 */
public interface IKfConfigService extends IService<KfConfig>
{
    /**
     * 查询参数列表
     * 
     * @param config 查询条件
     * @return 参数集合
     */
    public List<KfConfig> selectConfigList(KfConfig config);

    /**
     * 按键名读取参数值（带 Redis 缓存）
     * 
     * @param configKey 参数键名
     * @return 参数值，不存在返回空串
     */
    public String selectConfigByKey(String configKey);

    /**
     * 读取布尔参数
     * 
     * @param configKey 参数键名
     * @param defaultValue 缺省值
     * @return 布尔值
     */
    public boolean selectBooleanByKey(String configKey, boolean defaultValue);

    /**
     * 读取整型参数
     * 
     * @param configKey 参数键名
     * @param defaultValue 缺省值
     * @return 整型值
     */
    public int selectIntByKey(String configKey, int defaultValue);

    /**
     * 访客端可见参数（白名单，不含密钥类参数）
     * 
     * @return 键值集合
     */
    public Map<String, String> selectPublicConfig();

    /**
     * 管理后台业务设置参数（白名单，不含密钥类参数）
     * 
     * @return 键值集合，键为参数键名
     */
    public Map<String, String> selectBusinessConfig();

    /**
     * 批量更新业务设置参数，仅接受白名单内的键名
     * 
     * @param configs 键值集合
     * @param updateBy 操作人
     * @return 实际更新条数
     */
    public int updateBusinessConfig(Map<String, String> configs, String updateBy);

    /**
     * 新增参数
     * 
     * @param config 参数
     * @return 结果
     */
    public int insertConfig(KfConfig config);

    /**
     * 修改参数
     * 
     * @param config 参数
     * @return 结果
     */
    public int updateConfig(KfConfig config);

    /**
     * 删除参数（系统内置禁止删除）
     * 
     * @param configId 参数ID
     * @return 结果
     */
    public int deleteConfigById(Long configId);

    /**
     * 清空并重建参数缓存
     */
    public void resetConfigCache();
}
