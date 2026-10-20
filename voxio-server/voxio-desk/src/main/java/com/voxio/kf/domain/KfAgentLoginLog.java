package com.voxio.kf.domain;

import java.util.Date;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * 客服登录日志对象 kf_agent_login_log
 *
 * 阶段10.4：登录不再要图形验证码，「谁在什么时候从哪个地址登录成功/失败」就成了唯一的爆破追溯线索，
 * 因此成功与失败都要留痕（含 IP 与客户端标识），并按 login.log.retention.days 保留至少 180 天。
 *
 * @author voxio
 */
@Getter
@Setter
@ToString
@TableName("kf_agent_login_log")
public class KfAgentLoginLog
{
    /** 登录结果：成功（与若依 sys_logininfor 的 0/1 口径一致） */
    public static final String STATUS_SUCCESS = "0";

    /** 登录结果：失败 */
    public static final String STATUS_FAIL = "1";

    /** 日志ID */
    @TableId(type = IdType.AUTO)
    private Long logId;

    /** 登录账号（账号不存在时也记尝试值） */
    private String agentNo;

    /** 客服ID（账号存在时回填） */
    private Long agentId;

    /** 登录结果（0成功 1失败） */
    private String status;

    /** 结果说明 */
    private String msg;

    /** 登录IP */
    private String loginIp;

    /** 客户端标识 */
    private String loginUa;

    /** 登录时间 */
    private Date loginTime;
}
