package com.voxio.web.controller.common;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.voxio.common.constant.CacheConstants;
import com.voxio.common.constant.Constants;
import com.voxio.common.core.domain.AjaxResult;
import com.voxio.common.core.redis.RedisCache;
import com.voxio.common.utils.uuid.IdUtils;
import com.voxio.system.service.ISysConfigService;
import com.voxio.framework.captcha.VoxioArithmeticCaptcha;

import lombok.RequiredArgsConstructor;

/**
 * 生成验证码（阶段8：easy-captcha 彩色算术验证码，替换原 Kaptcha 实现）
 *
 * 阶段10.4 起登录链路不再校验验证码，本接口按 sys.account.captchaEnabled 出图：
 * 参数默认已置 false，因此只回 captchaEnabled=false；万一要兼容仍在用验证码的旧客户端，
 * 把该参数改回 true 即可恢复出图与 Redis 存答，登录侧仍不会拦（校验点已摘除）。
 *
 * @author voxio
 */
@RestController
@RequiredArgsConstructor
public class CaptchaController
{
    private final RedisCache redisCache;

    private final ISysConfigService configService;

    /**
     * 生成算术验证码
     * 
     * 接口路径 / captchaEnabled / uuid / img 字段、Redis key 与 2 分钟过期时间与改造前完全一致，
     * 前端只需把 img 直接当图片地址使用：toBase64() 已带 data:image/png;base64, 前缀。
     * 文字颜色由 easy-captcha 内置调色板逐次随机，即“彩色”效果，无需额外配置。
     */
    @GetMapping("/captchaImage")
    public AjaxResult getCode() throws IOException
    {
        AjaxResult ajax = AjaxResult.success();
        boolean captchaEnabled = configService.selectCaptchaEnabled();
        ajax.put("captchaEnabled", captchaEnabled);
        if (!captchaEnabled)
        {
            return ajax;
        }

        // 保存验证码信息
        String uuid = IdUtils.simpleUUID();
        String verifyKey = CacheConstants.CAPTCHA_CODE_KEY + uuid;

        // 生成验证码：115x42，两个一位数加减乘运算，text() 返回算式的计算结果
        VoxioArithmeticCaptcha captcha = new VoxioArithmeticCaptcha(115, 42, 2);
        String code = captcha.text();
        redisCache.setCacheObject(verifyKey, code, Constants.CAPTCHA_EXPIRATION, TimeUnit.MINUTES);

        ajax.put("uuid", uuid);
        ajax.put("img", captcha.toBase64());
        return ajax;
    }
}
