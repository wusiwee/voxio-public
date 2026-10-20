package com.voxio.framework.security.handle;
import lombok.RequiredArgsConstructor;

import java.io.IOException;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import com.alibaba.fastjson2.JSON;
import com.voxio.common.constant.Constants;
import com.voxio.common.core.domain.AjaxResult;
import com.voxio.common.core.domain.model.LoginUser;
import com.voxio.common.utils.MessageUtils;
import com.voxio.common.utils.ServletUtils;
import com.voxio.common.utils.StringUtils;
import com.voxio.framework.manager.AsyncManager;
import com.voxio.framework.manager.factory.AsyncFactory;
import com.voxio.framework.web.service.TokenService;

/**
 * 自定义退出处理类 返回成功
 * 
 * @author voxio
 */
@Configuration
@RequiredArgsConstructor
public class LogoutSuccessHandlerImpl implements LogoutSuccessHandler
{
    private final TokenService tokenService;

    /**
     * 退出处理
     * 
     * @return
     */
    @Override
    public void onLogoutSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication)
            throws IOException, ServletException
    {
        LoginUser loginUser = tokenService.getLoginUser(request);
        if (StringUtils.isNotNull(loginUser))
        {
            String userName = loginUser.getUsername();
            // 删除用户缓存记录
            tokenService.delLoginUser(loginUser.getToken());
            // 记录用户退出日志
            AsyncManager.me().execute(AsyncFactory.recordLogininfor(userName, Constants.LOGOUT, MessageUtils.message("user.logout.success")));
        }
        ServletUtils.renderString(response, JSON.toJSONString(AjaxResult.success(MessageUtils.message("user.logout.success"))));
    }
}
