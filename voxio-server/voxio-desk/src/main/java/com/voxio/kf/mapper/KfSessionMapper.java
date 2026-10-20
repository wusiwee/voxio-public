package com.voxio.kf.mapper;

import java.util.Date;
import java.util.List;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.voxio.kf.domain.KfSession;

/**
 * 会话 数据层
 * 
 * @author voxio
 */
public interface KfSessionMapper extends BaseMapper<KfSession>
{
    /**
     * 查询仍在排队或接待中、且最后活动时间早于 deadline 的会话（系统自动结束用）。
     * 最后活动时间取 last_msg_time，没有消息时退回分配/排队/创建时间。
     * 
     * @param deadline 活动截止时间
     * @param limit 单次最多处理条数
     * @return 会话集合
     */
    @Select("select session_id, session_no, visitor_id, agent_id, status, queue_time, assign_time, last_msg_time "
            + "from kf_session where status in ('0', '1') "
            + "and coalesce(last_msg_time, assign_time, queue_time, create_time) < #{deadline} "
            + "order by coalesce(last_msg_time, assign_time, queue_time, create_time) asc limit #{limit}")
    public List<KfSession> selectIdleSessions(@Param("deadline") Date deadline, @Param("limit") int limit);

    /**
     * 会话消息序号原子递增（行锁保证并发安全），随后同一事务内读取即为本次分配的序号
     * 
     * @param sessionId 会话ID
     * @param msgTime 消息时间
     * @return 结果
     */
    @Update("update kf_session set last_seq = last_seq + 1, last_msg_time = #{msgTime} where session_id = #{sessionId}")
    public int incrLastSeq(@Param("sessionId") Long sessionId, @Param("msgTime") Date msgTime);

    /**
     * 网关实时消息的会话侧写入：一条语句同时完成「占号 + 未读 + 首次响应 + 活跃时间」。
     *
     * 阶段11：MySQL 在公网，每条语句 15~20ms 纯往返；原先这三件事分三次 UPDATE 打在热路径上，
     * 一条消息的 ACK 延迟里有大半是排队等数据库。合成一条后语义不变（同样的 greatest 与
     * 「仅当尚未记录时才写」条件）。
     *
     * @param sessionId 会话ID
     * @param msgTime 消息时间（写 last_msg_time）
     * @param agentUnreadDelta 客服侧未读增量（访客来信时为 1）
     * @param visitorUnreadDelta 访客侧未读增量（客服来信时为 1）
     * @param markFirstResponse 1 表示本条是客服发出，需要记录首次响应时间
     * @return 影响行数
     */
    @Update("update kf_session set last_seq = last_seq + 1, last_msg_time = #{msgTime}, "
            + "unread_agent = greatest(unread_agent + #{agentUnreadDelta}, 0), "
            + "unread_visitor = greatest(unread_visitor + #{visitorUnreadDelta}, 0), "
            + "first_resp_time = if(first_resp_time is null and #{markFirstResponse} = 1, now(), first_resp_time) "
            + "where session_id = #{sessionId}")
    public int applyIncoming(@Param("sessionId") Long sessionId, @Param("msgTime") Date msgTime,
            @Param("agentUnreadDelta") int agentUnreadDelta, @Param("visitorUnreadDelta") int visitorUnreadDelta,
            @Param("markFirstResponse") int markFirstResponse);

    /**
     * 读取当前会话已分配的最大序号
     * 
     * @param sessionId 会话ID
     * @return 序号
     */
    @Select("select last_seq from kf_session where session_id = #{sessionId}")
    public int selectLastSeq(@Param("sessionId") Long sessionId);

    /**
     * 从未分配队列中抢占会话（仅当仍处于排队中且无人分配时成功）
     * 
     * @param sessionId 会话ID
     * @param agentId 客服ID
     * @param assignTime 分配时间
     * @return 影响行数，0 表示已被其他客服抢占
     */
    @Update("update kf_session set agent_id = #{agentId}, status = '1', assign_time = #{assignTime} "
            + "where session_id = #{sessionId} and status = '0' and agent_id is null")
    public int tryAssign(@Param("sessionId") Long sessionId, @Param("agentId") Long agentId,
            @Param("assignTime") Date assignTime);

    /**
     * 客服侧未读计数增减（结果不小于 0）
     * 
     * @param sessionId 会话ID
     * @param delta 增量
     * @return 结果
     */
    @Update("update kf_session set unread_agent = greatest(unread_agent + #{delta}, 0) where session_id = #{sessionId}")
    public int addUnreadAgent(@Param("sessionId") Long sessionId, @Param("delta") int delta);

    /**
     * 访客侧未读计数增减（结果不小于 0）
     * 
     * @param sessionId 会话ID
     * @param delta 增量
     * @return 结果
     */
    @Update("update kf_session set unread_visitor = greatest(unread_visitor + #{delta}, 0) where session_id = #{sessionId}")
    public int addUnreadVisitor(@Param("sessionId") Long sessionId, @Param("delta") int delta);

    /**
     * 清零客服侧未读
     * 
     * @param sessionId 会话ID
     * @return 结果
     */
    @Update("update kf_session set unread_agent = 0 where session_id = #{sessionId}")
    public int clearUnreadAgent(@Param("sessionId") Long sessionId);

    /**
     * 清零访客侧未读
     * 
     * @param sessionId 会话ID
     * @return 结果
     */
    @Update("update kf_session set unread_visitor = 0 where session_id = #{sessionId}")
    public int clearUnreadVisitor(@Param("sessionId") Long sessionId);

    /**
     * 客服会话列表视图（含访客昵称与最后一条消息摘要）
     * 
     * @param agentId 客服ID
     * @param status 会话状态，空字符串表示进行中（排队+接待）
     * @return 视图集合
     */
    @Select("<script>"
            + "select s.session_id sessionId, s.session_no sessionNo, s.status, s.agent_id agentId, "
            + "s.visitor_id visitorId, v.visitor_no visitorNo, v.nickname visitorNick, "
            + "s.unread_agent unreadAgent, s.unread_visitor unreadVisitor, "
            + "s.queue_time queueTime, s.assign_time assignTime, s.last_msg_time lastMsgTime, s.end_time endTime, "
            + "s.last_seq lastSeq, "
            + "(select m.content from kf_message m where m.session_id = s.session_id order by m.seq desc limit 1) lastContent "
            + "from kf_session s left join kf_visitor v on v.visitor_id = s.visitor_id "
            // 排队队列是全客服共享的：还没分配出去的会话（agent_id 为空）必须出现在每个在线客服的列表里，
            // 否则客服端「排队」页签永远是空的，「一键接入」也就无从接起（阶段15 实测：关掉自动分配后队列看不见）。
            // 已结束的历史会话仍只看自己的，走 selectAgentHistory。
            + "where (s.agent_id = #{agentId} or (s.status = '0' and s.agent_id is null)) "
            // 状态为空只看进行中（排队+接待）：客服端会话侧栏只应出现在接待的会话，历史走分页查询接口
            + "<choose><when test='status != null and status != \"\"'> and s.status = #{status}</when>"
            + "<otherwise> and s.status in ('0', '1')</otherwise></choose> "
            + "order by s.last_msg_time desc, s.session_id desc limit 200"
            + "</script>")
    public java.util.List<java.util.Map<String, Object>> selectAgentSessionView(@Param("agentId") Long agentId,
            @Param("status") String status);

    /**
     * 客服历史会话分页查询（按时间、访客关键字、状态筛选；分页由 PageHelper 接管）
     * 
     * @param agentId 客服ID
     * @param status 会话状态，空表示全部
     * @param keyword 访客昵称/访客标识/会话编号模糊匹配
     * @param beginTime 创建时间下界
     * @param endTime 创建时间上界
     * @return 视图集合
     */
    @Select("<script>"
            + "select s.session_id sessionId, s.session_no sessionNo, s.status, s.agent_id agentId, "
            + "s.visitor_id visitorId, v.visitor_no visitorNo, v.nickname visitorNick, "
            + "s.queue_time queueTime, s.assign_time assignTime, s.first_resp_time firstRespTime, s.end_time endTime, "
            + "s.end_by endBy, s.last_seq lastSeq, "
            + "(select m.content from kf_message m where m.session_id = s.session_id order by m.seq desc limit 1) lastContent "
            + "from kf_session s left join kf_visitor v on v.visitor_id = s.visitor_id "
            + "where s.agent_id = #{agentId} "
            + "<if test='status != null and status != &quot;&quot;'> and s.status = #{status}</if>"
            + "<if test='beginTime != null'> and s.create_time &gt;= #{beginTime}</if>"
            + "<if test='endTime != null'> and s.create_time &lt;= #{endTime}</if>"
            + "<if test='keyword != null and keyword != &quot;&quot;'>"
            + " and (v.nickname like concat('%', #{keyword}, '%') or v.visitor_no like concat('%', #{keyword}, '%')"
            + " or s.session_no like concat('%', #{keyword}, '%'))</if>"
            + " order by s.end_time desc, s.session_id desc"
            + "</script>")
    public java.util.List<java.util.Map<String, Object>> selectAgentHistory(@Param("agentId") Long agentId,
            @Param("status") String status, @Param("keyword") String keyword,
            @Param("beginTime") java.util.Date beginTime, @Param("endTime") java.util.Date endTime);

    /**
     * 记录客服首次响应时间（仅当尚未记录时生效）
     * 
     * @param sessionId 会话ID
     * @return 影响行数
     */
    @Update("update kf_session set first_resp_time = now() where session_id = #{sessionId} and first_resp_time is null")
    public int markFirstResponse(@Param("sessionId") Long sessionId);

    /**
     * 读取会话密钥材料（包装后的密文 + 摘要 + 版本）
     *
     * @param sessionId 会话ID
     * @return 只带密钥三列的会话对象，会话不存在时为 null
     */
    @Select("select session_id sessionId, sm4_key_cipher sm4KeyCipher, sm4_key_digest sm4KeyDigest, "
            + "sm4_key_version sm4KeyVersion from kf_session where session_id = #{sessionId}")
    public KfSession selectSessionKey(@Param("sessionId") Long sessionId);

    /**
     * 首写者建立会话密钥：只有尚未协商过密钥的会话才会被写入
     *
     * @param sessionId 会话ID
     * @param cipher 包装后的密钥材料
     * @param digest 密钥明文的 SM3 摘要
     * @param keyVersion 密钥版本
     * @return 影响行数，0 表示已有其它端先建立了会话密钥
     */
    @Update("update kf_session set sm4_key_cipher = #{cipher}, sm4_key_digest = #{digest}, "
            + "sm4_key_version = #{keyVersion} where session_id = #{sessionId} and sm4_key_cipher is null")
    public int bindSessionKey(@Param("sessionId") Long sessionId, @Param("cipher") String cipher,
            @Param("digest") String digest, @Param("keyVersion") int keyVersion);
}
