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
 * 客服工单对象 kf_ticket
 * 
 * 单商户下的简单工单：只有状态流转，无工作流引擎。
 * 
 * @author voxio
 */
@Getter
@Setter
@ToString(callSuper = true)
@TableName(value = "kf_ticket", autoResultMap = true)
public class KfTicket extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 状态：待处理 */
    public static final String STATUS_PENDING = "0";

    /** 状态：处理中 */
    public static final String STATUS_DOING = "1";

    /** 状态：已解决 */
    public static final String STATUS_SOLVED = "2";

    /** 状态：已关闭 */
    public static final String STATUS_CLOSED = "3";

    /** 来源：会话内创建 */
    public static final String SOURCE_SESSION = "1";

    /** 来源：离线留言转入 */
    public static final String SOURCE_OFFLINE = "2";

    /** 来源：后台手动 */
    public static final String SOURCE_MANUAL = "3";

    /** 创建人类型：管理员 */
    public static final String CREATOR_ADMIN = "1";

    /** 创建人类型：客服 */
    public static final String CREATOR_AGENT = "2";

    /** 工单ID */
    @TableId(type = IdType.AUTO)
    private Long ticketId;

    /** 工单编号 */
    private String ticketNo;

    /** 工单标题 */
    private String title;

    /** 工单内容（等保要求密文落库，读写由类型处理器自动加解密） */
    @TableField(typeHandler = SmFieldCipherTypeHandler.class)
    private String content;

    /** 工单分类（字典 kf_ticket_category） */
    private String category;

    /** 优先级（1低 2中 3高） */
    private String priority;

    /** 状态（0待处理 1处理中 2已解决 3已关闭） */
    private String status;

    /** 来源（1会话内创建 2离线留言转入 3后台手动） */
    private String source;

    /** 关联访客ID */
    private Long visitorId;

    /** 关联会话ID */
    private Long sessionId;

    /** 访客联系方式（密文落库） */
    @TableField(typeHandler = SmFieldCipherTypeHandler.class)
    private String contact;

    /** 创建人类型（1管理员 2客服 3系统） */
    private String creatorType;

    /** 创建人ID */
    private Long creatorId;

    /** 处理客服ID */
    private Long handlerAgentId;

    /** 解决时间 */
    private Date finishTime;

    /** 关闭时间 */
    private Date closeTime;

    /** 第三方推送状态（0未推送 1已推送 2推送失败），只保留最近一次结果 */
    private String pushStatus;

    /** 最近一次推送时间 */
    private Date pushTime;

    /** 最近一次推送响应摘要，供客服与主管判断失败原因 */
    private String pushResponse;
}
