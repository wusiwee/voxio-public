package com.voxio.kf.domain;

import java.io.Serializable;

import lombok.Getter;
import lombok.Setter;

/**
 * 访客登录身份信息（缓存于 Redis，令牌用于网关鉴权）
 * 
 * @author voxio
 */
@Getter
@Setter
public class KfVisitorLoginUser implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 访客ID */
    private Long visitorId;

    /** 访客唯一标识 */
    private String visitorNo;

    /** 绑定的会话ID */
    private Long sessionId;

    /** 令牌唯一标识 */
    private String token;

    /** 登录时间 */
    private Long loginTime;

    /** 过期时间 */
    private Long expireTime;

    /** 登录IP */
    private String ipaddr;
}
