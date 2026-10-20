package com.voxio.kf.service.impl;

import java.util.Date;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.voxio.common.exception.ServiceException;
import com.voxio.common.utils.DateUtils;
import com.voxio.common.utils.StringUtils;
import com.voxio.kf.domain.KfAgent;
import com.voxio.kf.domain.KfMessage;
import com.voxio.kf.domain.KfSession;
import com.voxio.kf.domain.KfVisitor;
import com.voxio.kf.event.KfChatEvent;
import com.voxio.kf.event.KfChatEventPublisher;
import com.voxio.kf.mapper.KfAgentMapper;
import com.voxio.kf.mapper.KfSessionMapper;
import com.voxio.kf.mapper.KfVisitorMapper;
import com.voxio.kf.service.IKfAgentService;
import com.voxio.kf.service.IKfConfigService;
import com.voxio.kf.service.IKfMessageService;
import com.voxio.kf.service.IKfSessionService;
import com.voxio.kf.service.IKfVisitorTokenService;
import com.voxio.kf.util.KfCipherRowReader;

import lombok.RequiredArgsConstructor;

/**
 * 会话 服务实现
 * 
 * @author voxio
 */
@Service
@RequiredArgsConstructor
public class KfSessionServiceImpl extends ServiceImpl<KfSessionMapper, KfSession> implements IKfSessionService
{
    private static final String OPERATOR = "gateway";

    private final KfVisitorMapper visitorMapper;

    private final KfAgentMapper agentMapper;


    private final IKfMessageService messageService;

    private final IKfConfigService configService;

    private final KfChatEventPublisher eventPublisher;

    private final IKfVisitorTokenService visitorTokenService;

    private final KfCipherRowReader cipherRowReader;

    @Override
    @Transactional
    public KfSession createOrResume(String visitorNo, String nickname, String ip, String userAgent,
            String referrer, String sourcePage)
    {
        if (StringUtils.isEmpty(visitorNo))
        {
            throw new ServiceException("访客标识不能为空");
        }
        KfVisitor visitor = findOrCreateVisitor(visitorNo, nickname, ip, userAgent, referrer);

        KfSession active = findActiveByVisitor(visitor.getVisitorId());
        if (StringUtils.isNotNull(active))
        {
            visitorTokenService.rebindSession(visitor.getVisitorId(), active.getSessionId());
            return active;
        }

        KfSession session = new KfSession();
        session.setSessionNo(nextSessionNo());
        session.setVisitorId(visitor.getVisitorId());
        session.setStatus(KfSession.STATUS_QUEUED);
        session.setQueueTime(DateUtils.getNowDate());
        session.setUnreadAgent(0);
        session.setUnreadVisitor(0);
        session.setLastSeq(0);
        session.setSourcePage(StringUtils.nvl(sourcePage, ""));
        session.setCreateBy(OPERATOR);
        session.setCreateTime(DateUtils.getNowDate());
        save(session);

        messageService.appendSystemMessage(session.getSessionId(),
                configService.selectConfigByKey("kf.welcome.text"), KfMessage.TYPE_EVENT);
        // assignAvailable 已经把分配后的会话读回来了（排队分支返回的是同一个实体），这里不再回查一次主键
        KfSession assigned = assignAvailable(session.getSessionId());
        return StringUtils.isNotNull(assigned) ? assigned : getById(session.getSessionId());
    }

    @Override
    public KfSession getSession(Long sessionId)
    {
        return getById(sessionId);
    }

    @Override
    public KfSession findActiveByVisitor(Long visitorId)
    {
        return getOne(new LambdaQueryWrapper<KfSession>()
                .eq(KfSession::getVisitorId, visitorId)
                .in(KfSession::getStatus, KfSession.STATUS_QUEUED, KfSession.STATUS_SERVING)
                .orderByDesc(KfSession::getSessionId)
                .last("limit 1"), false);
    }

    @Override
    public List<KfSession> listByAgent(Long agentId, String status)
    {
        return list(new LambdaQueryWrapper<KfSession>()
                .eq(KfSession::getAgentId, agentId)
                .eq(StringUtils.isNotEmpty(status), KfSession::getStatus, status)
                .orderByDesc(KfSession::getLastMsgTime)
                .orderByDesc(KfSession::getSessionId));
    }

    @Override
    public int queuePosition(KfSession session)
    {
        if (StringUtils.isNull(session) || !KfSession.STATUS_QUEUED.equals(session.getStatus()))
        {
            return 0;
        }
        return (int) count(new LambdaQueryWrapper<KfSession>()
                .eq(KfSession::getStatus, KfSession.STATUS_QUEUED)
                .le(KfSession::getQueueTime, session.getQueueTime()));
    }

    @Override
    public int countQueued()
    {
        return (int) count(new LambdaQueryWrapper<KfSession>().eq(KfSession::getStatus, KfSession.STATUS_QUEUED));
    }

    @Override
    @Transactional
    public KfSession assignAvailable(Long sessionId)
    {
        KfSession session = getById(sessionId);
        if (StringUtils.isNull(session) || !KfSession.STATUS_QUEUED.equals(session.getStatus()))
        {
            return session;
        }
        if (!configService.selectBooleanByKey("kf.assign.enabled", true))
        {
            return queueWait(session);
        }
        // 队首优先：只要有更早进入队列的会话在等，新会话不能插队占用刚空出的客服
        long ahead = count(new LambdaQueryWrapper<KfSession>()
                .eq(KfSession::getStatus, KfSession.STATUS_QUEUED)
                .lt(KfSession::getQueueTime, session.getQueueTime()));
        if (ahead > 0)
        {
            return queueWait(session);
        }
        int globalMax = configService.selectIntByKey("kf.assign.maxPerAgent", 5);
        Long agentId = agentMapper.selectAvailableAgent(globalMax);
        if (StringUtils.isNull(agentId))
        {
            return queueWait(session);
        }
        return doAssign(session, agentId, null);
    }

    @Override
    @Transactional
    public KfSession assignManually(Long sessionId, Long agentId, String operator)
    {
        KfSession session = getById(sessionId);
        if (StringUtils.isNull(session))
        {
            throw new ServiceException("会话不存在");
        }
        if (!KfSession.STATUS_QUEUED.equals(session.getStatus()))
        {
            throw new ServiceException("只有排队中的会话可以人工分配");
        }
        KfAgent agent = agentMapper.selectById(agentId);
        if (StringUtils.isNull(agent) || !"0".equals(agent.getStatus()))
        {
            throw new ServiceException("目标客服不存在或已停用");
        }
        if (!IKfAgentService.STATUS_ONLINE.equals(agent.getOnlineStatus())
                && !IKfAgentService.STATUS_BUSY.equals(agent.getOnlineStatus()))
        {
            throw new ServiceException("目标客服当前离线，无法分配");
        }
        int globalMax = configService.selectIntByKey("kf.assign.maxPerAgent", 5);
        if (StringUtils.nvl(agent.getCurrentLoad(), 0) >= Math.min(StringUtils.nvl(agent.getAcceptMax(), globalMax), globalMax))
        {
            throw new ServiceException("目标客服接待数已达上限");
        }
        KfSession assigned = doAssign(session, agentId, null);
        if (StringUtils.isNull(assigned))
        {
            throw new ServiceException("会话已被分配或已结束，请刷新后重试");
        }
        messageService.appendSystemMessage(sessionId, StringUtils.format("会话已由 {} 人工分配给客服 {}",
                operator, StringUtils.nvl(agent.getNickName(), agent.getAgentNo())), KfMessage.TYPE_EVENT);
        return assigned;
    }

    /**
     * 客服自己接入排队会话：走与人工分配完全相同的路径
     * （容量、在线状态、并发抢占这些判定一个都不能绕过，只是操作人换成了客服自己）。
     * 接哪条由调用方给：留痕要能答出「谁在什么时候接了哪条会话」，服务端悄悄挑队首就答不出来。
     */
    @Override
    @Transactional
    public KfSession acceptSession(Long sessionId, Long agentId, String operator)
    {
        KfSession session = getById(sessionId);
        if (StringUtils.isNull(session))
        {
            throw new ServiceException("会话不存在");
        }
        if (!KfSession.STATUS_QUEUED.equals(session.getStatus()))
        {
            throw new ServiceException("这条会话已经不在排队队列里，请刷新列表后再选");
        }
        return assignManually(sessionId, agentId, operator + "（一键接入）");
    }

    /**
     * 会话继续排队：推送当前位置与排队提示
     */
    private KfSession queueWait(KfSession session)
    {
        int position = queuePosition(session);
        eventPublisher.publishToVisitor(session.getVisitorId(),
                new KfChatEvent("SESSION_QUEUED", session.getSessionId()).with("position", position)
                        .with("tip", configService.selectConfigByKey("kf.queue.tip").replace("{position}",
                                String.valueOf(position))));
        return session;
    }

    @Override
    @Transactional
    public int pullQueuedForAgent(Long agentId)
    {
        if (!configService.selectBooleanByKey("kf.assign.enabled", true))
        {
            return 0;
        }
        KfAgent agent = agentMapper.selectById(agentId);
        if (StringUtils.isNull(agent) || !"0".equals(agent.getStatus()) || !"1".equals(agent.getOnlineStatus()))
        {
            return 0;
        }
        int globalMax = configService.selectIntByKey("kf.assign.maxPerAgent", 5);
        int capacity = Math.min(StringUtils.nvl(agent.getAcceptMax(), globalMax), globalMax)
                - StringUtils.nvl(agent.getCurrentLoad(), 0);
        if (capacity <= 0)
        {
            return 0;
        }
        List<KfSession> queued = list(new LambdaQueryWrapper<KfSession>()
                .eq(KfSession::getStatus, KfSession.STATUS_QUEUED)
                .orderByAsc(KfSession::getQueueTime)
                .orderByAsc(KfSession::getSessionId)
                .last("limit " + capacity));
        int assigned = 0;
        for (KfSession session : queued)
        {
            if (StringUtils.isNotNull(doAssign(session, agentId, null)))
            {
                assigned++;
            }
        }
        return assigned;
    }

    @Override
    @Transactional
    public int assignQueuedToOnlineAgents()
    {
        List<KfAgent> agents = agentMapper.selectList(new LambdaQueryWrapper<KfAgent>()
                .eq(KfAgent::getStatus, "0")
                .in(KfAgent::getOnlineStatus, IKfAgentService.STATUS_ONLINE, IKfAgentService.STATUS_BUSY));
        int assigned = 0;
        for (KfAgent agent : agents)
        {
            assigned += pullQueuedForAgent(agent.getAgentId());
        }
        return assigned;
    }

    @Override
    @Transactional
    public void endSession(Long sessionId, String endBy, Long operatorAgentId, String reason)
    {
        KfSession session = getById(sessionId);
        if (StringUtils.isNull(session))
        {
            throw new ServiceException("会话不存在");
        }
        if (KfSession.STATUS_ENDED.equals(session.getStatus()))
        {
            return;
        }
        KfSession update = new KfSession();
        update.setSessionId(sessionId);
        update.setStatus(KfSession.STATUS_ENDED);
        update.setEndTime(DateUtils.getNowDate());
        update.setEndBy(endBy);
        update.setCloseReason(StringUtils.nvl(reason, ""));
        update.setUnreadAgent(0);
        update.setUnreadVisitor(0);
        update.setUpdateBy(OPERATOR);
        update.setUpdateTime(DateUtils.getNowDate());
        updateById(update);

        if (StringUtils.isNotNull(session.getAgentId()))
        {
            agentMapper.addLoad(session.getAgentId(), -1);
            eventPublisher.publishToAgent(session.getAgentId(),
                    new KfChatEvent("SESSION_END", sessionId).with("endBy", endBy));
            pullQueuedForAgent(session.getAgentId());
        }
    }

    @Override
    public void changeAgentOnlineStatus(Long agentId, String onlineStatus)
    {
        agentMapper.updateOnlineStatus(agentId, onlineStatus);
        if (IKfAgentService.STATUS_ONLINE.equals(onlineStatus))
        {
            pullQueuedForAgent(agentId);
        }
    }

    /**
     * 执行分配：抢占会话 -> 负载 +1 -> 通知双方
     * 
     * @return 分配成功返回会话，被其他线程抢占返回空
     */
    private KfSession doAssign(KfSession session, Long agentId, Long fromAgentId)
    {
        Date now = DateUtils.getNowDate();
        int rows = baseMapper.tryAssign(session.getSessionId(), agentId, now);
        if (rows == 0)
        {
            return null;
        }
        agentMapper.addLoad(agentId, 1);
        if (StringUtils.isNotNull(fromAgentId))
        {
            agentMapper.addLoad(fromAgentId, -1);
        }
        KfAgent agent = agentMapper.selectById(agentId);
        KfVisitor visitor = visitorMapper.selectById(session.getVisitorId());
        eventPublisher.publishToAgent(agentId, new KfChatEvent("SESSION_NEW", session.getSessionId())
                .with("visitorNick", StringUtils.nvl(visitor.getNickname(), "访客")));
        eventPublisher.publishToVisitor(session.getVisitorId(), new KfChatEvent("SESSION_ASSIGNED", session.getSessionId())
                .with("agentNick", StringUtils.nvl(agent.getNickName(), "客服"))
                .with("welcome", StringUtils.isNotEmpty(agent.getWelcomeText())
                        ? agent.getWelcomeText()
                        : configService.selectConfigByKey("kf.welcome.text")));
        return getById(session.getSessionId());
    }

    @Override
    public java.util.List<java.util.Map<String, Object>> listAgentSessionView(Long agentId, String status)
    {
        java.util.List<java.util.Map<String, Object>> rows = baseMapper.selectAgentSessionView(agentId,
                StringUtils.nvl(status, ""));
        // 会话预览的最后一句在库里是密文，出网前解回明文（客服端与后台一样只看明文）
        cipherRowReader.decrypt(rows, "lastContent");
        return rows;
    }

    @Override
    public java.util.List<java.util.Map<String, Object>> listAgentHistory(Long agentId, String status,
            String keyword, Date beginTime, Date endTime)
    {
        java.util.List<java.util.Map<String, Object>> rows = baseMapper.selectAgentHistory(agentId,
                StringUtils.nvl(status, ""), StringUtils.nvl(keyword, ""), beginTime, endTime);
        cipherRowReader.decrypt(rows, "lastContent");
        return rows;
    }

    @Override
    public void bumpUnread(Long sessionId, String senderType)
    {
        // 访客发的消息计入客服未读，客服发的消息计入访客未读
        if (KfMessage.SENDER_VISITOR.equals(senderType))
        {
            baseMapper.addUnreadAgent(sessionId, 1);
        }
        else if (KfMessage.SENDER_AGENT.equals(senderType))
        {
            baseMapper.addUnreadVisitor(sessionId, 1);
        }
    }

    @Override
    @Transactional
    public int markRead(Long sessionId, String readerType, Integer lastSeq)
    {
        String peerType = KfMessage.SENDER_AGENT.equals(readerType) ? KfMessage.SENDER_VISITOR : KfMessage.SENDER_AGENT;
        int rows = messageService.markRead(sessionId, peerType, lastSeq);
        if (KfMessage.SENDER_AGENT.equals(readerType))
        {
            baseMapper.clearUnreadAgent(sessionId);
        }
        else
        {
            baseMapper.clearUnreadVisitor(sessionId);
        }
        return rows;
    }

    @Override
    public void markFirstResponse(Long sessionId)
    {
        baseMapper.markFirstResponse(sessionId);
    }

    private KfVisitor findOrCreateVisitor(String visitorNo, String nickname, String ip, String userAgent, String referrer)
    {
        KfVisitor visitor = visitorMapper.selectOne(new LambdaQueryWrapper<KfVisitor>()
                .eq(KfVisitor::getVisitorNo, visitorNo).last("limit 1"));
        Date now = DateUtils.getNowDate();
        if (StringUtils.isNull(visitor))
        {
            visitor = new KfVisitor();
            visitor.setVisitorNo(visitorNo);
            visitor.setNickname(StringUtils.isNotEmpty(nickname) ? nickname : "访客" + visitorNo.substring(Math.max(0, visitorNo.length() - 4)));
            visitor.setIp(StringUtils.nvl(ip, ""));
            visitor.setUserAgent(StringUtils.nvl(userAgent, ""));
            visitor.setReferrer(StringUtils.nvl(referrer, ""));
            visitor.setSource("web");
            visitor.setSessionCount(1);
            visitor.setLastVisitTime(now);
            visitor.setCreateBy(OPERATOR);
            visitor.setCreateTime(now);
            visitorMapper.insert(visitor);
        }
        else
        {
            KfVisitor update = new KfVisitor();
            update.setVisitorId(visitor.getVisitorId());
            update.setIp(StringUtils.nvl(ip, visitor.getIp()));
            update.setUserAgent(StringUtils.nvl(userAgent, visitor.getUserAgent()));
            update.setReferrer(StringUtils.nvl(referrer, visitor.getReferrer()));
            update.setSessionCount(StringUtils.nvl(visitor.getSessionCount(), 0) + 1);
            update.setLastVisitTime(now);
            update.setUpdateTime(now);
            if (StringUtils.isNotEmpty(nickname))
            {
                update.setNickname(nickname);
            }
            visitorMapper.updateById(update);
            visitor.setSessionCount(update.getSessionCount());
        }
        return visitor;
    }

    private String nextSessionNo()
    {
        return "S" + DateUtils.dateTimeNow("yyyyMMddHHmmss") + ThreadLocalRandom.current().nextInt(1000, 9999);
    }
}
