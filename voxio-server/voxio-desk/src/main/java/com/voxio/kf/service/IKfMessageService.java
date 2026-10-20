package com.voxio.kf.service;

import java.util.List;

import com.baomidou.mybatisplus.spring.service.IService;
import com.voxio.kf.domain.KfMessage;

/**
 * 消息 服务层
 * 
 * 负责序号分配、幂等落库、已读标记与增量拉取；网关只负责投递。
 * 
 * @author voxio
 */
public interface IKfMessageService extends IService<KfMessage>
{
    /**
     * 落库一条消息（同一事务内递增会话序号，按 clientMsgId 幂等）
     * 
     * @param sessionId 会话ID
     * @param senderType 发送方类型（1访客 2客服 3系统）
     * @param senderId 发送方ID
     * @param msgType 消息类型
     * @param content 文本内容
     * @param clientMsgId 客户端幂等标识，可为空
     * @return 消息（含 seq、msgId）；若命中幂等返回已存在的消息，并在 duplicated 标记下由调用方判断
     */
    public KfMessage appendMessage(Long sessionId, String senderType, Long senderId, String msgType,
            String content, String clientMsgId);

    /**
     * 网关实时消息落库（阶段11）。
     *
     * 与 appendMessage 唯一区别：会话侧的「序号 + 未读 + 首次响应 + 活跃时间」用一条 UPDATE 写完，
     * 少两次公网数据库往返。未读与首响的判定口径与调用方原先自己调 bumpUnread / markFirstResponse 完全一致，
     * 所以调用方（网关）不再需要另外发这两条语句。
     */
    public KfMessage appendRealtimeMessage(Long sessionId, String senderType, Long senderId, String msgType,
            String content, String clientMsgId);

    /**
     * 追加一条系统消息
     * 
     * @param sessionId 会话ID
     * @param content 内容
     * @param msgType 消息类型
     * @return 消息
     */
    public KfMessage appendSystemMessage(Long sessionId, String content, String msgType);

    /**
     * 按 clientMsgId 查询已存在消息（幂等命中判断）
     * 
     * @param sessionId 会话ID
     * @param clientMsgId 客户端幂等标识
     * @return 消息，不存在返回空
     */
    public KfMessage findByClientMsgId(Long sessionId, String clientMsgId);

    /**
     * 查询会话内 seq 大于 sinceSeq 的消息（离线补发）
     * 
     * @param sessionId 会话ID
     * @param sinceSeq 起始序号（不含）
     * @param limit 最大条数
     * @return 消息集合，按 seq 升序
     */
    public List<KfMessage> listAfter(Long sessionId, int sinceSeq, int limit);

    /**
     * 分页查询会话历史（向前翻页）
     * 
     * @param sessionId 会话ID
     * @param beforeSeq 上界序号（不含），空表示最新
     * @param limit 最大条数
     * @return 消息集合，按 seq 升序
     */
    public List<KfMessage> listBefore(Long sessionId, Integer beforeSeq, int limit);

    /**
     * 标记某发送方的消息已送达
     * 
     * @param sessionId 会话ID
     * @param senderType 消息发送方类型
     * @return 影响行数
     */
    public int markSent(Long sessionId, String senderType);

    /**
     * 标记某发送方的消息已读
     * 
     * @param sessionId 会话ID
     * @param peerSenderType 被阅读消息的发送方类型
     * @param lastSeq 已读到的序号
     * @return 影响行数
     */
    public int markRead(Long sessionId, String peerSenderType, Integer lastSeq);

    /**
     * 统计未送达消息数
     * 
     * @param sessionId 会话ID
     * @param peerSenderType 消息发送方类型
     * @return 数量
     */
    public long countUndelivered(Long sessionId, String peerSenderType);
}
