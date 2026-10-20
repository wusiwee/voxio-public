package com.voxio.kf.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.voxio.common.annotation.Log;
import com.voxio.common.constant.HttpStatus;
import com.voxio.common.core.controller.BaseController;
import com.voxio.common.core.domain.AjaxResult;
import com.voxio.common.core.page.PageDomain;
import com.voxio.common.core.page.TableDataInfo;
import com.voxio.common.core.page.TableSupport;
import com.voxio.common.enums.BusinessType;
import com.voxio.common.utils.StringUtils;
import com.voxio.kf.domain.KfAgent;
import com.voxio.kf.service.IKfAgentService;

import lombok.RequiredArgsConstructor;

/**
 * 客服账号管理（管理后台使用，管理员 sys_user + kf:agent:* 权限）
 * 
 * @author voxio
 */
@RestController
@RequestMapping("/kf/agent")
@RequiredArgsConstructor
public class KfAgentController extends BaseController
{
    private final IKfAgentService agentService;

    /**
     * 获取客服账号列表
     */
    @PreAuthorize("@ss.hasPermi('kf:agent:list')")
    @GetMapping("/list")
    public TableDataInfo list(KfAgent agent)
    {
        // 手机号密文落库，SQL 的 LIKE 打不到：这条筛选走「解密后内存过滤 + 手工分页」，
        // 其余条件仍由 PageHelper 在库里分页（客服账号量级小，内存过滤只影响这一个查询框）
        if (StringUtils.isNotEmpty(agent.getPhone()))
        {
            return pageByPhone(agent);
        }
        startPage();
        List<KfAgent> list = agentService.selectAgentList(agent);
        return getDataTable(list);
    }

    private TableDataInfo pageByPhone(KfAgent agent)
    {
        List<KfAgent> matched = agentService.selectAgentListByPhoneLike(agent);
        PageDomain pageDomain = TableSupport.buildPageRequest();
        int pageNum = pageDomain.getPageNum() == null ? 1 : pageDomain.getPageNum();
        int pageSize = pageDomain.getPageSize() == null ? 10 : pageDomain.getPageSize();
        int from = Math.max(0, Math.min((pageNum - 1) * pageSize, matched.size()));
        int to = Math.max(from, Math.min(from + pageSize, matched.size()));
        TableDataInfo info = new TableDataInfo();
        info.setCode(HttpStatus.SUCCESS);
        info.setMsg("查询成功");
        info.setRows(matched.subList(from, to));
        info.setTotal(matched.size());
        return info;
    }

    /**
     * 根据客服编号获取详细信息
     */
    @PreAuthorize("@ss.hasPermi('kf:agent:query')")
    @GetMapping(value = "/{agentId}")
    public AjaxResult getInfo(@PathVariable Long agentId)
    {
        return success(agentService.getById(agentId));
    }

    /**
     * 新增客服账号
     */
    @PreAuthorize("@ss.hasPermi('kf:agent:add')")
    @Log(title = "客服账号", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody KfAgent agent)
    {
        return toAjax(agentService.insertAgent(agent));
    }

    /**
     * 修改客服账号
     */
    @PreAuthorize("@ss.hasPermi('kf:agent:edit')")
    @Log(title = "客服账号", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody KfAgent agent)
    {
        return toAjax(agentService.updateAgent(agent));
    }

    /**
     * 删除客服账号（逻辑删除）
     */
    @PreAuthorize("@ss.hasPermi('kf:agent:remove')")
    @Log(title = "客服账号", businessType = BusinessType.DELETE)
    @DeleteMapping("/{agentIds}")
    public AjaxResult remove(@PathVariable Long[] agentIds)
    {
        return toAjax(agentService.deleteAgentByIds(agentIds));
    }

    /**
     * 重置客服登录密码
     */
    @PreAuthorize("@ss.hasPermi('kf:agent:resetPwd')")
    @Log(title = "客服账号", businessType = BusinessType.UPDATE)
    @PutMapping("/resetPwd")
    public AjaxResult resetPwd(@RequestBody KfAgent agent)
    {
        return toAjax(agentService.resetPwd(agent.getAgentId(), agent.getPassword()));
    }

    /**
     * 停用/启用客服账号
     */
    @PreAuthorize("@ss.hasPermi('kf:agent:changeStatus')")
    @Log(title = "客服账号", businessType = BusinessType.UPDATE)
    @PutMapping("/changeStatus")
    public AjaxResult changeStatus(@RequestBody KfAgent agent)
    {
        return toAjax(agentService.changeStatus(agent));
    }

    /**
     * 强制客服下线（清除登录态并置为离线，会话归属不变）
     */
    @PreAuthorize("@ss.hasPermi('kf:agent:forceOffline')")
    @Log(title = "客服账号", businessType = BusinessType.UPDATE)
    @PutMapping("/forceOffline/{agentId}")
    public AjaxResult forceOffline(@PathVariable Long agentId)
    {
        return toAjax(agentService.forceOffline(agentId));
    }
}
