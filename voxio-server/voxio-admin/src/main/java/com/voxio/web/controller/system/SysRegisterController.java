package com.voxio.web.controller.system;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import com.voxio.common.core.controller.BaseController;
import com.voxio.common.core.domain.AjaxResult;
import com.voxio.common.core.domain.model.RegisterBody;
import com.voxio.common.utils.StringUtils;
import com.voxio.framework.web.service.SysRegisterService;
import com.voxio.system.service.ISysConfigService;
import lombok.RequiredArgsConstructor;

/**
 * 注册验证
 * 
 * @author voxio
 */
@RestController
@RequiredArgsConstructor
public class SysRegisterController extends BaseController
{
    private final SysRegisterService registerService;

    private final ISysConfigService configService;

    @PostMapping("/register")
    public AjaxResult register(@RequestBody RegisterBody user)
    {
        if (!("true".equals(configService.selectConfigByKey("sys.account.registerUser"))))
        {
            return error("当前系统没有开启注册功能！");
        }
        String msg = registerService.register(user);
        return StringUtils.isEmpty(msg) ? success() : error(msg);
    }
}
