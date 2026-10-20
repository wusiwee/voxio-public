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
import com.voxio.common.core.controller.BaseController;
import com.voxio.common.core.domain.AjaxResult;
import com.voxio.common.core.page.TableDataInfo;
import com.voxio.common.enums.BusinessType;
import com.voxio.kf.domain.KfConfig;
import com.voxio.kf.service.IKfConfigService;

import lombok.RequiredArgsConstructor;

/**
 * 客服业务参数配置（管理后台）
 * 
 * @author voxio
 */
@RestController
@RequestMapping("/kf/config")
@RequiredArgsConstructor
public class KfConfigController extends BaseController
{
    private final IKfConfigService configService;

    /**
     * 查询参数列表
     */
    @PreAuthorize("@ss.hasPermi('kf:config:list')")
    @GetMapping("/list")
    public TableDataInfo list(KfConfig config)
    {
        startPage();
        List<KfConfig> list = configService.selectConfigList(config);
        return getDataTable(list);
    }

    /**
     * 根据参数键名查询参数值
     */
    @PreAuthorize("@ss.hasPermi('kf:config:query')")
    @GetMapping("/configKey/{configKey}")
    public AjaxResult getConfigKey(@PathVariable String configKey)
    {
        // 显式转 Object，避免命中 AjaxResult.success(String msg) 重载导致取值落到 msg 字段
        return success((Object) configService.selectConfigByKey(configKey));
    }

    /**
     * 参数详情
     */
    @PreAuthorize("@ss.hasPermi('kf:config:query')")
    @GetMapping("/{configId}")
    public AjaxResult getInfo(@PathVariable Long configId)
    {
        return success(configService.getById(configId));
    }

    /**
     * 新增参数
     */
    @PreAuthorize("@ss.hasPermi('kf:config:add')")
    @Log(title = "客服参数", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody KfConfig config)
    {
        return toAjax(configService.insertConfig(config));
    }

    /**
     * 修改参数
     */
    @PreAuthorize("@ss.hasPermi('kf:config:edit')")
    @Log(title = "客服参数", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody KfConfig config)
    {
        return toAjax(configService.updateConfig(config));
    }

    /**
     * 删除参数
     */
    @PreAuthorize("@ss.hasPermi('kf:config:remove')")
    @Log(title = "客服参数", businessType = BusinessType.DELETE)
    @DeleteMapping("/{configId}")
    public AjaxResult remove(@PathVariable Long configId)
    {
        return toAjax(configService.deleteConfigById(configId));
    }

    /**
     * 刷新参数缓存
     */
    @PreAuthorize("@ss.hasPermi('kf:config:remove')")
    @Log(title = "客服参数", businessType = BusinessType.CLEAN)
    @DeleteMapping("/refreshCache")
    public AjaxResult refreshCache()
    {
        configService.resetConfigCache();
        return success();
    }
}
