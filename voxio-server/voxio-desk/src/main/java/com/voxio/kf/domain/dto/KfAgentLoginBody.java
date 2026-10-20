package com.voxio.kf.domain.dto;

import java.io.Serializable;

import lombok.Getter;
import lombok.Setter;

/**
 * 客服登录请求体
 * 
 * @author voxio
 */
@Getter
@Setter
public class KfAgentLoginBody implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 客服登录账号 */
    private String agentNo;

    /** 登录密码 */
    private String password;
}
