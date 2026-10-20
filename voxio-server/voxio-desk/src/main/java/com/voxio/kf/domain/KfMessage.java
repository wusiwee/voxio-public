package com.voxio.kf.domain;

import java.util.Date;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * 消息对象 kf_message
 * 
 * @author voxio
 */
@Getter
@Setter
@ToString(callSuper = true)
@TableName("kf_message")
public class KfMessage
{
    private static final long serialVersionUID = 1L;

    /** 发送方：访客 */
    public static final String SENDER_VISITOR = "1";

    /** 发送方：客服 */
    public static final String SENDER_AGENT = "2";

    /** 发送方：系统 */
    public static final String SENDER_SYSTEM = "3";

    /** 消息类型：文本 */
    public static final String TYPE_TEXT = "1";

    /** 消息类型：系统事件 */
    public static final String TYPE_EVENT = "4";


    /** 投递状态：已入库 */
    public static final String DELIVER_STORED = "0";

    /** 投递状态：已送达 */
    public static final String DELIVER_SENT = "1";

    /** 投递状态：已读 */
    public static final String DELIVER_READ = "2";

    /** 内容密文存储 */
    public static final String ENCRYPT_YES = "1";

    /** 内容明文存量（阶段10.6 迁移前的历史数据） */
    public static final String ENCRYPT_NO = "0";

    /** 消息ID */
    @TableId(type = IdType.AUTO)
    private Long msgId;

    /** 所属会话ID */
    private Long sessionId;

    /** 会话内递增序号 */
    private Integer seq;

    /** 客户端幂等标识 */
    private String clientMsgId;

    /** 发送方类型（1访客 2客服 3系统） */
    private String senderType;

    /** 发送方ID */
    private Long senderId;

    /** 消息类型 */
    private String msgType;

    /** 文本内容或系统事件描述（阶段10.2 起：加密开启时存 SM4-GCM 密文，见 encryptFlag） */
    private String content;

    /** 内容是否密文存储（1 密文，0 明文存量） */
    private String encryptFlag;

    /** 内容加密算法标识（SM4-GCM） */
    private String algoType;

    /** 加密所用会话密钥版本 */
    private Integer keyVersion;

    /** 投递状态（0已入库 1已送达 2已读 3失败） */
    private String deliverStatus;

    /** 已读时间 */
    private Date readTime;

    /** 创建时间 */
    private Date createTime;
}
