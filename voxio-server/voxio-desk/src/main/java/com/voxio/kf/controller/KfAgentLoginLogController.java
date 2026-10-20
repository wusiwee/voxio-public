package com.voxio.kf.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.voxio.common.core.controller.BaseController;
import com.voxio.common.core.page.TableDataInfo;
import com.voxio.kf.domain.KfAgentLoginLog;
import com.voxio.kf.service.IKfAgentLoginLogService;
import com.voxio.kf.util.KfQuerySupport;

import lombok.RequiredArgsConstructor;

/**
 * 客服登录日志（管理后台，只读）
 * 
 * 阶段10.4：登录取消图形验证码后，这份留痕是唯一的爆破追溯线索，因此只查不改不删，
 * 清理统一交给 KfLoginLogCleanupJob 按保留期处理。
 * 
 * @author voxio
 */
@RestController
@RequestMapping("/kf/loginlog")
@RequiredArgsConstructor
public class KfAgentLoginLogController extends BaseController
{
    private final IKfAgentLoginLogService loginLogService;

    @PreAuthorize("@ss.hasPermi('kf:loginlog:list')")
    @GetMapping("/list")
    public TableDataInfo list(@RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String beginTime,
            @RequestParam(required = false) String endTime)
    {
        startPage();
        List<KfAgentLoginLog> list = loginLogService.selectLogList(keyword, status,
                KfQuerySupport.begin(beginTime), KfQuerySupport.end(endTime));
        return getDataTable(list);
    }
}
