package com.voxio.kf.mapper;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.voxio.kf.domain.KfTicket;

/**
 * 客服工单 数据层
 * 
 * @author voxio
 */
public interface KfTicketMapper extends BaseMapper<KfTicket>
{
    /**
     * 管理后台工单列表（带访客昵称、处理客服昵称、关联会话号）
     *
     * 阶段11：列表不再取 content 与 push_response —— 表格里没有这两列，详情走 /kf/ticket/{id}，
     * 而它们都是大字段：取出来还要逐行 SM4-GCM 解密，等于每页白做一次无用的批量解密。
     */
    @Select("<script>"
            + "select k.ticket_id ticketId, k.ticket_no ticketNo, k.title, k.category, k.priority, "
            + "k.status, k.source, k.visitor_id visitorId, v.nickname visitorNick, v.visitor_no visitorNo, "
            + "k.session_id sessionId, s.session_no sessionNo, k.contact, k.creator_type creatorType, "
            + "k.creator_id creatorId, k.handler_agent_id handlerAgentId, a.nick_name handlerNick, "
            + "k.finish_time finishTime, k.close_time closeTime, k.push_status pushStatus, k.push_time pushTime, "
            + "k.create_by createBy, k.create_time createTime "
            + "from kf_ticket k "
            + "left join kf_visitor v on v.visitor_id = k.visitor_id "
            + "left join kf_session s on s.session_id = k.session_id "
            + "left join kf_agent a on a.agent_id = k.handler_agent_id "
            + "where 1 = 1 "
            + "<if test='query.status != null and query.status != \"\"'> and k.status = #{query.status}</if>"
            + "<if test='query.category != null and query.category != \"\"'> and k.category = #{query.category}</if>"
            + "<if test='query.priority != null and query.priority != \"\"'> and k.priority = #{query.priority}</if>"
            + "<if test='query.source != null and query.source != \"\"'> and k.source = #{query.source}</if>"
            + "<if test='query.handlerAgentId != null'> and k.handler_agent_id = #{query.handlerAgentId}</if>"
            + "<if test='query.visitorId != null'> and k.visitor_id = #{query.visitorId}</if>"
            + "<if test='query.sessionId != null'> and k.session_id = #{query.sessionId}</if>"
            + "<if test='query.title != null and query.title != \"\"'>"
            + " and (k.title like concat('%', #{query.title}, '%') or k.ticket_no like concat('%', #{query.title}, '%')"
            // 阶段10.3：联系方式密文落库，参与不了 LIKE；工单标题与工单号仍是明文，检索照常
            + " or v.nickname like concat('%', #{query.title}, '%'))</if>"
            + "<if test='beginTime != null and beginTime != \"\"'> and k.create_time &gt;= #{beginTime}</if>"
            + "<if test='endTime != null and endTime != \"\"'> and k.create_time &lt;= #{endTime}</if>"
            + " order by k.ticket_id desc"
            + "</script>")
    public List<Map<String, Object>> selectAdminList(@Param("query") KfTicket query,
            @Param("beginTime") String beginTime, @Param("endTime") String endTime);
}
