package com.voxio.kf.controller;

import java.util.Map;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.voxio.common.core.controller.BaseController;
import com.voxio.common.core.domain.AjaxResult;
import com.voxio.kf.service.IKfSecurityService;

import lombok.RequiredArgsConstructor;

/**
 * 国密加密合规（管理后台）
 *
 * 只读诊断接口：暴露加密开关、算法、SM2 公钥与三端互通固定向量，用于交付前自检与门禁比对；
 * 不下发私钥、不下发主密钥，也不回传任何解密明文（互通校验只回答是/否，见 /verify-cipher）。
 * 权限沿用业务设置的 kf:settings:list，安全参数本就是业务设置的一部分，不额外造菜单授权。
 *
 * @author voxio
 */
@RestController
@RequestMapping("/kf/security")
@RequiredArgsConstructor
public class KfSecurityController extends BaseController
{
    private final IKfSecurityService securityService;

    /**
     * 当前加密策略
     */
    @PreAuthorize("@ss.hasPermi('kf:settings:list')")
    @GetMapping("/status")
    public AjaxResult status()
    {
        return success(securityService.publicStatus());
    }

    /**
     * 跨端互通固定向量（Java 侧计算结果，供前端与客服端逐字段比对）
     */
    @PreAuthorize("@ss.hasPermi('kf:settings:list')")
    @GetMapping("/vectors")
    public AjaxResult vectors()
    {
        return success(securityService.vectors());
    }

    /**
     * 验签：客户端用向量固定私钥对固定消息签的名，服务端验；反向由 vectors 里的 sm2SignatureHex 交给客户端验
     */
    @PreAuthorize("@ss.hasPermi('kf:settings:list')")
    @PostMapping("/verify-signature")
    public AjaxResult verifySignature(@RequestBody Map<String, String> body)
    {
        return success(securityService.verifySignature(body == null ? null : body.get("signatureHex")));
    }

    /**
     * 互通自检：客户端用向量公钥加密固定消息交回来，服务端只回答「是不是那句话」
     *
     * 用的是 GM/T 0003 公开的示例密钥，且返回值是布尔而不是明文，因此不构成解密预言机；
     * 生产会话密钥的解密路径永远只在网关内部，不暴露任何 HTTP 入口。
     */
    @PreAuthorize("@ss.hasPermi('kf:settings:list')")
    @PostMapping("/verify-cipher")
    public AjaxResult verifyCipher(@RequestBody Map<String, String> body)
    {
        return success(securityService.verifyVectorCipher(body == null ? null : body.get("cipherHex")));
    }
}
