package com.voxio.kf.mapper;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 管理后台只读查询（会话监控 / 聊天记录 / 客服状态）
 * 
 * @author voxio
 */
public interface KfMonitorMapper
{
    /**
     * 会话列表（全量，管理员只读）
     *
     * 阶段11：去掉「逐行数消息条数」的相关子查询 —— 会话内 seq 从 1 递增且消息不物理删除，
     * s.last_seq 就是条数，直接取列即可。最后一条摘要仍用一次索引定位，但它只对分页输出的那几行求值，
     * 不是每条记录多打一次库的 N+1。排序改按 last_msg_time 优先，配合 idx_last_msg 免掉整表排序。
     * 
     * @param status active=进行中(0/1)，all=全部，其它为精确状态
     */
    @Select("<script>"
            + "select s.session_id sessionId, s.session_no sessionNo, s.status, s.source_page sourcePage, "
            + "s.visitor_id visitorId, v.visitor_no visitorNo, v.nickname visitorNick, v.ip visitorIp, "
            + "v.contact_phone contactPhone, v.contact_email contactEmail, "
            + "s.agent_id agentId, a.agent_no agentNo, a.nick_name agentNick, a.online_status agentOnlineStatus, "
            + "s.queue_time queueTime, s.assign_time assignTime, s.first_resp_time firstRespTime, "
            + "s.end_time endTime, s.end_by endBy, s.close_reason closeReason, "
            + "s.unread_agent unreadAgent, s.unread_visitor unreadVisitor, s.last_seq lastSeq, "
            + "s.last_seq messageCount, s.last_msg_time lastMsgTime, s.create_time createTime, "
            + "(select m.content from kf_message m where m.session_id = s.session_id order by m.seq desc limit 1) lastContent "
            + "from kf_session s "
            + "left join kf_visitor v on v.visitor_id = s.visitor_id "
            + "left join kf_agent a on a.agent_id = s.agent_id "
            + "where 1 = 1 "
            + "<if test='status != null and status == \"active\"'> and s.status in ('0','1')</if>"
            + "<if test='status != null and status != \"active\" and status != \"all\" and status != \"\"'>"
            + " and s.status = #{status}</if>"
            + "<if test='agentId != null'> and s.agent_id = #{agentId}</if>"
            + "<if test='visitorId != null'> and s.visitor_id = #{visitorId}</if>"
            + "<if test='beginTime != null and beginTime != \"\"'> and s.create_time &gt;= #{beginTime}</if>"
            + "<if test='endTime != null and endTime != \"\"'> and s.create_time &lt;= #{endTime}</if>"
            + "<if test='keyword != null and keyword != \"\"'>"
            + " and (v.nickname like concat('%', #{keyword}, '%') or v.visitor_no like concat('%', #{keyword}, '%')"
            + " or s.session_no like concat('%', #{keyword}, '%') or a.nick_name like concat('%', #{keyword}, '%'))</if>"
            + " order by s.status asc, s.last_msg_time desc, s.session_id desc"
            + "</script>")
    public List<Map<String, Object>> selectSessionView(@Param("status") String status,
            @Param("agentId") Long agentId, @Param("visitorId") Long visitorId, @Param("keyword") String keyword,
            @Param("beginTime") String beginTime, @Param("endTime") String endTime);

    /**
     * 会话详情（含访客、客服信息）
     */
    @Select("select s.session_id sessionId, s.session_no sessionNo, s.status, s.source_page sourcePage, "
            + "s.visitor_id visitorId, v.visitor_no visitorNo, v.nickname visitorNick, v.ip visitorIp, "
            + "v.user_agent userAgent, v.referrer referrer, v.contact_phone contactPhone, v.contact_email contactEmail, "
            + "s.agent_id agentId, a.agent_no agentNo, a.nick_name agentNick, a.online_status agentOnlineStatus, "
            + "s.queue_time queueTime, s.assign_time assignTime, s.first_resp_time firstRespTime, "
            + "s.end_time endTime, s.end_by endBy, s.close_reason closeReason, "
            + "s.from_agent_id fromAgentId, s.unread_agent unreadAgent, s.unread_visitor unreadVisitor, "
            + "s.last_seq lastSeq, s.last_seq messageCount, s.last_msg_time lastMsgTime, s.create_time createTime "
            + "from kf_session s "
            + "left join kf_visitor v on v.visitor_id = s.visitor_id "
            + "left join kf_agent a on a.agent_id = s.agent_id "
            + "where s.session_id = #{sessionId}")
    public Map<String, Object> selectSessionDetail(@Param("sessionId") Long sessionId);

    /**
     * 客服在线状态与实时负载
     *
     * 不取手机号：这个页面只用得上昵称/账号/负载/时间，而手机号是密文列，
     * 带上它就等于为每一行多做一次 SM4 解密，还把一个界面用不到的个人信息发到浏览器（阶段11 功能点5）。
     */
    @Select("<script>"
            + "select a.agent_id agentId, a.agent_no agentNo, a.nick_name agentNick, "
            + "a.online_status onlineStatus, a.current_load currentLoad, a.accept_max acceptMax, "
            + "a.status, a.login_date lastLoginTime, "
            + "(select count(1) from kf_session s where s.agent_id = a.agent_id and s.status = '1') servingCount, "
            + "(select count(1) from kf_session s where s.agent_id = a.agent_id and s.status = '1' "
            + " and s.unread_agent &gt; 0) unreadSessionCount "
            + "from kf_agent a where a.del_flag = '0' "
            + "<if test='onlineStatus != null and onlineStatus != \"\"'> and a.online_status = #{onlineStatus}</if>"
            + "<if test='keyword != null and keyword != \"\"'>"
            + " and (a.nick_name like concat('%', #{keyword}, '%') or a.agent_no like concat('%', #{keyword}, '%'))</if>"
            + " order by a.online_status asc, a.current_load asc, a.agent_id asc"
            + "</script>")
    public List<Map<String, Object>> selectAgentStatus(@Param("onlineStatus") String onlineStatus,
            @Param("keyword") String keyword);

    /** 聊天记录的投影与筛选条件（offset 分页与游标分页共用，避免两份 SQL 走偏） */
    String CHAT_LOG_SELECT = "select m.msg_id msgId, m.session_id sessionId, s.session_no sessionNo, m.seq, "
            + "m.sender_type senderType, m.sender_id senderId, "
            + "case m.sender_type when '2' then a.nick_name when '1' then v.nickname else '系统' end senderName, "
            + "v.visitor_no visitorNo, v.nickname visitorNick, "
            + "m.msg_type msgType, m.content, "
            + "m.deliver_status deliverStatus, m.read_time readTime, m.create_time createTime "
            + "from kf_message m "
            + "left join kf_session s on s.session_id = m.session_id "
            + "left join kf_visitor v on v.visitor_id = s.visitor_id "
            + "left join kf_agent a on m.sender_type = '2' and a.agent_id = m.sender_id "
            + "where 1 = 1 ";

    String CHAT_LOG_FILTERS = "<if test='beginTime != null and beginTime != \"\"'> and m.create_time &gt;= #{beginTime}</if>"
            + "<if test='endTime != null and endTime != \"\"'> and m.create_time &lt;= #{endTime}</if>"
            + "<if test='sessionId != null'> and m.session_id = #{sessionId}</if>"
            + "<if test='visitorId != null'> and s.visitor_id = #{visitorId}</if>"
            + "<if test='agentId != null'> and m.sender_type = '2' and m.sender_id = #{agentId}</if>"
            + "<if test='senderType != null and senderType != \"\"'> and m.sender_type = #{senderType}</if>"
            + "<if test='msgType != null and msgType != \"\"'> and m.msg_type = #{msgType}</if>"
            + "<if test='keyword != null and keyword != \"\"'>"
            // 阶段10.2：消息正文密文落库，LIKE 打不到密文，关键词只按明文的会话号与访客维度匹配
            + " and (s.session_no like concat('%', #{keyword}, '%')"
            + " or v.nickname like concat('%', #{keyword}, '%') or v.visitor_no like concat('%', #{keyword}, '%'))</if>";

    /**
     * 聊天记录分页查询（管理后台 offset 分页；分页由 PageHelper 接管）
     *
     * 排序用 msg_id 而不是 create_time：msg_id 自增、与写入时间同序，主键反向扫描即可零 filesort；
     * 按 create_time 排序时优化器会改走二级索引并整表排序（阶段11 EXPLAIN 实测）。
     */
    @Select("<script>" + CHAT_LOG_SELECT + CHAT_LOG_FILTERS
            + " order by m.msg_id desc"
            + "</script>")
    public List<Map<String, Object>> selectChatLog(@Param("sessionId") Long sessionId,
            @Param("visitorId") Long visitorId, @Param("agentId") Long agentId, @Param("senderType") String senderType,
            @Param("msgType") String msgType, @Param("keyword") String keyword, @Param("beginTime") String beginTime,
            @Param("endTime") String endTime);

    /**
     * 聊天记录游标分页（阶段11）
     *
     * offset 分页要付两条语句：一条 count 全量匹配行、一条 limit 后再丢掉前 N 行；
     * 游标按 msg_id（自增，与写入时间同序）往前翻，一条语句、走主键倒序，深翻页不再变慢。
     * 单页默认 30 条由调用方给，向前加载就是把上一页最后一行的 msgId 当 cursorMsgId 传进来。
     */
    @Select("<script>" + CHAT_LOG_SELECT + CHAT_LOG_FILTERS
            + "<if test='cursorMsgId != null'> and m.msg_id &lt; #{cursorMsgId}</if>"
            + " order by m.msg_id desc limit #{pageSize}"
            + "</script>")
    public List<Map<String, Object>> selectChatLogPage(@Param("sessionId") Long sessionId,
            @Param("visitorId") Long visitorId, @Param("agentId") Long agentId, @Param("senderType") String senderType,
            @Param("msgType") String msgType, @Param("keyword") String keyword, @Param("beginTime") String beginTime,
            @Param("endTime") String endTime, @Param("cursorMsgId") Long cursorMsgId,
            @Param("pageSize") int pageSize);

    /**
     * 单条消息详情
     */
    @Select("select m.msg_id msgId, m.session_id sessionId, s.session_no sessionNo, m.seq, m.client_msg_id clientMsgId, "
            + "m.sender_type senderType, m.sender_id senderId, "
            + "case m.sender_type when '2' then a.nick_name when '1' then v.nickname else '系统' end senderName, "
            + "v.visitor_no visitorNo, v.nickname visitorNick, m.msg_type msgType, m.content, "
            + "m.deliver_status deliverStatus, m.read_time readTime, "
            + "m.create_time createTime "
            + "from kf_message m left join kf_session s on s.session_id = m.session_id "
            + "left join kf_visitor v on v.visitor_id = s.visitor_id "
            + "left join kf_agent a on m.sender_type = '2' and a.agent_id = m.sender_id "
            + "where m.msg_id = #{msgId}")
    public Map<String, Object> selectMessageById(@Param("msgId") Long msgId);
}
