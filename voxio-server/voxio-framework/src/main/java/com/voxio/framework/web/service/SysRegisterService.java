package com.voxio.framework.web.service;
import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;
import com.voxio.common.constant.Constants;
import com.voxio.common.constant.UserConstants;
import com.voxio.common.core.domain.entity.SysUser;
import com.voxio.common.core.domain.model.RegisterBody;
import com.voxio.common.utils.DateUtils;
import com.voxio.common.utils.MessageUtils;
import com.voxio.common.utils.SecurityUtils;
import com.voxio.common.utils.StringUtils;
import com.voxio.framework.manager.AsyncManager;
import com.voxio.framework.manager.factory.AsyncFactory;
import com.voxio.system.service.ISysUserService;

/**
 * 注册校验方法
 * 
 * @author voxio
 */
@Component
@RequiredArgsConstructor
public class SysRegisterService
{
    private final ISysUserService userService;

    /**
     * 注册
     */
    public String register(RegisterBody registerBody)
    {
        String msg = "", username = registerBody.getUsername(), password = registerBody.getPassword();
        SysUser sysUser = new SysUser();
        sysUser.setUserName(username);

        if (StringUtils.isEmpty(username))
        {
            msg = "用户名不能为空";
        }
        else if (StringUtils.isEmpty(password))
        {
            msg = "用户密码不能为空";
        }
        else if (username.length() < UserConstants.USERNAME_MIN_LENGTH
                || username.length() > UserConstants.USERNAME_MAX_LENGTH)
        {
            msg = "账户长度必须在2到20个字符之间";
        }
        else if (password.length() < UserConstants.PASSWORD_MIN_LENGTH
                || password.length() > UserConstants.PASSWORD_MAX_LENGTH)
        {
            msg = "密码长度必须在5到20个字符之间";
        }
        else if (!userService.checkUserNameUnique(sysUser))
        {
            msg = "保存用户'" + username + "'失败，注册账号已存在";
        }
        else
        {
            sysUser.setNickName(username);
            sysUser.setPwdUpdateDate(DateUtils.getNowDate());
            sysUser.setPassword(SecurityUtils.encryptPassword(password));
            boolean regFlag = userService.registerUser(sysUser);
            if (!regFlag)
            {
                msg = "注册失败,请联系系统管理人员";
            }
            else
            {
                AsyncManager.me().execute(AsyncFactory.recordLogininfor(username, Constants.REGISTER, MessageUtils.message("user.register.success")));
            }
        }
        return msg;
    }
}
