package com.voxio.kf.controller;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.voxio.common.annotation.Log;
import com.voxio.common.core.controller.BaseController;
import com.voxio.common.core.domain.AjaxResult;
import com.voxio.common.core.domain.entity.SysUser;
import com.voxio.common.core.page.TableDataInfo;
import com.voxio.common.enums.BusinessType;
import com.voxio.common.utils.poi.ExcelUtil;
import com.voxio.common.utils.DateUtils;
import com.voxio.kf.domain.dto.KfTicketExport;
import com.voxio.common.exception.ServiceException;
import com.voxio.common.utils.SecurityUtils;
import com.voxio.common.utils.StringUtils;
import com.voxio.kf.domain.KfTicket;
import com.voxio.kf.domain.KfTicketReply;
import com.voxio.kf.service.IKfMonitorService;
import com.voxio.kf.service.IKfTicketService;
import com.voxio.kf.util.KfQuerySupport;

import lombok.RequiredArgsConstructor;

/**
 * 工单管理（管理后台）
 * 
 * 工单独立管理，只做简单状态流转：待处理→处理中→已解决→已关闭，不含工作流引擎。
 * 
 * @author voxio
 */
@RestController
@RequestMapping("/kf/ticket")
@RequiredArgsConstructor
public class KfTicketController extends BaseController
{
    /** 单次导出的行数上限（超过即视为筛选条件写错，不给出「导出全表」的口子） */
    private static final int EXPORT_ROW_LIMIT = 50000;

    private final IKfTicketService ticketService;

    private final IKfMonitorService monitorService;

    @PreAuthorize("@ss.hasPermi('kf:ticket:list')")
    @GetMapping("/list")
    public TableDataInfo list(KfTicket query, @RequestParam(required = false) String beginTime,
            @RequestParam(required = false) String endTime)
    {
        startPage();
        List<Map<String, Object>> list = ticketService.selectForAdmin(query, KfQuerySupport.begin(beginTime),
                KfQuerySupport.end(endTime));
        return getDataTable(list);
    }

    /**
     * 工单导出（服务端出表，带审计）
     *
     * 权限沿用 `kf:ticket:list`：导出的列就是列表的列，能看列表就能导，另设一个 export 权限只会造出
     * 「有授权无实现」或「有实现无授权」的口径差。真正新增的是留痕——@Log(EXPORT) 让每次导出
     * 都在操作日志与业务审计里各留一条，谁在什么时候把业务数据带出系统可以追溯。
     * 行数上限是防手滑：不带筛选导全量在五万条以内是正常运营，超了说明条件写错了。
     */
    @PreAuthorize("@ss.hasPermi('kf:ticket:list')")
    @Log(title = "工单导出", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, KfTicket query, @RequestParam(required = false) String beginTime,
            @RequestParam(required = false) String endTime)
    {
        List<Map<String, Object>> rows = ticketService.selectForAdmin(query, KfQuerySupport.begin(beginTime),
                KfQuerySupport.end(endTime));
        if (rows.size() > EXPORT_ROW_LIMIT)
        {
            throw new ServiceException("导出行数 " + rows.size() + " 条，超过 " + EXPORT_ROW_LIMIT
                    + " 条上限，请收窄筛选条件");
        }
        List<KfTicketExport> list = rows.stream().map(KfTicketExport::from).toList();
        // ExcelUtil 只设 content-type，不带文件名；这里补 Content-Disposition，
        // 让 curl / 浏览器直连这类不走前端 download() 命名的调用方也能拿到有意义的文件名
        String stamp = DateUtils.dateTimeNow("yyyyMMddHHmmss");
        String fileName = "工单数据_" + stamp + ".xlsx";
        response.setHeader("Content-Disposition", "attachment; filename=kf-ticket-" + stamp
                + ".xlsx; filename*=UTF-8''" + URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20"));
        ExcelUtil<KfTicketExport> util = new ExcelUtil<KfTicketExport>(KfTicketExport.class);
        util.exportExcel(response, list, "工单数据");
    }

    /**
     * 工单详情：基础信息 + 流转与回复记录 + 关联会话摘要（只读）
     */
    @PreAuthorize("@ss.hasPermi('kf:ticket:query')")
    @GetMapping("/{ticketId}")
    public AjaxResult detail(@PathVariable Long ticketId)
    {
        KfTicket ticket = ticketService.getById(ticketId);
        if (StringUtils.isNull(ticket))
        {
            throw new ServiceException("工单不存在", 404);
        }
        AjaxResult ajax = AjaxResult.success();
        ajax.put("ticket", ticket);
        ajax.put("replies", ticketService.listReplies(ticketId));
        if (StringUtils.isNotNull(ticket.getSessionId()))
        {
            ajax.put("session", monitorService.selectSessionDetail(ticket.getSessionId()));
        }
        return ajax;
    }

    @PreAuthorize("@ss.hasPermi('kf:ticket:add')")
    @Log(title = "工单管理", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody KfTicket ticket)
    {
        KfTicket created = ticketService.createBy(ticket, KfTicket.CREATOR_ADMIN, SecurityUtils.getUserId(),
                adminName(), ticket.getHandlerAgentId());
        return success(created);
    }

    @PreAuthorize("@ss.hasPermi('kf:ticket:edit')")
    @Log(title = "工单管理", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody KfTicket ticket)
    {
        if (StringUtils.isNull(ticket.getTicketId()))
        {
            throw new ServiceException("缺少工单ID");
        }
        return success(ticketService.updateBasic(ticket, adminName(), SecurityUtils.getUserId(), true));
    }

    @PreAuthorize("@ss.hasPermi('kf:ticket:status')")
    @Log(title = "工单流转", businessType = BusinessType.UPDATE)
    @PutMapping("/{ticketId}/status")
    public AjaxResult changeStatus(@PathVariable Long ticketId, @RequestBody Map<String, String> body)
    {
        return success(ticketService.changeStatus(ticketId, body.get("status"), body.get("note"),
                SecurityUtils.getUserId(), adminName(), true));
    }

    /**
     * 追加工单回复（replyType：1对外回复 3内部备注）
     */
    @PreAuthorize("@ss.hasPermi('kf:ticket:edit')")
    @Log(title = "工单回复", businessType = BusinessType.INSERT)
    @PostMapping("/{ticketId}/reply")
    public AjaxResult reply(@PathVariable Long ticketId, @RequestBody Map<String, String> body)
    {
        KfTicketReply reply = ticketService.addReply(ticketId, body.get("content"), body.get("replyType"),
                adminName(), SecurityUtils.getUserId(), true);
        return success(reply);
    }

    /**
     * 手动同步工单到第三方系统（结果记在工单上，前端 Toast 展示）
     */
    @PreAuthorize("@ss.hasPermi('kf:ticket:edit')")
    @Log(title = "工单第三方推送", businessType = BusinessType.OTHER)
    @PostMapping("/{ticketId}/push")
    public AjaxResult push(@PathVariable Long ticketId)
    {
        return success(ticketService.pushToThirdParty(ticketId, adminName()));
    }

    @PreAuthorize("@ss.hasPermi('kf:ticket:remove')")
    @Log(title = "工单管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ticketIds}")
    public AjaxResult remove(@PathVariable Long[] ticketIds)
    {
        ticketService.removeAdmin(ticketIds);
        return success();
    }

    private String adminName()
    {
        SysUser user = SecurityUtils.getLoginUser().getUser();
        return StringUtils.isNotNull(user) && StringUtils.isNotEmpty(user.getNickName()) ? user.getNickName()
                : SecurityUtils.getUsername();
    }
}
