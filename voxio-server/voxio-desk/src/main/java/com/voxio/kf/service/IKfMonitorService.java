package com.voxio.kf.service;

import java.util.List;
import java.util.Map;

/**
 * 管理后台会话监控 / 聊天记录 服务（只读）
 * 
 * @author voxio
 */
public interface IKfMonitorService
{
    /**
     * 会话列表（进行中或按状态筛选）
     */
    public List<Map<String, Object>> selectSessionList(String status, Long agentId, Long visitorId, String keyword,
            String beginTime, String endTime);

    /**
     * 会话详情（含访客、客服与最近消息）
     */
    public Map<String, Object> selectSessionDetail(Long sessionId);

    /**
     * 客服在线状态与实时负载
     */
    public List<Map<String, Object>> selectAgentStatus(String onlineStatus, String keyword);

    /**
     * 聊天记录分页查询
     */
    public List<Map<String, Object>> selectChatLog(Long sessionId, Long visitorId, Long agentId, String senderType,
            String msgType, String keyword, String beginTime, String endTime);

    /**
     * 聊天记录游标分页（阶段11）
     *
     * @param cursorMsgId 游标：只取 msg_id 小于它的行，null 表示从最新一条开始
     * @param pageSize 单页条数（调用方给，默认 30）
     */
    public List<Map<String, Object>> selectChatLogPage(Long sessionId, Long visitorId, Long agentId, String senderType,
            String msgType, String keyword, String beginTime, String endTime, Long cursorMsgId, int pageSize);

    /**
     * 单条消息详情
     */
    public Map<String, Object> selectMessage(Long msgId);
}
