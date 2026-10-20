package com.voxio.kf.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.voxio.common.core.domain.BaseEntity;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * 安全与加密策略参数对象 kf_security_config
 *
 * 与 kf_config 分开存放：这里的键控制加密开关、密钥版本、登录锁定等安全策略，
 * 不参与访客端公开配置白名单，避免把安全参数随 /kf/open/config 下发。
 *
 * @author voxio
 */
@Getter
@Setter
@ToString(callSuper = true)
@TableName("kf_security_config")
public class KfSecurityConfig extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 参数ID */
    @TableId(type = IdType.AUTO)
    private Long configId;

    /** 参数键名 */
    private String configKey;

    /** 参数键值 */
    private String configValue;

    /** 参数说明 */
    private String description;
}
