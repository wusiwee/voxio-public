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
 * 工单回复与流转记录对象 kf_ticket_reply
 * 
 * 一张表承载对外回复、状态变更、内部备注三类记录，用 replyType 区分。
 * 
 * @author voxio
 */
@Getter
@Setter
@ToString
@TableName(value = "kf_ticket_reply", autoResultMap = true)
public class KfTicketReply
{
    private static final long serialVersionUID = 1L;

    /** 类型：对外回复 */
    public static final String TYPE_REPLY = "1";

    /** 类型：状态变更 */
    public static final String TYPE_STATUS = "2";

    /** 类型：内部备注 */
    public static final String TYPE_NOTE = "3";

    /** 操作人类型：管理员 */
    public static final String OPERATOR_ADMIN = "1";

    /** 操作人类型：客服 */
    public static final String OPERATOR_AGENT = "2";

    /** 回复ID */
    @TableId(type = IdType.AUTO)
    private Long replyId;

    /** 工单ID */
    private Long ticketId;

    /** 类型（1对外回复 2状态变更 3内部备注） */
    private String replyType;

    /** 回复/备注内容（等保要求密文落库，读写由类型处理器自动加解密） */
    @TableField(typeHandler = SmFieldCipherTypeHandler.class)
    private String content;

    /** 变更前状态 */
    private String fromStatus;

    /** 变更后状态 */
    private String toStatus;

    /** 操作人类型（1管理员 2客服 3访客） */
    private String operatorType;

    /** 操作人ID */
    private Long operatorId;

    /** 操作人名称 */
    private String operatorName;

    /** 创建时间 */
    private Date createTime;
}
