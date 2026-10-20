package com.voxio.kf.service.impl;

import java.util.Collections;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.voxio.common.exception.ServiceException;
import com.voxio.common.utils.DateUtils;
import com.voxio.common.utils.StringUtils;
import com.voxio.common.utils.crypto.SmCodec;
import com.voxio.kf.domain.KfMessage;
import com.voxio.kf.mapper.KfMessageMapper;
import com.voxio.kf.mapper.KfSessionMapper;
import com.voxio.kf.service.IKfMessageService;
import com.voxio.kf.service.IKfSessionKeyService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 消息 服务实现
 * 
 * @author voxio
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KfMessageServiceImpl extends ServiceImpl<KfMessageMapper, KfMessage> implements IKfMessageService
{
    /** 单条文本消息最大长度 */
    private static final int MAX_CONTENT_LENGTH = 2000;

    private final KfSessionMapper sessionMapper;

    private final IKfSessionKeyService sessionKeyService;


    @Override
    @Transactional
    public KfMessage appendMessage(Long sessionId, String senderType, Long senderId, String msgType,
            String content, String clientMsgId)
    {
        return append(sessionId, senderType, senderId, msgType, content, clientMsgId,
                false);
    }

    @Override
    @Transactional
    public KfMessage appendRealtimeMessage(Long sessionId, String senderType, Long senderId, String msgType,
            String content, String clientMsgId)
    {
        return append(sessionId, senderType, senderId, msgType, content, clientMsgId,
                true);
    }

    /**
     * @param incoming true = 网关实时消息：会话侧的「占号 + 未读 + 首次响应 + 活跃时间」合成一条 UPDATE。
     *                 阶段11 基线里一条消息要 7~8 次公网数据库往返，ACK 延迟 827ms 大半耗在排队等库，
     *                 这几件事都改同一行，本来就该一次写完。
     *                 false = 系统消息/离线留言/后台写入，保持原有语义（不动未读与首响）。
     */
    private KfMessage append(Long sessionId, String senderType, Long senderId, String msgType,
            String content, String clientMsgId, boolean incoming)
    {
        if (StringUtils.isNotEmpty(content) && content.length() > MAX_CONTENT_LENGTH)
        {
            throw new ServiceException("消息内容过长，最多 " + MAX_CONTENT_LENGTH + " 字");
        }
        if (StringUtils.isEmpty(content))
        {
            throw new ServiceException("消息内容不能为空");
        }
        KfMessage message = new KfMessage();
        message.setSessionId(sessionId);
        message.setSenderType(senderType);
        message.setSenderId(senderId);
        message.setMsgType(StringUtils.isNotEmpty(msgType) ? msgType : KfMessage.TYPE_TEXT);
        // 等保要求核心业务数据密文落库：写入密文，返回给调用方的仍是明文（网关要用它加密下发）
        String cipher = StringUtils.isEmpty(content) ? content : sessionKeyService.encryptForStore(sessionId, content);
        boolean encrypted = cipher != null && cipher.startsWith(SmCodec.STORE_PREFIX);
        if (!encrypted && StringUtils.isNotEmpty(content))
        {
            // 交付配置（voxio.crypto.enforce=true）下 isEnabled() 恒真，这条分支走不到；走到就是有人用
            // --voxio.crypto.enabled=false --voxio.crypto.enforce=false 起了进程。明文写进库里只有门禁复查能发现，
            // 而门禁查的是库，日志才是当下就能看见的那一层（阶段14 批次4 实测抓到过一条）。
            log.warn("[消息落库] 会话 {} 的消息以明文落库（加密开关未生效），请检查该进程的 voxio.crypto 启动参数", sessionId);
        }
        message.setContent(cipher);
        message.setEncryptFlag(encrypted ? KfMessage.ENCRYPT_YES : KfMessage.ENCRYPT_NO);
        message.setAlgoType(encrypted ? "SM4-GCM" : null);
        message.setKeyVersion(encrypted ? sessionKeyService.getSessionKeyVersion(sessionId) : null);
        message.setClientMsgId(StringUtils.isNotEmpty(clientMsgId) ? clientMsgId : null);
        message.setDeliverStatus(KfMessage.DELIVER_STORED);
        message.setCreateTime(DateUtils.getNowDate());

        // 同一事务内先递增行锁再取号，保证并发下 seq 不重复
        if (incoming)
        {
            sessionMapper.applyIncoming(sessionId, message.getCreateTime(),
                    KfMessage.SENDER_VISITOR.equals(senderType) ? 1 : 0,
                    KfMessage.SENDER_AGENT.equals(senderType) ? 1 : 0,
                    KfMessage.SENDER_AGENT.equals(senderType) ? 1 : 0);
        }
        else
        {
            sessionMapper.incrLastSeq(sessionId, message.getCreateTime());
        }
        message.setSeq(sessionMapper.selectLastSeq(sessionId));
        save(message);
        return message;
    }

    @Override
    public KfMessage appendSystemMessage(Long sessionId, String content, String msgType)
    {
        return appendMessage(sessionId, KfMessage.SENDER_SYSTEM, null, msgType, content, null);
    }

    @Override
    public KfMessage findByClientMsgId(Long sessionId, String clientMsgId)
    {
        if (StringUtils.isEmpty(clientMsgId))
        {
            return null;
        }
        return decryptInPlace(getOne(new LambdaQueryWrapper<KfMessage>()
                .eq(KfMessage::getSessionId, sessionId)
                .eq(KfMessage::getClientMsgId, clientMsgId)
                .last("limit 1"), false));
    }

    @Override
    public List<KfMessage> listAfter(Long sessionId, int sinceSeq, int limit)
    {
        return decryptAll(list(new LambdaQueryWrapper<KfMessage>()
                .eq(KfMessage::getSessionId, sessionId)
                .gt(KfMessage::getSeq, sinceSeq)
                .orderByAsc(KfMessage::getSeq)
                .last("limit " + limit)));
    }

    @Override
    public List<KfMessage> listBefore(Long sessionId, Integer beforeSeq, int limit)
    {
        List<KfMessage> list = list(new LambdaQueryWrapper<KfMessage>()
                .eq(KfMessage::getSessionId, sessionId)
                .lt(StringUtils.isNotNull(beforeSeq), KfMessage::getSeq, beforeSeq)
                .orderByDesc(KfMessage::getSeq)
                .last("limit " + limit));
        Collections.reverse(list);
        return decryptAll(list);
    }

    /**
     * 读取侧解密：接口与网关拿到的始终是明文，密文只存在于库里
     *
     * 密钥丢失或密文被改动时置空内容而不是抛异常：一条坏消息不该让整屏历史记录打不开。
     */
    private List<KfMessage> decryptAll(List<KfMessage> list)
    {
        for (KfMessage message : list)
        {
            decryptInPlace(message);
        }
        return list;
    }

    private KfMessage decryptInPlace(KfMessage message)
    {
        if (message == null)
        {
            return null;
        }
        if (KfMessage.ENCRYPT_YES.equals(message.getEncryptFlag()))
        {
            message.setContent(sessionKeyService.decryptStored(message.getSessionId(), message.getContent()));
        }
        return message;
    }

    @Override
    public int markSent(Long sessionId, String senderType)
    {
        return baseMapper.markSentBySender(sessionId, senderType);
    }

    @Override
    public int markRead(Long sessionId, String peerSenderType, Integer lastSeq)
    {
        return baseMapper.markReadBySender(sessionId, peerSenderType, lastSeq, DateUtils.getNowDate());
    }

    @Override
    public long countUndelivered(Long sessionId, String peerSenderType)
    {
        return count(new LambdaQueryWrapper<KfMessage>()
                .eq(KfMessage::getSessionId, sessionId)
                .eq(KfMessage::getSenderType, peerSenderType)
                .eq(KfMessage::getDeliverStatus, KfMessage.DELIVER_STORED));
    }
}
