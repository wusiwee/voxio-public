package com.voxio.kf.service;

import java.util.List;

import com.baomidou.mybatisplus.spring.service.IService;
import com.voxio.kf.domain.KfSession;

/**
 * 会话 服务层
 * 
 * 负责会话创建/恢复、排队与分配、结束；所有在线推送通过 KfChatEventPublisher 交给网关。
 * 
 * @author voxio
 */
public interface IKfSessionService extends IService<KfSession>
{
    /** 结束方：访客 */
    public static final String END_BY_VISITOR = "1";

    /** 结束方：客服 */
    public static final String END_BY_AGENT = "2";

    /** 结束方：系统 */
    public static final String END_BY_SYSTEM = "3";

    /**
     * 创建或恢复访客会话
     * 
     * @param visitorNo 访客标识
     * @param nickname 访客昵称，可为空
     * @param ip 访客IP
     * @param userAgent 浏览器信息
     * @param referrer 来源页面
     * @param sourcePage 发起页面
     * @return 会话（已分配或排队中）
     */
    public KfSession createOrResume(String visitorNo, String nickname, String ip, String userAgent,
            String referrer, String sourcePage);

    /**
     * 查询会话，不存在返回空
     * 
     * @param sessionId 会话ID
     * @return 会话
     */
    public KfSession getSession(Long sessionId);

    /**
     * 查询访客当前进行中的会话
     * 
     * @param visitorId 访客ID
     * @return 会话
     */
    public KfSession findActiveByVisitor(Long visitorId);

    /**
     * 查询客服的会话列表
     * 
     * @param agentId 客服ID
     * @param status 会话状态，空表示进行中（排队+接待）
     * @return 会话集合
     */
    public List<KfSession> listByAgent(Long agentId, String status);

    /**
     * 会话在排队队列中的位置（非排队会话返回 0）
     * 
     * @param session 会话
     * @return 位置
     */
    public int queuePosition(KfSession session);

    /**
     * 当前排队中的会话数（访客端用于展示「正在排队的人数」）
     * 
     * @return 排队人数
     */
    public int countQueued();

    /**
     * 为会话尝试分配可用客服（空闲优先）
     * 
     * @param sessionId 会话ID
     * @return 分配成功返回会话，无可用客服返回空
     */
    public KfSession assignAvailable(Long sessionId);

    /**
     * 人工分配排队中的会话给指定客服（关闭自动分配时由主管在会话监控操作）
     * 
     * @param sessionId 会话ID
     * @param agentId 目标客服ID
     * @param operator 操作人
     * @return 分配后的会话
     */
    public KfSession assignManually(Long sessionId, Long agentId, String operator);

    /**
     * 客服自己接入一条排队会话（客服端「一键接入」，不需要主管在场）
     *
     * 与人工分配的分工：主管替别人指派，客服自己接管。会话标识走路径而不是让服务端挑队首，
     * 是为了审计能答出「谁在什么时候接了哪条会话」——无参接口的留痕只能记一个动作，记不出对象。
     * 接入哪条由客服在排队列表里选（客服端默认给等待最久的那条），判定与人工分配完全一致。
     * 
     * @param sessionId 要接管的排队会话
     * @param agentId 操作的客服ID
     * @param operator 操作人（写进会话事件消息与留痕）
     * @return 接入后的会话
     */
    public KfSession acceptSession(Long sessionId, Long agentId, String operator);

    /**
     * 客服空闲时从队列拉取会话（上线、结束会话后调用）
     * 
     * @param agentId 客服ID
     * @return 本次分配的会话数
     */
    public int pullQueuedForAgent(Long agentId);

    /**
     * 重新开启自动分配后立刻排空队列：逐个在线客服按剩余容量拉取排队会话
     * 
     * @return 本次分配的会话数
     */
    public int assignQueuedToOnlineAgents();

    /**
     * 结束会话
     * 
     * @param sessionId 会话ID
     * @param endBy 结束方（1访客 2客服 3系统）
     * @param operatorAgentId 操作客服ID，访客结束可为空
     * @param reason 结束原因
     */
    public void endSession(Long sessionId, String endBy, Long operatorAgentId, String reason);

    /**
     * 客服会话列表视图（含访客昵称与最后一条消息摘要）
     * 
     * @param agentId 客服ID
     * @param status 会话状态，空表示进行中
     * @return 视图集合
     */
    public java.util.List<java.util.Map<String, Object>> listAgentSessionView(Long agentId, String status);

    /**
     * 客服历史会话分页查询（时间、访客关键字、状态筛选）
     * 
     * @param agentId 客服ID
     * @param status 会话状态，空表示全部
     * @param keyword 访客昵称/访客标识/会话编号
     * @param beginTime 创建时间下界
     * @param endTime 创建时间上界
     * @return 视图集合
     */
    public java.util.List<java.util.Map<String, Object>> listAgentHistory(Long agentId, String status,
            String keyword, java.util.Date beginTime, java.util.Date endTime);

    /**
     * 对方新增一条未读消息
     * 
     * @param sessionId 会话ID
     * @param senderType 消息发送方类型（1访客 2客服）
     */
    public void bumpUnread(Long sessionId, String senderType);

    /**
     * 阅读会话内对方消息，清零本方未读
     * 
     * @param sessionId 会话ID
     * @param readerType 阅读方类型（1访客 2客服）
     * @param lastSeq 已读到的序号
     * @return 标记条数
     */
    public int markRead(Long sessionId, String readerType, Integer lastSeq);

    /**
     * 记录客服首次响应时间（仅第一次生效）
     * 
     * @param sessionId 会话ID
     */
    public void markFirstResponse(Long sessionId);

    /**
     * 客服在线状态变更（网关调用）
     * 
     * @param agentId 客服ID
     * @param onlineStatus 在线状态（0离线 1在线 2忙碌）
     */
    public void changeAgentOnlineStatus(Long agentId, String onlineStatus);
}
