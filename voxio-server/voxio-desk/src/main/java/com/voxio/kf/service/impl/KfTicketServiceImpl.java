package com.voxio.kf.service.impl;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.voxio.common.exception.ServiceException;
import com.voxio.common.utils.DateUtils;
import com.voxio.common.utils.StringUtils;
import com.voxio.kf.domain.KfSession;
import com.voxio.kf.domain.KfTicket;
import com.voxio.kf.domain.KfTicketReply;
import com.voxio.kf.mapper.KfAgentMapper;
import com.voxio.kf.mapper.KfSessionMapper;
import com.voxio.kf.mapper.KfTicketMapper;
import com.voxio.kf.mapper.KfTicketReplyMapper;
import com.voxio.kf.service.IKfConfigService;
import com.voxio.kf.service.IKfTicketService;
import com.voxio.kf.util.KfCipherRowReader;

import lombok.RequiredArgsConstructor;

/**
 * 客服工单 服务层实现
 * 
 * @author voxio
 */
@Service
@RequiredArgsConstructor
public class KfTicketServiceImpl extends ServiceImpl<KfTicketMapper, KfTicket> implements IKfTicketService
{
    /** 允许的简单状态流转 */
    private static final Map<String, Set<String>> TRANSITIONS = Map.of(
            KfTicket.STATUS_PENDING, Set.of(KfTicket.STATUS_DOING, KfTicket.STATUS_CLOSED),
            KfTicket.STATUS_DOING, Set.of(KfTicket.STATUS_SOLVED, KfTicket.STATUS_CLOSED),
            KfTicket.STATUS_SOLVED, Set.of(KfTicket.STATUS_CLOSED));

    private static final String OPERATOR = "voxio";

    /** 工单第三方推送参数键（业务设置维护，鉴权头不下发到访客端与登录前接口） */
    private static final String PUSH_ENABLED_KEY = "kf.ticket.push.enabled";

    private static final String PUSH_URL_KEY = "kf.ticket.push.url";

    private static final String PUSH_HEADERS_KEY = "kf.ticket.push.headers";

    private static final String PUSH_TIMEOUT_KEY = "kf.ticket.push.timeoutSeconds";

    /** 推送状态：0 未推送 1 已推送 2 推送失败 */
    private static final String PUSH_NONE = "0";

    private static final String PUSH_OK = "1";

    private static final String PUSH_FAIL = "2";

    /** HTTP 保留头由客户端负责，配置里出现就直接拒绝，避免推送静默失败 */
    private static final Set<String> RESERVED_HEADERS = Set.of("content-length", "host", "connection", "upgrade",
            "expect");

    /** 全系统单实例复用：连接超时固定 5s，单次请求超时按参数设置 */
    private static final HttpClient PUSH_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5)).build();

    private final KfTicketReplyMapper replyMapper;

    private final KfSessionMapper sessionMapper;

    private final KfAgentMapper agentMapper;

    private final IKfConfigService configService;

    private final KfCipherRowReader cipherRowReader;

    @Override
    @Transactional
    public KfTicket create(KfTicket ticket, Long creatorAgentId, String creatorName)
    {
        return createBy(ticket, KfTicket.CREATOR_AGENT, creatorAgentId, creatorName, creatorAgentId);
    }

    @Override
    @Transactional
    public KfTicket createBy(KfTicket ticket, String creatorType, Long creatorId, String creatorName,
            Long handlerAgentId)
    {
        if (StringUtils.isEmpty(ticket.getTitle()) || ticket.getTitle().trim().isEmpty())
        {
            throw new ServiceException("工单标题不能为空");
        }
        if (ticket.getTitle().length() > 100)
        {
            throw new ServiceException("工单标题最多 100 字");
        }
        if (StringUtils.isEmpty(ticket.getContent()) || ticket.getContent().trim().isEmpty())
        {
            throw new ServiceException("工单内容不能为空");
        }
        if (ticket.getContent().length() > 2000)
        {
            throw new ServiceException("工单内容最多 2000 字");
        }
        if (StringUtils.isNotEmpty(ticket.getPriority()) && !"1".equals(ticket.getPriority())
                && !"2".equals(ticket.getPriority()) && !"3".equals(ticket.getPriority()))
        {
            throw new ServiceException("优先级只能为 1低 2中 3高");
        }
        if (StringUtils.isNotNull(handlerAgentId) && StringUtils.isNull(agentMapper.selectById(handlerAgentId)))
        {
            throw new ServiceException("处理客服不存在");
        }

        KfTicket entity = new KfTicket();
        entity.setTicketNo(nextTicketNo());
        entity.setTitle(ticket.getTitle().trim());
        entity.setContent(ticket.getContent().trim());
        entity.setCategory(StringUtils.nvl(ticket.getCategory(), "other"));
        entity.setPriority(StringUtils.nvl(ticket.getPriority(), "2"));
        entity.setStatus(KfTicket.STATUS_PENDING);
        entity.setSource(StringUtils.isNotEmpty(ticket.getSource()) ? ticket.getSource()
                : (StringUtils.isNotNull(ticket.getSessionId()) ? KfTicket.SOURCE_SESSION : KfTicket.SOURCE_MANUAL));
        entity.setContact(StringUtils.nvl(ticket.getContact(), ""));
        entity.setCreatorType(creatorType);
        entity.setCreatorId(creatorId);
        entity.setHandlerAgentId(handlerAgentId);
        entity.setCreateBy(OPERATOR);
        entity.setCreateTime(DateUtils.getNowDate());
        entity.setUpdateBy(OPERATOR);
        entity.setUpdateTime(DateUtils.getNowDate());

        if (StringUtils.isNotNull(ticket.getSessionId()))
        {
            KfSession session = sessionMapper.selectById(ticket.getSessionId());
            if (StringUtils.isNull(session))
            {
                throw new ServiceException("关联会话不存在");
            }
            entity.setSessionId(session.getSessionId());
            entity.setVisitorId(session.getVisitorId());
            if (StringUtils.isEmpty(entity.getContact()))
            {
                entity.setContact(StringUtils.nvl(session.getSessionNo(), ""));
            }
        }
        if (StringUtils.isNull(entity.getVisitorId()) && StringUtils.isNotNull(ticket.getVisitorId()))
        {
            // 离线留言转工单等场景没有会话，但已知访客，需保留关联
            entity.setVisitorId(ticket.getVisitorId());
        }
        save(entity);

        KfTicketReply created = new KfTicketReply();
        created.setTicketId(entity.getTicketId());
        created.setReplyType(KfTicketReply.TYPE_STATUS);
        created.setContent(StringUtils.isNotNull(handlerAgentId)
                ? "创建工单并指派给客服跟进" : "创建工单，等待指派处理客服");
        created.setToStatus(KfTicket.STATUS_PENDING);
        created.setOperatorType(KfTicket.CREATOR_AGENT.equals(creatorType) ? KfTicketReply.OPERATOR_AGENT
                : KfTicketReply.OPERATOR_ADMIN);
        created.setOperatorId(creatorId);
        created.setOperatorName(StringUtils.nvl(creatorName, ""));
        created.setCreateTime(DateUtils.getNowDate());
        replyMapper.insert(created);
        return entity;
    }

    @Override
    @Transactional
    public KfTicket updateBasic(KfTicket ticket, String operatorName, Long operatorId, boolean byAdmin)
    {
        KfTicket old = getById(ticket.getTicketId());
        if (StringUtils.isNull(old))
        {
            throw new ServiceException("工单不存在");
        }
        if (StringUtils.isEmpty(ticket.getTitle()) || ticket.getTitle().trim().isEmpty())
        {
            throw new ServiceException("工单标题不能为空");
        }
        if (ticket.getTitle().length() > 100)
        {
            throw new ServiceException("工单标题最多 100 字");
        }
        if (StringUtils.isNotEmpty(ticket.getPriority()) && !"1".equals(ticket.getPriority())
                && !"2".equals(ticket.getPriority()) && !"3".equals(ticket.getPriority()))
        {
            throw new ServiceException("优先级只能为 1低 2中 3高");
        }
        if (StringUtils.isNotNull(ticket.getHandlerAgentId())
                && StringUtils.isNull(agentMapper.selectById(ticket.getHandlerAgentId())))
        {
            throw new ServiceException("处理客服不存在");
        }

        KfTicket update = new KfTicket();
        update.setTicketId(old.getTicketId());
        update.setTitle(ticket.getTitle().trim());
        update.setContent(StringUtils.nvl(ticket.getContent(), old.getContent()));
        update.setCategory(StringUtils.nvl(ticket.getCategory(), old.getCategory()));
        update.setPriority(StringUtils.nvl(ticket.getPriority(), old.getPriority()));
        update.setContact(StringUtils.nvl(ticket.getContact(), old.getContact()));
        update.setHandlerAgentId(StringUtils.isNotNull(ticket.getHandlerAgentId()) ? ticket.getHandlerAgentId()
                : old.getHandlerAgentId());
        update.setUpdateBy(OPERATOR);
        update.setUpdateTime(DateUtils.getNowDate());
        updateById(update);

        appendReply(old.getTicketId(), KfTicketReply.TYPE_NOTE, "更新工单信息（标题/内容/分类/优先级/处理人）", null, null,
                operatorName, operatorId, byAdmin);
        return getById(old.getTicketId());
    }

    @Override
    @Transactional
    public KfTicketReply addReply(Long ticketId, String content, String replyType, String operatorName,
            Long operatorId, boolean byAdmin)
    {
        KfTicket ticket = getById(ticketId);
        if (StringUtils.isNull(ticket))
        {
            throw new ServiceException("工单不存在");
        }
        if (StringUtils.isEmpty(content) || content.trim().isEmpty())
        {
            throw new ServiceException("回复内容不能为空");
        }
        if (content.length() > 2000)
        {
            throw new ServiceException("回复内容最多 2000 字");
        }
        String type = StringUtils.isEmpty(replyType) ? KfTicketReply.TYPE_REPLY : replyType;
        if (!KfTicketReply.TYPE_REPLY.equals(type) && !KfTicketReply.TYPE_NOTE.equals(type))
        {
            throw new ServiceException("回复类型只能为 1对外回复 3内部备注");
        }
        return appendReply(ticketId, type, content.trim(), null, null, operatorName, operatorId, byAdmin);
    }

    @Override
    @Transactional
    public void removeAdmin(Long[] ticketIds)
    {
        if (ticketIds == null || ticketIds.length == 0)
        {
            throw new ServiceException("请选择要删除的工单");
        }
        for (Long ticketId : ticketIds)
        {
            KfTicket ticket = getById(ticketId);
            if (StringUtils.isNull(ticket))
            {
                throw new ServiceException(StringUtils.format("工单【{}】不存在", ticketId));
            }
            if (!KfTicket.STATUS_SOLVED.equals(ticket.getStatus())
                    && !KfTicket.STATUS_CLOSED.equals(ticket.getStatus()))
            {
                throw new ServiceException(StringUtils.format("工单【{}】状态为{}，仅已解决或已关闭的工单可删除",
                        ticket.getTicketNo(), statusText(ticket.getStatus())));
            }
            replyMapper.delete(new LambdaQueryWrapper<KfTicketReply>().eq(KfTicketReply::getTicketId, ticketId));
            baseMapper.deleteById(ticketId);
        }
    }

    @Override
    public List<Map<String, Object>> selectForAdmin(KfTicket query, String beginTime, String endTime)
    {
        List<Map<String, Object>> rows = baseMapper.selectAdminList(query, beginTime, endTime);
        // 列表是 Map 视图，不走实体类型处理器，出网前把密文列解回明文；
        // 正文与推送响应已从列表 SQL 去掉（阶段11），要看全文走详情接口
        cipherRowReader.decryptFields(rows, "contact");
        return rows;
    }

    private KfTicketReply appendReply(Long ticketId, String replyType, String content, String fromStatus,
            String toStatus, String operatorName, Long operatorId, boolean byAdmin)
    {
        KfTicketReply reply = new KfTicketReply();
        reply.setTicketId(ticketId);
        reply.setReplyType(replyType);
        reply.setContent(content);
        reply.setFromStatus(fromStatus);
        reply.setToStatus(toStatus);
        reply.setOperatorType(byAdmin ? KfTicketReply.OPERATOR_ADMIN : KfTicketReply.OPERATOR_AGENT);
        reply.setOperatorId(operatorId);
        reply.setOperatorName(StringUtils.nvl(operatorName, ""));
        reply.setCreateTime(DateUtils.getNowDate());
        replyMapper.insert(reply);
        return reply;
    }

    @Override
    @Transactional
    public KfTicket changeStatus(Long ticketId, String targetStatus, String note, Long operatorAgentId,
            String operatorName)
    {
        return changeStatus(ticketId, targetStatus, note, operatorAgentId, operatorName, false);
    }

    @Override
    @Transactional
    public KfTicket changeStatus(Long ticketId, String targetStatus, String note, Long operatorId,
            String operatorName, boolean byAdmin)
    {
        KfTicket ticket = getById(ticketId);
        if (StringUtils.isNull(ticket))
        {
            throw new ServiceException("工单不存在");
        }
        if (StringUtils.isEmpty(targetStatus))
        {
            throw new ServiceException("缺少目标状态");
        }
        Set<String> allowed = TRANSITIONS.get(ticket.getStatus());
        if (allowed == null || !allowed.contains(targetStatus))
        {
            throw new ServiceException(StringUtils.format("不允许从 {} 流转到 {}", statusText(ticket.getStatus()),
                    statusText(targetStatus)));
        }

        KfTicket update = new KfTicket();
        update.setTicketId(ticketId);
        update.setStatus(targetStatus);
        update.setUpdateBy(OPERATOR);
        update.setUpdateTime(DateUtils.getNowDate());
        if (KfTicket.STATUS_SOLVED.equals(targetStatus))
        {
            update.setFinishTime(DateUtils.getNowDate());
        }
        if (KfTicket.STATUS_CLOSED.equals(targetStatus))
        {
            update.setCloseTime(DateUtils.getNowDate());
        }
        updateById(update);

        appendReply(ticketId, KfTicketReply.TYPE_STATUS, StringUtils.nvl(note, statusText(targetStatus)),
                ticket.getStatus(), targetStatus, operatorName, operatorId, byAdmin);
        return getById(ticketId);
    }

    @Override
    public List<KfTicketReply> listReplies(Long ticketId)
    {
        return replyMapper.selectList(new LambdaQueryWrapper<KfTicketReply>()
                .eq(KfTicketReply::getTicketId, ticketId)
                .orderByAsc(KfTicketReply::getReplyId));
    }

    @Override
    public List<KfTicket> selectForAgent(KfTicket query, Long agentId)
    {
        String keyword = query.getTitle();
        LambdaQueryWrapper<KfTicket> wrapper = new LambdaQueryWrapper<KfTicket>()
                .and(w -> w.eq(KfTicket::getCreatorId, agentId).or().eq(KfTicket::getHandlerAgentId, agentId))
                .eq(StringUtils.isNotEmpty(query.getStatus()), KfTicket::getStatus, query.getStatus())
                .eq(StringUtils.isNotEmpty(query.getCategory()), KfTicket::getCategory, query.getCategory())
                .eq(StringUtils.isNotNull(query.getSessionId()), KfTicket::getSessionId, query.getSessionId())
                .and(StringUtils.isNotEmpty(keyword),
                        w -> w.like(KfTicket::getTitle, keyword).or().like(KfTicket::getTicketNo, keyword))
                .orderByDesc(KfTicket::getTicketId);
        return list(wrapper);
    }

    private String nextTicketNo()
    {
        return "T" + DateUtils.dateTimeNow("yyyyMMddHHmmss") + ThreadLocalRandom.current().nextInt(100, 1000);
    }

    private String statusText(String status)
    {
        switch (StringUtils.nvl(status, ""))
        {
            case KfTicket.STATUS_PENDING:
                return "待处理";
            case KfTicket.STATUS_DOING:
                return "处理中";
            case KfTicket.STATUS_SOLVED:
                return "已解决";
            case KfTicket.STATUS_CLOSED:
                return "已关闭";
            default:
                return "未知状态";
        }
    }

    /**
     * 手动把当前工单推送给第三方系统。
     *
     * 单向上报、就地留痕：只记录最近一次结果，不做字段映射引擎，也不建重试队列——
     * 单商户自建系统里自动重试最容易造成的后果是重复建单，人工重推一次的成本更低。
     */
    @Override
    public Map<String, Object> pushToThirdParty(Long ticketId, String operator)
    {
        KfTicket ticket = getById(ticketId);
        if (StringUtils.isNull(ticket))
        {
            throw new ServiceException("工单不存在");
        }
        if (!configService.selectBooleanByKey(PUSH_ENABLED_KEY, false))
        {
            throw new ServiceException("工单第三方推送未启用，请在「业务设置 → 工单第三方推送」开启并填写地址");
        }
        String url = configService.selectConfigByKey(PUSH_URL_KEY);
        if (StringUtils.isBlank(url) || !url.startsWith("http"))
        {
            throw new ServiceException("推送地址未配置或不合法，需填写完整 http(s) 地址");
        }
        int timeoutSeconds = configService.selectIntByKey(PUSH_TIMEOUT_KEY, 5);

        String payload = JSON.toJSONString(pushPayload(ticket, operator));
        HttpRequest.Builder builder;
        try
        {
            builder = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .header("Content-Type", "application/json;charset=UTF-8")
                    .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8));
            applyHeaders(builder, configService.selectConfigByKey(PUSH_HEADERS_KEY));
        }
        catch (IllegalArgumentException e)
        {
            throw new ServiceException("推送地址或鉴权头格式不正确：" + e.getMessage());
        }

        boolean success = false;
        String summary;
        try
        {
            HttpResponse<String> response = PUSH_CLIENT.send(builder.build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            success = response.statusCode() >= 200 && response.statusCode() < 300;
            summary = "HTTP " + response.statusCode() + " " + abbreviate(response.body());
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
            summary = "推送被中断（超时 " + timeoutSeconds + " 秒）";
        }
        catch (Exception e)
        {
            // 连不上/超时/证书问题都属于业务失败，需要让客服在界面上看到原因而不是 500
            summary = "推送失败：" + e.getClass().getSimpleName() + " " + abbreviate(e.getMessage());
        }
        recordPush(ticket, success ? PUSH_OK : PUSH_FAIL, summary);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ticketId", ticket.getTicketId());
        result.put("ticketNo", ticket.getTicketNo());
        result.put("pushStatus", success ? PUSH_OK : PUSH_FAIL);
        result.put("pushStatusText", success ? "已推送" : "推送失败");
        result.put("pushTime", DateUtils.getTime());
        result.put("message", summary);
        return result;
    }

    /** 推送内容固定为工单本体字段，不随第三方接口变化做映射 */
    private Map<String, Object> pushPayload(KfTicket ticket, String operator)
    {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("ticketNo", ticket.getTicketNo());
        payload.put("title", ticket.getTitle());
        payload.put("content", ticket.getContent());
        payload.put("category", ticket.getCategory());
        payload.put("priority", ticket.getPriority());
        payload.put("status", ticket.getStatus());
        payload.put("statusText", statusText(ticket.getStatus()));
        payload.put("source", ticket.getSource());
        payload.put("contact", ticket.getContact());
        payload.put("sessionId", ticket.getSessionId());
        payload.put("visitorId", ticket.getVisitorId());
        payload.put("handlerAgentId", ticket.getHandlerAgentId());
        payload.put("createTime", ticket.getCreateTime());
        payload.put("finishTime", ticket.getFinishTime());
        payload.put("operator", operator);
        payload.put("pushTime", DateUtils.getTime());
        return payload;
    }

    /** 鉴权头格式：k1=v1;k2=v2，只接受普通自定义头 */
    private void applyHeaders(HttpRequest.Builder builder, String headers)
    {
        if (StringUtils.isBlank(headers))
        {
            return;
        }
        for (String pair : headers.split(";"))
        {
            int idx = pair.indexOf('=');
            if (idx <= 0)
            {
                continue;
            }
            String name = pair.substring(0, idx).trim();
            String value = pair.substring(idx + 1).trim();
            if (RESERVED_HEADERS.contains(name.toLowerCase()))
            {
                throw new ServiceException("鉴权头不允许配置 " + name + "，请只填鉴权相关请求头");
            }
            builder.header(name, value);
        }
    }

    /** 留痕失败也要落库，否则客服无法回溯上次推送的原因 */
    private void recordPush(KfTicket ticket, String status, String summary)
    {
        KfTicket update = new KfTicket();
        update.setTicketId(ticket.getTicketId());
        update.setPushStatus(status);
        update.setPushTime(DateUtils.getNowDate());
        update.setPushResponse(abbreviate(summary, 500));
        update.setUpdateBy(OPERATOR);
        update.setUpdateTime(DateUtils.getNowDate());
        updateById(update);
    }

    private String abbreviate(String text)
    {
        return abbreviate(text, 200);
    }

    private String abbreviate(String text, int max)
    {
        if (StringUtils.isNull(text))
        {
            return "";
        }
        String one = text.replaceAll("\\s+", " ").trim();
        return one.length() <= max ? one : one.substring(0, max) + "...";
    }
}
