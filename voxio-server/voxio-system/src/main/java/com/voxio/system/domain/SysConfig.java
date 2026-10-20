package com.voxio.system.domain;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.IdType;

import lombok.Getter;
import lombok.Setter;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.voxio.common.annotation.Excel;
import com.voxio.common.annotation.Excel.ColumnType;
import com.voxio.common.core.domain.BaseEntity;

/**
 * 参数配置表 sys_config
 * 
 * @author voxio
 */
@Getter
@Setter
@TableName("sys_config")
public class SysConfig extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 参数主键 */
    @Excel(name = "参数主键", cellType = ColumnType.NUMERIC)
    @TableId(type = IdType.AUTO)
    private Long configId;

    /** 参数名称 */
    @Excel(name = "参数名称")
    @Size(min = 0, max = 100, message = "参数名称不能超过100个字符")
    @NotBlank(message = "参数名称不能为空")
    private String configName;

    /** 参数键名 */
    @Excel(name = "参数键名")
    @Size(min = 0, max = 100, message = "参数键名长度不能超过100个字符")
    @NotBlank(message = "参数键名长度不能为空")
    private String configKey;

    /** 参数键值 */
    @Excel(name = "参数键值")
    @Size(min = 0, max = 500, message = "参数键值长度不能超过500个字符")
    @NotBlank(message = "参数键值不能为空")
    private String configValue;

    /** 系统内置（Y是 N否） */
    @Excel(name = "系统内置", readConverterExp = "Y=是,N=否")
    private String configType;

    @Override
    public String toString() {
        return new ToStringBuilder(this,ToStringStyle.MULTI_LINE_STYLE)
            .append("configId", getConfigId())
            .append("configName", getConfigName())
            .append("configKey", getConfigKey())
            .append("configValue", getConfigValue())
            .append("configType", getConfigType())
            .append("createBy", getCreateBy())
            .append("createTime", getCreateTime())
            .append("updateBy", getUpdateBy())
            .append("updateTime", getUpdateTime())
            .append("remark", getRemark())
            .toString();
    }
}
