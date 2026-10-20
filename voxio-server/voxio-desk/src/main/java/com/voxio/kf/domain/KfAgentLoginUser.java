package com.voxio.kf.domain;

import java.io.Serializable;
import java.util.Date;

import com.fasterxml.jackson.annotation.JsonFormat;

import lombok.Getter;
import lombok.Setter;

/**
 * 客服登录身份信息（缓存于 Redis，独立于若依 LoginUser）
 * 
 * @author voxio
 */
@Getter
@Setter
public class KfAgentLoginUser implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 客服ID */
    private Long agentId;

    /** 客服登录账号 */
    private String agentNo;

    /** 客服昵称 */
    private String nickName;

    /** 头像地址 */
    private String avatar;

    /** 在线状态（0离线 1在线 2忙碌） */
    private String onlineStatus;

    /** 最大并发接待会话数 */
    private Integer acceptMax;

    /** 令牌唯一标识 */
    private String token;

    /** 登录时间 */
    private Long loginTime;

    /** 过期时间 */
    private Long expireTime;

    /** 登录IP */
    private String ipaddr;

    public boolean isOnline()
    {
        return onlineStatus != null && !"0".equals(onlineStatus);
    }

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    public Date getLoginDate()
    {
        return loginTime == null ? null : new Date(loginTime);
    }
}
