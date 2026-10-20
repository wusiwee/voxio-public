package com.voxio.kf.service.impl;

import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.voxio.common.exception.ServiceException;
import com.voxio.common.utils.DateUtils;
import com.voxio.common.utils.StringUtils;
import com.voxio.kf.domain.KfOfflineMessage;
import com.voxio.kf.domain.KfTicket;
import com.voxio.kf.mapper.KfOfflineMessageMapper;
import com.voxio.kf.service.IKfConfigService;
import com.voxio.kf.service.IKfOfflineMessageService;
import com.voxio.kf.service.IKfTicketService;
import com.voxio.kf.util.KfCipherRowReader;

import lombok.RequiredArgsConstructor;

/**
 * 离线留言 服务层实现
 * 
 * @author voxio
 */
@Service
@RequiredArgsConstructor
public class KfOfflineMessageServiceImpl extends ServiceImpl<KfOfflineMessageMapper, KfOfflineMessage>
        implements IKfOfflineMessageService
{
    /** 留言内容长度上限，与表字段一致 */
    private static final int MAX_CONTENT_LENGTH = 2000;

    /** 同一访客 5 分钟内的留言条数上限，避免公开接口被刷 */
    private static final int SUBMIT_LIMIT = 5;

    /** 自动生成工单标题时截取的留言长度 */
    private static final int TITLE_LENGTH = 40;

    private final IKfConfigService configService;

    private final IKfTicketService ticketService;

    private final KfCipherRowReader cipherRowReader;

    @Override
    public boolean isInServiceTime()
    {
        if (!configService.selectBooleanByKey("kf.service.enabled", false))
        {
            return true;
        }
        String range = StringUtils.nvl(configService.selectConfigByKey("kf.service.time"), "").trim();
        int dash = range.indexOf('-');
        if (dash <= 0 || dash == range.length() - 1)
        {
            // 参数配错时按全天受理处理，避免把访客挡在门外
            return true;
        }
        LocalTime now = LocalTime.now();
        LocalTime start = parseTime(range.substring(0, dash).trim());
        LocalTime end = parseTime(range.substring(dash + 1).trim());
        if (start == null || end == null)
        {
            return true;
        }
        if (start.isAfter(end))
        {
            // 跨零点时段，如 20:00-02:00
            return !now.isBefore(start) || !now.isAfter(end);
        }
        return !now.isBefore(start) && !now.isAfter(end);
    }

    @Override
    public boolean isOfflineAllowed(int onlineAgentCount)
    {
        if (!configService.selectBooleanByKey("kf.offline.enabled", true))
        {
            return false;
        }
        return onlineAgentCount == 0 || !isInServiceTime();
    }

    @Override
    public Long submit(Long visitorId, Long sessionId, String contact, String content)
    {
        if (StringUtils.isEmpty(content) || content.trim().isEmpty())
        {
            throw new ServiceException("留言内容不能为空");
        }
        if (content.length() > MAX_CONTENT_LENGTH)
        {
            throw new ServiceException("留言内容最多 " + MAX_CONTENT_LENGTH + " 字");
        }
        if (StringUtils.isNotEmpty(contact) && contact.length() > 50)
        {
            throw new ServiceException("联系方式最多 50 字");
        }
        if (count(new LambdaQueryWrapper<KfOfflineMessage>()
                .eq(KfOfflineMessage::getVisitorId, visitorId)
                .ge(KfOfflineMessage::getCreateTime, DateUtils.addMinutes(DateUtils.getNowDate(), -5))) >= SUBMIT_LIMIT)
        {
            throw new ServiceException("留言提交过于频繁，请稍后再试");
        }

        KfOfflineMessage message = new KfOfflineMessage();
        message.setVisitorId(visitorId);
        message.setSessionId(sessionId);
        message.setContact(StringUtils.nvl(contact, ""));
        message.setContent(content.trim());
        message.setStatus(KfOfflineMessage.STATUS_PENDING);
        message.setCreateTime(DateUtils.getNowDate());
        message.setUpdateTime(DateUtils.getNowDate());
        save(message);
        return message.getMsgId();
    }

    @Override
    public List<Map<String, Object>> selectAdminList(String status, Long handlerAgentId, String keyword,
            String beginTime, String endTime)
    {
        List<Map<String, Object>> rows = baseMapper.selectAdminList(status, handlerAgentId,
                StringUtils.isNull(keyword) ? null : keyword.trim(), beginTime, endTime);
        // Map 视图不经实体类型处理器，出网前把留言正文、联系方式与访客手机邮箱解回明文
        cipherRowReader.decryptFields(rows, "content", "contact", "contactPhone", "contactEmail");
        return rows;
    }

    @Override
    public Map<String, Object> selectAdminDetail(Long msgId)
    {
        Map<String, Object> detail = baseMapper.selectAdminDetail(msgId);
        if (StringUtils.isNull(detail))
        {
            throw new ServiceException("留言不存在", 404);
        }
        cipherRowReader.decryptFields(java.util.Collections.singletonList(detail), "content", "contact");
        return detail;
    }

    @Override
    @Transactional
    public KfTicket toTicket(Long msgId, Long handlerAgentId, String title, Long operatorId, String operatorName)
    {
        KfOfflineMessage message = getById(msgId);
        if (StringUtils.isNull(message))
        {
            throw new ServiceException("留言不存在");
        }
        if (!KfOfflineMessage.STATUS_PENDING.equals(message.getStatus()))
        {
            throw new ServiceException("该留言已处理，不能重复转工单");
        }

        KfTicket ticket = new KfTicket();
        String brief = message.getContent().replaceAll("\\s+", " ").trim();
        ticket.setTitle(StringUtils.isNotEmpty(title) ? title.trim()
                : "离线留言：" + (brief.length() > TITLE_LENGTH ? brief.substring(0, TITLE_LENGTH) : brief));
        ticket.setContent("访客留言内容：" + message.getContent()
                + (StringUtils.isEmpty(message.getContact()) ? "" : "\n联系方式：" + message.getContact()));
        ticket.setCategory("other");
        ticket.setPriority("2");
        ticket.setSource(KfTicket.SOURCE_OFFLINE);
        ticket.setContact(StringUtils.nvl(message.getContact(), ""));
        ticket.setSessionId(message.getSessionId());
        ticket.setVisitorId(message.getVisitorId());
        ticket.setHandlerAgentId(handlerAgentId);
        KfTicket created = ticketService.createBy(ticket, KfTicket.CREATOR_ADMIN, operatorId, operatorName,
                handlerAgentId);

        KfOfflineMessage update = new KfOfflineMessage();
        update.setMsgId(msgId);
        update.setStatus(KfOfflineMessage.STATUS_TICKET);
        update.setTicketId(created.getTicketId());
        update.setHandlerAgentId(handlerAgentId);
        update.setUpdateTime(DateUtils.getNowDate());
        updateById(update);
        return created;
    }

    @Override
    @Transactional
    public void close(Long msgId)
    {
        KfOfflineMessage message = getById(msgId);
        if (StringUtils.isNull(message))
        {
            throw new ServiceException("留言不存在");
        }
        if (KfOfflineMessage.STATUS_CLOSED.equals(message.getStatus()))
        {
            throw new ServiceException("该留言已关闭");
        }
        if (KfOfflineMessage.STATUS_TICKET.equals(message.getStatus()))
        {
            throw new ServiceException("已转工单的留言请在工单中处理");
        }
        KfOfflineMessage update = new KfOfflineMessage();
        update.setMsgId(msgId);
        update.setStatus(KfOfflineMessage.STATUS_CLOSED);
        update.setUpdateTime(DateUtils.getNowDate());
        updateById(update);
    }

    private LocalTime parseTime(String text)
    {
        try
        {
            String[] parts = text.split(":");
            int hour = Integer.parseInt(parts[0]);
            int minute = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
            return LocalTime.of(hour, minute);
        }
        catch (NumberFormatException | java.time.DateTimeException e)
        {
            return null;
        }
    }
}
