package com.voxio.common.core.domain.entity;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.IdType;

import lombok.Getter;
import lombok.Setter;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.voxio.common.annotation.Excel;
import com.voxio.common.annotation.Excel.ColumnType;
import com.voxio.common.core.domain.BaseEntity;

/**
 * 字典类型表 sys_dict_type
 * 
 * @author voxio
 */
@Getter
@Setter
@TableName("sys_dict_type")
public class SysDictType extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 字典主键 */
    @Excel(name = "字典主键", cellType = ColumnType.NUMERIC)
    @TableId(type = IdType.AUTO)
    private Long dictId;

    /** 字典名称 */
    @Excel(name = "字典名称")
    @Size(min = 0, max = 100, message = "字典类型名称长度不能超过100个字符")
    @NotBlank(message = "字典名称不能为空")
    private String dictName;

    /** 字典类型 */
    @Excel(name = "字典类型")
    @Pattern(regexp = "^[a-z][a-z0-9_]*$", message = "字典类型必须以字母开头，且只能为（小写字母，数字，下滑线）")
    @Size(min = 0, max = 100, message = "字典类型类型长度不能超过100个字符")
    @NotBlank(message = "字典类型不能为空")
    private String dictType;

    /** 状态（0正常 1停用） */
    @Excel(name = "状态", readConverterExp = "0=正常,1=停用")
    private String status;

    @Override
    public String toString() {
        return new ToStringBuilder(this,ToStringStyle.MULTI_LINE_STYLE)
            .append("dictId", getDictId())
            .append("dictName", getDictName())
            .append("dictType", getDictType())
            .append("status", getStatus())
            .append("createBy", getCreateBy())
            .append("createTime", getCreateTime())
            .append("updateBy", getUpdateBy())
            .append("updateTime", getUpdateTime())
            .append("remark", getRemark())
            .toString();
    }
}
