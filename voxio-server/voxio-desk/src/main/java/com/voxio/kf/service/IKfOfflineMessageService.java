package com.voxio.kf.service;

import java.util.List;
import java.util.Map;

import com.baomidou.mybatisplus.spring.service.IService;
import com.voxio.kf.domain.KfOfflineMessage;
import com.voxio.kf.domain.KfTicket;

/**
 * 离线留言 服务层
 * 
 * @author voxio
 */
public interface IKfOfflineMessageService extends IService<KfOfflineMessage>
{
    /**
     * 当前是否处于服务时间内（未开启服务时间限制时恒为 true）
     * 
     * @return 是否在服务时间内
     */
    public boolean isInServiceTime();

    /**
     * 是否允许访客提交留言（留言开关 + 非服务时间或无客服在线）
     * 
     * @param onlineAgentCount 当前在线客服数
     * @return 是否允许留言
     */
    public boolean isOfflineAllowed(int onlineAgentCount);

    /**
     * 提交离线留言
     * 
     * @param visitorId 访客ID
     * @param sessionId 关联会话ID，可为空
     * @param contact 联系方式
     * @param content 留言内容
     * @return 留言ID
     */
    public Long submit(Long visitorId, Long sessionId, String contact, String content);

    /**
     * 后台留言列表
     */
    public List<Map<String, Object>> selectAdminList(String status, Long handlerAgentId, String keyword,
            String beginTime, String endTime);

    /**
     * 后台留言详情
     */
    public Map<String, Object> selectAdminDetail(Long msgId);

    /**
     * 留言转为工单（来源标记为离线留言，回写 ticket_id 与状态）
     * 
     * @param msgId 留言ID
     * @param handlerAgentId 指派客服ID，可为空
     * @param title 工单标题，为空时按留言内容自动生成
     * @param operatorId 操作管理员ID
     * @param operatorName 操作人名称
     * @return 新建工单
     */
    public KfTicket toTicket(Long msgId, Long handlerAgentId, String title, Long operatorId, String operatorName);

    /**
     * 直接关闭留言（无需建工单的场景）
     */
    public void close(Long msgId);
}
