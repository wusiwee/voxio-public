package com.voxio.kf.domain;

import java.util.Date;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.voxio.common.core.domain.BaseEntity;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * 会话对象 kf_session
 * 
 * @author voxio
 */
@Getter
@Setter
@ToString(callSuper = true)
@TableName("kf_session")
public class KfSession extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 会话状态：排队中 */
    public static final String STATUS_QUEUED = "0";

    /** 会话状态：接待中 */
    public static final String STATUS_SERVING = "1";

    /** 会话状态：已结束 */
    public static final String STATUS_ENDED = "2";

    /** 会话密钥版本：尚未协商 */
    public static final int KEY_VERSION_NONE = 0;

    /** 会话ID */
    @TableId(type = IdType.AUTO)
    private Long sessionId;

    /** 会话编号 */
    private String sessionNo;

    /** 访客ID */
    private Long visitorId;

    /** 接待客服ID（空=未分配） */
    private Long agentId;

    /** 会话状态（0排队中 1接待中 2已结束） */
    private String status;

    /** 进入排队时间 */
    private Date queueTime;

    /** 分配客服时间 */
    private Date assignTime;

    /** 客服首次响应时间 */
    private Date firstRespTime;

    /** 结束时间 */
    private Date endTime;

    /** 结束方（1访客 2客服 3系统超时） */
    private String endBy;

    /** 结束原因 */
    private String closeReason;

    /** 最近一次转接来源客服ID */
    private Long fromAgentId;

    /** 访客未读消息数 */
    private Integer unreadVisitor;

    /** 客服未读消息数 */
    private Integer unreadAgent;

    /** 会话内已分配的最大消息序号 */
    private Integer lastSeq;

    /** 最后一条消息时间 */
    private Date lastMsgTime;

    /** 访客发起页面 */
    private String sourcePage;

    /** 会话 SM4 密钥材料：由主密钥派生的包装密钥做 SM4-GCM 后的 base64，库里拿不到密钥本身 */
    private String sm4KeyCipher;

    /** 会话密钥明文的 SM3 摘要，解包后校验用 */
    private String sm4KeyDigest;

    /** 会话密钥版本，0 表示尚未协商 */
    private Integer sm4KeyVersion;
}
