package com.voxio.kf.domain;

import java.util.Date;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.voxio.common.utils.crypto.SmFieldCipherTypeHandler;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * 离线留言对象 kf_offline_message
 * 
 * @author voxio
 */
@Getter
@Setter
@ToString
@TableName(value = "kf_offline_message", autoResultMap = true)
public class KfOfflineMessage
{
    private static final long serialVersionUID = 1L;

    /** 状态：待处理 */
    public static final String STATUS_PENDING = "0";

    /** 状态：已转工单 */
    public static final String STATUS_TICKET = "1";

    /** 状态：已关闭 */
    public static final String STATUS_CLOSED = "2";

    /** 留言ID */
    @TableId(type = IdType.AUTO)
    private Long msgId;

    /** 访客ID */
    private Long visitorId;

    /** 关联会话ID */
    private Long sessionId;

    /** 联系方式（等保要求密文落库，读写由类型处理器自动加解密） */
    @TableField(typeHandler = SmFieldCipherTypeHandler.class)
    private String contact;

    /** 留言内容（密文落库） */
    @TableField(typeHandler = SmFieldCipherTypeHandler.class)
    private String content;

    /** 状态（0待处理 1已转工单 2已关闭） */
    private String status;

    /** 转入的工单ID */
    private Long ticketId;

    /** 处理客服ID */
    private Long handlerAgentId;

    /** 创建时间 */
    private Date createTime;

    /** 更新时间 */
    private Date updateTime;
}
