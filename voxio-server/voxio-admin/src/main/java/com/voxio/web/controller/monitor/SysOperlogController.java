package com.voxio.web.controller.monitor;

import java.util.List;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.voxio.common.annotation.Log;
import com.voxio.common.core.controller.BaseController;
import com.voxio.common.core.page.TableDataInfo;
import com.voxio.common.enums.BusinessType;
import com.voxio.common.utils.poi.ExcelUtil;
import com.voxio.system.domain.SysOperLog;
import com.voxio.system.service.ISysOperLogService;
import lombok.RequiredArgsConstructor;

/**
 * 操作日志记录
 *
 * 阶段10.5 P0-3：审计留痕必须防删除。原本这里带 {@code DELETE /{operIds}} 与 {@code DELETE /clean}
 * 两个入口（若依默认），任何拿到 monitor:operlog:remove 的人都能抹掉自己的操作记录，
 * 等保三级「审计记录防篡改/防删除」直接不过，故两个写入口整体摘除，页面只查只导。
 * 留存期由 KfAuditRetentionJob 按 ≥180 天下限统一收敛，不给人工清空的口子。
 *
 * @author voxio
 */
@RestController
@RequestMapping("/monitor/operlog")
@RequiredArgsConstructor
public class SysOperlogController extends BaseController
{
    private final ISysOperLogService operLogService;

    @PreAuthorize("@ss.hasPermi('monitor:operlog:list')")
    @GetMapping("/list")
    public TableDataInfo list(SysOperLog operLog)
    {
        startPage();
        List<SysOperLog> list = operLogService.selectOperLogList(operLog);
        return getDataTable(list);
    }

    @Log(title = "操作日志", businessType = BusinessType.EXPORT)
    @PreAuthorize("@ss.hasPermi('monitor:operlog:export')")
    @PostMapping("/export")
    public void export(HttpServletResponse response, SysOperLog operLog)
    {
        List<SysOperLog> list = operLogService.selectOperLogList(operLog);
        ExcelUtil<SysOperLog> util = new ExcelUtil<SysOperLog>(SysOperLog.class);
        util.exportExcel(response, list, "操作日志");
    }
}
