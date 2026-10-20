package com.voxio.kf.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.voxio.common.core.domain.BaseEntity;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * 客服业务参数对象 kf_config
 * 
 * @author voxio
 */
@Getter
@Setter
@ToString(callSuper = true)
@TableName("kf_config")
public class KfConfig extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 参数ID */
    @TableId(type = IdType.AUTO)
    private Long configId;

    /** 参数键名 */
    private String configKey;

    /** 参数键值 */
    private String configValue;

    /** 参数名称 */
    private String configName;

    /** 系统内置（0是 1否） */
    private String configType;
}
