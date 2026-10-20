package com.voxio.kf.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.voxio.common.core.controller.BaseController;
import com.voxio.common.core.page.TableDataInfo;
import com.voxio.kf.domain.KfAuditLog;
import com.voxio.kf.service.IKfAuditLogService;
import com.voxio.kf.util.KfQuerySupport;

import lombok.RequiredArgsConstructor;

/**
 * 业务审计留痕（管理后台，只读）
 *
 * 阶段10.5 P0-3：这张表只有查询映射，没有新增/修改/删除端点——审计证据的可信度来自「应用自己删不掉」。
 * 留存期由 KfAuditRetentionJob 按 ≥180 天下限统一收敛。
 *
 * @author voxio
 */
@RestController
@RequestMapping("/kf/auditlog")
@RequiredArgsConstructor
public class KfAuditLogController extends BaseController
{
    private final IKfAuditLogService auditLogService;

    @PreAuthorize("@ss.hasPermi('kf:auditlog:list')")
    @GetMapping("/list")
    public TableDataInfo list(@RequestParam(required = false) String keyword,
            @RequestParam(required = false) String userType,
            @RequestParam(required = false) String operation,
            @RequestParam(required = false) String beginTime,
            @RequestParam(required = false) String endTime)
    {
        startPage();
        List<KfAuditLog> list = auditLogService.selectLogList(keyword, userType, operation,
                KfQuerySupport.begin(beginTime), KfQuerySupport.end(endTime));
        return getDataTable(list);
    }
}
