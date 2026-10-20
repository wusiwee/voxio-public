package com.voxio.kf.domain;

import java.util.Date;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.voxio.common.core.domain.BaseEntity;
import com.voxio.common.utils.crypto.SmFieldCipherTypeHandler;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * 客服账号对象 kf_agent
 * 
 * 客服账号独立于 sys_user，管理员账号仍使用若依 sys_user
 * 
 * @author voxio
 */
@Getter
@Setter
@ToString(callSuper = true)
@TableName(value = "kf_agent", autoResultMap = true)
public class KfAgent extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 客服ID */
    @TableId(type = IdType.AUTO)
    private Long agentId;

    /** 客服登录账号 */
    private String agentNo;

    /** 登录密码（阶段10.3 起为 SM3 加盐迭代哈希，存量记录仍可能是 BCrypt；可写入不可输出） */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;

    /** 密码哈希算法（sm3-salt / bcrypt），登录成功后存量记录渐进升级 */
    private String passwordAlgo;

    /** 客服昵称（访客可见） */
    private String nickName;

    /** 头像地址 */
    private String avatar;

    /** 手机号码（等保要求密文落库，读写由类型处理器自动加解密） */
    @TableField(typeHandler = SmFieldCipherTypeHandler.class)
    private String phone;

    /** 邮箱（密文落库） */
    @TableField(typeHandler = SmFieldCipherTypeHandler.class)
    private String email;

    /** 在线状态（0离线 1在线 2忙碌） */
    private String onlineStatus;

    /** 最大并发接待会话数 */
    private Integer acceptMax;

    /** 当前接待中会话数 */
    private Integer currentLoad;

    /** 个人欢迎语，为空则使用全局欢迎语 */
    private String welcomeText;

    /** 账号状态（0正常 1停用） */
    private String status;

    /** 删除标志（0代表存在 2代表删除） */
    @TableLogic(value = "0", delval = "2")
    @JsonIgnore
    private String delFlag;

    /** 最后登录IP */
    private String loginIp;

    /** 最后登录时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date loginDate;
}
