package com.voxio.kf.domain;

import java.util.Date;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.voxio.common.core.domain.BaseEntity;
import com.voxio.common.utils.crypto.SmFieldCipherTypeHandler;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * 访客对象 kf_visitor
 * 
 * 匿名访客，visitor_no 由前端生成并持久化到 localStorage
 * 
 * @author voxio
 */
@Getter
@Setter
@ToString(callSuper = true)
@TableName(value = "kf_visitor", autoResultMap = true)
public class KfVisitor extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 访客ID */
    @TableId(type = IdType.AUTO)
    private Long visitorId;

    /** 访客唯一标识 */
    private String visitorNo;

    /** 访客昵称 */
    private String nickname;

    /** 最近访问IP */
    private String ip;

    /** 浏览器信息 */
    private String userAgent;

    /** 来源页面 */
    private String referrer;

    /** 来源渠道标识 */
    private String source;

    /** 联系方式-手机（密文落库） */
    @TableField(typeHandler = SmFieldCipherTypeHandler.class)
    private String contactPhone;

    /** 联系方式-邮箱（密文落库） */
    @TableField(typeHandler = SmFieldCipherTypeHandler.class)
    private String contactEmail;

    /** 累计会话数 */
    private Integer sessionCount;

    /** 最近访问时间 */
    private Date lastVisitTime;
}
