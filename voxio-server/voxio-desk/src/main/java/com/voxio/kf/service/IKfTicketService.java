package com.voxio.kf.service;

import java.util.List;
import java.util.Map;

import com.baomidou.mybatisplus.spring.service.IService;
import com.voxio.kf.domain.KfTicket;
import com.voxio.kf.domain.KfTicketReply;

/**
 * 客服工单 服务层
 * 
 * 只做简单状态流转（待处理→处理中→已解决→已关闭），不含工作流引擎。
 * 
 * @author voxio
 */
public interface IKfTicketService extends IService<KfTicket>
{
    /**
     * 创建工单
     * 
     * @param ticket 工单（标题、内容、分类、优先级、联系方式、关联会话）
     * @param creatorAgentId 创建客服ID
     * @param creatorName 创建客服名称
     * @return 工单
     */
    public KfTicket create(KfTicket ticket, Long creatorAgentId, String creatorName);

    /**
     * 流转工单状态并留痕
     * 
     * @param ticketId 工单ID
     * @param targetStatus 目标状态
     * @param note 备注
     * @param operatorAgentId 操作客服ID
     * @param operatorName 操作人名称
     * @return 工单
     */
    public KfTicket changeStatus(Long ticketId, String targetStatus, String note, Long operatorAgentId,
            String operatorName);

    /**
     * 查询工单流转与回复记录
     * 
     * @param ticketId 工单ID
     * @return 记录集合
     */
    public List<KfTicketReply> listReplies(Long ticketId);

    /**
     * 客服可见工单列表（本人创建或本人处理）
     * 
     * @param query 查询条件
     * @param agentId 客服ID
     * @return 工单集合
     */
    public List<KfTicket> selectForAgent(KfTicket query, Long agentId);

    /**
     * 创建工单（可指定创建人类型与处理客服，供管理后台使用）
     * 
     * @param ticket 工单
     * @param creatorType 创建人类型（1管理员 2客服）
     * @param creatorId 创建人ID
     * @param creatorName 创建人名称
     * @param handlerAgentId 处理客服ID，空表示待指派
     * @return 工单
     */
    public KfTicket createBy(KfTicket ticket, String creatorType, Long creatorId, String creatorName,
            Long handlerAgentId);

    /**
     * 流转工单状态（区分操作人是管理员还是客服）
     */
    public KfTicket changeStatus(Long ticketId, String targetStatus, String note, Long operatorId,
            String operatorName, boolean byAdmin);

    /**
     * 修改工单基础信息与处理客服
     */
    public KfTicket updateBasic(KfTicket ticket, String operatorName, Long operatorId, boolean byAdmin);

    /**
     * 追加工单回复或内部备注
     */
    public KfTicketReply addReply(Long ticketId, String content, String replyType, String operatorName,
            Long operatorId, boolean byAdmin);

    /**
     * 删除工单（仅已解决/已关闭，连带删除流转记录）
     */
    public void removeAdmin(Long[] ticketIds);

    /**
     * 管理后台工单列表（含访客昵称、处理客服昵称、会话号）
     */
    public List<Map<String, Object>> selectForAdmin(KfTicket query, String beginTime, String endTime);

    /**
     * 手动将当前工单推送给第三方系统，并把结果记在工单上
     * 
     * @param ticketId 工单ID
     * @param operator 操作人名称
     * @return 推送结果（pushStatus/pushStatusText/pushTime/message）
     */
    public Map<String, Object> pushToThirdParty(Long ticketId, String operator);
}
