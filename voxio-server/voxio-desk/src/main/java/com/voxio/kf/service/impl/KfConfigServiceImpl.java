package com.voxio.kf.service.impl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.voxio.common.core.redis.RedisCache;
import com.voxio.common.exception.ServiceException;
import com.voxio.common.utils.DateUtils;
import com.voxio.common.utils.SecurityUtils;
import com.voxio.common.utils.StringUtils;
import com.voxio.kf.domain.KfConfig;
import com.voxio.kf.mapper.KfConfigMapper;
import com.voxio.kf.service.IKfConfigService;

import lombok.RequiredArgsConstructor;

/**
 * 客服业务参数 服务实现
 * 
 * @author voxio
 */
@Service
@RequiredArgsConstructor
public class KfConfigServiceImpl extends ServiceImpl<KfConfigMapper, KfConfig> implements IKfConfigService
{
    /** 参数缓存前缀 */
    private static final String CONFIG_CACHE_KEY = "kf_config:";

    /** 访客端可见参数（白名单，避免泄露密钥类配置） */
    private static final List<String> PUBLIC_KEYS = List.of(
            "kf.welcome.text", "kf.queue.tip", "kf.agent.offline.tip", "kf.session.timeout.minutes",
            "kf.offline.enabled", "kf.service.enabled", "kf.service.time", "kf.offline.reply.tip");

    /**
     * 业务设置字段定义（白名单 + 取值校验规则）。
     * 密钥类、网关限流类参数不进白名单，仍由「参数配置」页面维护。
     */
    private static final Map<String, BusinessField> BUSINESS_FIELDS = businessFields();

    private static Map<String, BusinessField> businessFields()
    {
        Map<String, BusinessField> map = new LinkedHashMap<>();
        map.put("kf.welcome.text", BusinessField.text("访客进入欢迎语", 500));
        map.put("kf.queue.tip", BusinessField.text("排队提示语", 500));
        map.put("kf.agent.offline.tip", BusinessField.text("无客服在线提示语", 500));
        map.put("kf.offline.reply.tip", BusinessField.text("留言成功提示语", 500));
        map.put("kf.service.time", BusinessField.time("服务时间段"));
        map.put("kf.offline.enabled", BusinessField.bool("允许离线留言"));
        map.put("kf.assign.enabled", BusinessField.bool("会话自动分配"));
        map.put("kf.service.enabled", BusinessField.bool("限制服务时间"));
        map.put("kf.session.timeout.minutes", BusinessField.number("会话自动结束（分钟）", 0, 1440));
        map.put("kf.assign.maxPerAgent", BusinessField.number("单客服默认最大并发会话数", 1, 50));
        map.put("kf.ticket.push.enabled", BusinessField.bool("工单第三方推送"));
        map.put("kf.ticket.push.url", BusinessField.text("工单推送地址", 300));
        map.put("kf.ticket.push.headers", BusinessField.text("推送鉴权头", 300));
        map.put("kf.ticket.push.timeoutSeconds", BusinessField.number("推送超时（秒）", 1, 30));
        return map;
    }

    private final RedisCache redisCache;

    @Override
    public List<KfConfig> selectConfigList(KfConfig config)
    {
        return list(new LambdaQueryWrapper<KfConfig>()
                .like(StringUtils.isNotEmpty(config.getConfigName()), KfConfig::getConfigName, config.getConfigName())
                .like(StringUtils.isNotEmpty(config.getConfigKey()), KfConfig::getConfigKey, config.getConfigKey())
                .eq(StringUtils.isNotEmpty(config.getConfigType()), KfConfig::getConfigType, config.getConfigType())
                .orderByAsc(KfConfig::getConfigId));
    }

    @Override
    public String selectConfigByKey(String configKey)
    {
        String cached = redisCache.getCacheObject(CONFIG_CACHE_KEY + configKey);
        if (StringUtils.isNotEmpty(cached))
        {
            return cached;
        }
        KfConfig config = getOne(new LambdaQueryWrapper<KfConfig>().eq(KfConfig::getConfigKey, configKey), false);
        if (StringUtils.isNull(config))
        {
            return "";
        }
        redisCache.setCacheObject(CONFIG_CACHE_KEY + configKey, config.getConfigValue());
        return config.getConfigValue();
    }

    @Override
    public boolean selectBooleanByKey(String configKey, boolean defaultValue)
    {
        String value = selectConfigByKey(configKey);
        return StringUtils.isEmpty(value) ? defaultValue : "true".equalsIgnoreCase(value);
    }

    @Override
    public int selectIntByKey(String configKey, int defaultValue)
    {
        String value = selectConfigByKey(configKey);
        if (StringUtils.isEmpty(value))
        {
            return defaultValue;
        }
        try
        {
            return Integer.parseInt(value.trim());
        }
        catch (NumberFormatException e)
        {
            return defaultValue;
        }
    }

    @Override
    public Map<String, String> selectPublicConfig()
    {
        return selectValuesByKey(PUBLIC_KEYS);
    }

    /**
     * 批量取值：Redis 一次 MGET，未命中的键合并成一条 SQL 回源，再一次性写回缓存。
     *
     * 数据库与 Redis 都部署在公网，单次往返 15~20ms；访客端入口要读十几个参数键，
     * 逐键 GET 就是十几次往返（阶段11 基线 /kf/open/config 164ms 基本全是往返）。
     * 仍保留「空值视为未命中」的原语义，管理后台改值后清缓存的生效路径不变。
     */
    private Map<String, String> selectValuesByKey(List<String> keys)
    {
        List<String> redisKeys = new ArrayList<>(keys.size());
        for (String key : keys)
        {
            redisKeys.add(CONFIG_CACHE_KEY + key);
        }
        List<Object> cached = redisCache.getMultiCacheObject(redisKeys);
        Map<String, String> values = new LinkedHashMap<>();
        List<String> missing = new ArrayList<>();
        for (int i = 0; i < keys.size(); i++)
        {
            Object hit = i < cached.size() ? cached.get(i) : null;
            String value = hit == null ? null : String.valueOf(hit);
            if (StringUtils.isNotEmpty(value))
            {
                values.put(keys.get(i), value);
            }
            else
            {
                missing.add(keys.get(i));
            }
        }
        if (!missing.isEmpty())
        {
            Map<String, String> refill = new LinkedHashMap<>();
            for (KfConfig config : list(new LambdaQueryWrapper<KfConfig>().in(KfConfig::getConfigKey, missing)))
            {
                String value = StringUtils.nvl(config.getConfigValue(), "");
                values.put(config.getConfigKey(), value);
                refill.put(CONFIG_CACHE_KEY + config.getConfigKey(), value);
            }
            redisCache.setMultiCacheObject(refill);
        }
        Map<String, String> ordered = new LinkedHashMap<>();
        for (String key : keys)
        {
            ordered.put(key, values.getOrDefault(key, ""));
        }
        return ordered;
    }

    @Override
    public int insertConfig(KfConfig config)
    {
        if (StringUtils.isEmpty(config.getConfigKey()) || StringUtils.isEmpty(config.getConfigName()))
        {
            throw new ServiceException("参数键名与参数名称不能为空");
        }
        if (count(new LambdaQueryWrapper<KfConfig>().eq(KfConfig::getConfigKey, config.getConfigKey())) > 0)
        {
            throw new ServiceException(StringUtils.format("参数键名【{}】已存在", config.getConfigKey()));
        }
        config.setConfigId(null);
        config.setCreateBy(SecurityUtils.getUsername());
        config.setCreateTime(DateUtils.getNowDate());
        if (StringUtils.isEmpty(config.getConfigType()))
        {
            config.setConfigType("1");
        }
        int rows = save(config) ? 1 : 0;
        redisCache.setCacheObject(CONFIG_CACHE_KEY + config.getConfigKey(), config.getConfigValue());
        return rows;
    }

    @Override
    public int updateConfig(KfConfig config)
    {
        KfConfig old = getById(config.getConfigId());
        if (StringUtils.isNull(old))
        {
            throw new ServiceException("参数不存在");
        }
        if (!old.getConfigKey().equals(config.getConfigKey())
                && count(new LambdaQueryWrapper<KfConfig>().eq(KfConfig::getConfigKey, config.getConfigKey())) > 0)
        {
            throw new ServiceException(StringUtils.format("参数键名【{}】已存在", config.getConfigKey()));
        }
        config.setUpdateBy(SecurityUtils.getUsername());
        config.setUpdateTime(DateUtils.getNowDate());
        int rows = updateById(config) ? 1 : 0;
        redisCache.deleteObject(CONFIG_CACHE_KEY + old.getConfigKey());
        selectConfigByKey(config.getConfigKey());
        return rows;
    }

    @Override
    public int deleteConfigById(Long configId)
    {
        KfConfig config = getById(configId);
        if (StringUtils.isNull(config))
        {
            throw new ServiceException("参数不存在");
        }
        if ("0".equals(config.getConfigType()))
        {
            throw new ServiceException("系统内置参数不允许删除，可直接修改取值");
        }
        int rows = baseMapper.deleteById(configId);
        redisCache.deleteObject(CONFIG_CACHE_KEY + config.getConfigKey());
        return rows;
    }

    @Override
    public void resetConfigCache()
    {
        // 用库里的键名精确删除，不用 KEYS 模式匹配：KEYS 是阻塞命令，公网 Redis 上一次往返就把整库扫了
        List<KfConfig> configs = list();
        List<String> cacheKeys = new ArrayList<>(configs.size());
        Map<String, String> refill = new LinkedHashMap<>(configs.size());
        for (KfConfig config : configs)
        {
            cacheKeys.add(CONFIG_CACHE_KEY + config.getConfigKey());
            refill.put(CONFIG_CACHE_KEY + config.getConfigKey(), StringUtils.nvl(config.getConfigValue(), ""));
        }
        redisCache.deleteObject(cacheKeys);
        redisCache.setMultiCacheObject(refill);
    }

    @Override
    public Map<String, String> selectBusinessConfig()
    {
        return selectValuesByKey(new ArrayList<>(BUSINESS_FIELDS.keySet()));
    }

    @Override
    @Transactional
    public int updateBusinessConfig(Map<String, String> configs, String updateBy)
    {
        if (StringUtils.isEmpty(configs))
        {
            throw new ServiceException("请至少提交一项业务参数");
        }
        int rows = 0;
        for (Map.Entry<String, String> entry : configs.entrySet())
        {
            String key = entry.getKey();
            BusinessField field = BUSINESS_FIELDS.get(key);
            if (StringUtils.isNull(field))
            {
                throw new ServiceException(StringUtils.format("参数【{}】不支持在业务设置中修改", key));
            }
            KfConfig config = getOne(new LambdaQueryWrapper<KfConfig>().eq(KfConfig::getConfigKey, key), false);
            if (StringUtils.isNull(config))
            {
                throw new ServiceException(StringUtils.format("参数【{}】不存在", key));
            }
            String value = field.normalize(entry.getValue());
            config.setConfigValue(value);
            config.setUpdateBy(updateBy);
            config.setUpdateTime(DateUtils.getNowDate());
            if (updateById(config))
            {
                rows++;
            }
            // 直接覆写缓存，避免其他节点读到旧值
            redisCache.setCacheObject(CONFIG_CACHE_KEY + key, value);
        }
        return rows;
    }

    /** 业务设置字段：类型 + 取值校验 */
    private static final class BusinessField
    {
        private static final String TYPE_TEXT = "text";
        private static final String TYPE_BOOL = "bool";
        private static final String TYPE_NUMBER = "number";
        private static final String TYPE_TIME = "time";

        private static final Pattern TIME_RANGE = Pattern.compile("^(\\d{1,2}):(\\d{2})-(\\d{1,2}):(\\d{2})$");

        private final String label;
        private final String type;
        private final int min;
        private final int max;
        private final int maxLength;

        private BusinessField(String label, String type, int min, int max, int maxLength)
        {
            this.label = label;
            this.type = type;
            this.min = min;
            this.max = max;
            this.maxLength = maxLength;
        }

        static BusinessField text(String label, int maxLength)
        {
            return new BusinessField(label, TYPE_TEXT, 0, 0, maxLength);
        }

        static BusinessField bool(String label)
        {
            return new BusinessField(label, TYPE_BOOL, 0, 0, 0);
        }

        static BusinessField number(String label, int min, int max)
        {
            return new BusinessField(label, TYPE_NUMBER, min, max, 0);
        }

        static BusinessField time(String label)
        {
            return new BusinessField(label, TYPE_TIME, 0, 0, 32);
        }

        /** 校验并归一化取值，非法时抛业务异常 */
        String normalize(String raw)
        {
            String value = StringUtils.nvl(raw, "").trim();
            switch (type)
            {
                case TYPE_BOOL:
                    if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value))
                    {
                        throw new ServiceException(StringUtils.format("{} 只能为 true 或 false", label));
                    }
                    return value.toLowerCase();
                case TYPE_NUMBER:
                    int num;
                    try
                    {
                        num = Integer.parseInt(value);
                    }
                    catch (NumberFormatException e)
                    {
                        throw new ServiceException(StringUtils.format("{} 必须为整数", label));
                    }
                    if (num < min || num > max)
                    {
                        throw new ServiceException(StringUtils.format("{} 取值范围为 {}~{}", label, min, max));
                    }
                    return String.valueOf(num);
                case TYPE_TIME:
                    if (StringUtils.isEmpty(value))
                    {
                        return "";
                    }
                    Matcher matcher = TIME_RANGE.matcher(value);
                    if (!matcher.matches() || Integer.parseInt(matcher.group(1)) > 23
                            || Integer.parseInt(matcher.group(2)) > 59
                            || Integer.parseInt(matcher.group(3)) > 23
                            || Integer.parseInt(matcher.group(4)) > 59)
                    {
                        throw new ServiceException(StringUtils.format("{} 格式应为 HH:mm-HH:mm", label));
                    }
                    return value;
                default:
                    if (value.length() > maxLength)
                    {
                        throw new ServiceException(StringUtils.format("{} 最多 {} 个字符", label, maxLength));
                    }
                    return value;
            }
        }
    }
}
