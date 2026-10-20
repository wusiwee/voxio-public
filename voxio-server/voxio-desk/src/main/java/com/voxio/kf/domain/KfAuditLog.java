package com.voxio.kf.domain;

import java.util.Date;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * 业务审计日志对象 kf_audit_log
 *
 * 阶段10.5 P0-3：等保要求「谁在什么时候改了哪条业务对象」可追溯且留痕不可删。
 * 与若依 sys_oper_log 的区别是这张表在代码里根本不存在删除与修改入口——
 * 没有 @TableLogic、Service 只有 record 与查询、Controller 只有一个 GET，
 * 留存期只由 KfAuditRetentionJob 按 ≥180 天下限收敛。
 *
 * @author voxio
 */
@Getter
@Setter
@ToString
@TableName("kf_audit_log")
public class KfAuditLog
{
    /** 操作人类型：管理后台（若依 sys_user） */
    public static final String USER_ADMIN = "admin";

    /** 操作人类型：客服端（独立 kf_agent 账号体系） */
    public static final String USER_AGENT = "agent";

    /** 执行结果：成功 */
    public static final String RESULT_SUCCESS = "success";

    /** 审计ID */
    @TableId(type = IdType.AUTO)
    private Long logId;

    /** 操作人类型（admin / agent） */
    private String userType;

    /** 操作人ID（sys_user.user_id 或 kf_agent.agent_id） */
    private Long userId;

    /** 操作动作（@Log 标题 + 业务类型） */
    private String operation;

    /** 操作对象类型（按接口路径推导） */
    private String targetType;

    /** 操作对象标识 */
    private String targetId;

    /** 操作IP */
    private String ip;

    /** 客户端标识 */
    private String ua;

    /** 请求参数（已剔除个人信息与文件内容） */
    private String requestParams;

    /** 执行结果（success 或 fail:原因） */
    private String result;

    /** 操作时间 */
    private Date createTime;
}
