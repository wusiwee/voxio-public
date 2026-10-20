package com.voxio.kf.mapper;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.voxio.kf.domain.KfOfflineMessage;

/**
 * 离线留言 数据层
 * 
 * @author voxio
 */
public interface KfOfflineMessageMapper extends BaseMapper<KfOfflineMessage>
{
    /**
     * 管理后台留言列表（带访客、会话、工单与处理客服信息）
     */
    @Select("<script>"
            + "select o.msg_id msgId, o.visitor_id visitorId, v.nickname visitorNick, v.visitor_no visitorNo, "
            + "v.contact_phone contactPhone, v.contact_email contactEmail, "
            + "o.session_id sessionId, s.session_no sessionNo, o.contact, o.content, o.status, "
            + "o.ticket_id ticketId, t.ticket_no ticketNo, t.status ticketStatus, "
            + "o.handler_agent_id handlerAgentId, a.nick_name handlerNick, "
            + "o.create_time createTime, o.update_time updateTime "
            + "from kf_offline_message o "
            + "left join kf_visitor v on v.visitor_id = o.visitor_id "
            + "left join kf_session s on s.session_id = o.session_id "
            + "left join kf_ticket t on t.ticket_id = o.ticket_id "
            + "left join kf_agent a on a.agent_id = o.handler_agent_id "
            + "where 1 = 1 "
            + "<if test='status != null and status != \"\"'> and o.status = #{status}</if>"
            + "<if test='handlerAgentId != null'> and o.handler_agent_id = #{handlerAgentId}</if>"
            + "<if test='beginTime != null and beginTime != \"\"'> and o.create_time &gt;= #{beginTime}</if>"
            + "<if test='endTime != null and endTime != \"\"'> and o.create_time &lt;= #{endTime}</if>"
            + "<if test='keyword != null and keyword != \"\"'>"
            // 阶段10.3：正文与联系方式密文落库，SQL 的 LIKE 打不到密文，关键字只按明文列（访客昵称/编号）匹配
            + " and (v.nickname like concat('%', #{keyword}, '%') or v.visitor_no like concat('%', #{keyword}, '%'))</if>"
            + " order by o.msg_id desc"
            + "</script>")
    public List<Map<String, Object>> selectAdminList(@Param("status") String status,
            @Param("handlerAgentId") Long handlerAgentId, @Param("keyword") String keyword,
            @Param("beginTime") String beginTime, @Param("endTime") String endTime);

    /**
     * 单条留言详情（带访客与会话信息）
     */
    @Select("select o.msg_id msgId, o.visitor_id visitorId, v.nickname visitorNick, v.visitor_no visitorNo, "
            + "o.session_id sessionId, s.session_no sessionNo, o.contact, o.content, o.status, "
            + "o.ticket_id ticketId, t.ticket_no ticketNo, o.handler_agent_id handlerAgentId, "
            + "a.nick_name handlerNick, o.create_time createTime, o.update_time updateTime "
            + "from kf_offline_message o "
            + "left join kf_visitor v on v.visitor_id = o.visitor_id "
            + "left join kf_session s on s.session_id = o.session_id "
            + "left join kf_ticket t on t.ticket_id = o.ticket_id "
            + "left join kf_agent a on a.agent_id = o.handler_agent_id "
            + "where o.msg_id = #{msgId}")
    public Map<String, Object> selectAdminDetail(@Param("msgId") Long msgId);
}
