package com.voxio.system.mapper;

import java.util.List;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.voxio.system.domain.SysNotice;

/**
 * 通知公告表 数据层
 * 
 * @author voxio
 */
public interface SysNoticeMapper extends BaseMapper<SysNotice>
{
    /**
     * 查询公告列表（含模糊检索条件，保留自定义 SQL）
     * 
     * @param notice 公告信息
     * @return 公告集合
     */
    public List<SysNotice> selectNoticeList(SysNotice notice);
}
