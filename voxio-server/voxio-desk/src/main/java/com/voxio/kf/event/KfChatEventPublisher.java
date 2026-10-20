package com.voxio.kf.event;

/**
 * 网关事件投递器
 * 
 * 由 voxio-chat-netty 提供实现；kf 业务层只依赖接口，避免反向依赖网关模块。
 * 
 * @author voxio
 */
public interface KfChatEventPublisher
{
    /**
     * 向指定客服投递事件，客服离线时静默丢弃（消息本身已落库，上线后通过 SYNC 补发）
     * 
     * @param agentId 客服ID
     * @param event 事件
     */
    public void publishToAgent(Long agentId, KfChatEvent event);

    /**
     * 向指定访客投递事件
     * 
     * @param visitorId 访客ID
     * @param event 事件
     */
    public void publishToVisitor(Long visitorId, KfChatEvent event);

    /**
     * 客服是否在线
     * 
     * @param agentId 客服ID
     * @return 是否在线
     */
    public boolean isAgentOnline(Long agentId);

    /**
     * 访客是否在线
     * 
     * @param visitorId 访客ID
     * @return 是否在线
     */
    public boolean isVisitorOnline(Long visitorId);
}
