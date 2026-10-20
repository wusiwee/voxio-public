package com.voxio.web.controller.system;

import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.voxio.common.config.VoxioConfig;
import com.voxio.common.core.domain.AjaxResult;
import com.voxio.common.core.domain.entity.SysUser;
import com.voxio.common.utils.SecurityUtils;
import com.voxio.common.utils.StringUtils;
import com.voxio.system.service.ISysUserService;
import lombok.RequiredArgsConstructor;

/**
 * 首页
 *
 * @author voxio
 */
@RestController
@RequiredArgsConstructor
public class SysIndexController
{
    /** 系统基础配置 */
    private final VoxioConfig voxioConfig;

    private final ISysUserService userService;

    /**
     * 访问首页，提示语
     */
    @RequestMapping("/")
    public String index()
    {
        return StringUtils.format("VOXIO 在线客服系统 v{} 服务端已就绪，请通过前端地址访问。", voxioConfig.getVersion());
    }

    /**
     * 解锁屏幕
     */
    @PostMapping("/unlockscreen")
    public AjaxResult unlockScreen(@RequestBody Map<String, String> body)
    {
        String password = body.get("password");
        if (StringUtils.isEmpty(password))
        {
            return AjaxResult.error("密码不能为空");
        }
        String username = SecurityUtils.getUsername();
        SysUser user = userService.selectUserByUserName(username);
        if (user == null)
        {
            return AjaxResult.error("服务器超时，请重新登录");
        }
        if (!SecurityUtils.matchesPassword(password, user.getPassword()))
        {
            return AjaxResult.error("密码错误，请重新输入");
        }

        return AjaxResult.success("解锁成功");
    }
}
