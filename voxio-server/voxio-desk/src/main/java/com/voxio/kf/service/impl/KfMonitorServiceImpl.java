package com.voxio.kf.service.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.voxio.common.utils.StringUtils;
import com.voxio.kf.mapper.KfMonitorMapper;
import com.voxio.kf.service.IKfMonitorService;
import com.voxio.kf.util.KfCipherRowReader;

import lombok.RequiredArgsConstructor;

/**
 * 管理后台只读监控服务实现
 * 
 * @author voxio
 */
@Service
@RequiredArgsConstructor
public class KfMonitorServiceImpl implements IKfMonitorService
{
    /** 会话详情默认带出的消息条数 */
    private static final int DETAIL_MESSAGE_LIMIT = 200;

    private final KfMonitorMapper monitorMapper;

    private final KfCipherRowReader cipherRowReader;

    @Override
    public List<Map<String, Object>> selectSessionList(String status, Long agentId, Long visitorId, String keyword,
            String beginTime, String endTime)
    {
        List<Map<String, Object>> rows = monitorMapper.selectSessionView(StringUtils.nvl(status, "active"), agentId,
                visitorId, trim(keyword), beginTime, endTime);
        cipherRowReader.decrypt(rows, "lastContent");
        cipherRowReader.decryptFields(rows, "contactPhone", "contactEmail");
        return rows;
    }

    @Override
    public Map<String, Object> selectSessionDetail(Long sessionId)
    {
        Map<String, Object> detail = monitorMapper.selectSessionDetail(sessionId);
        if (StringUtils.isNull(detail))
        {
            return null;
        }
        Map<String, Object> result = new LinkedHashMap<>(detail);
        cipherRowReader.decryptFields(Collections.singletonList(result), "contactPhone", "contactEmail");
        // 只要最近 N 条：交给 SQL 的 limit，而不是把整个会话的消息全捞进内存再截断
        List<Map<String, Object>> recent = new ArrayList<>(
                monitorMapper.selectChatLogPage(sessionId, null, null, null, null, null, null, null, null,
                        DETAIL_MESSAGE_LIMIT));
        // 查询按 msg_id 倒序，取最近若干条后翻转为正序，便于界面直接顺序展示
        Collections.reverse(recent);
        cipherRowReader.decrypt(recent, "content");
        result.put("messages", recent);
        return result;
    }

    @Override
    public List<Map<String, Object>> selectAgentStatus(String onlineStatus, String keyword)
    {
        // 投影里已经没有密文列，这一路不需要逐行解密
        return monitorMapper.selectAgentStatus(onlineStatus, trim(keyword));
    }

    @Override
    public List<Map<String, Object>> selectChatLog(Long sessionId, Long visitorId, Long agentId, String senderType,
            String msgType, String keyword, String beginTime, String endTime)
    {
        List<Map<String, Object>> rows = monitorMapper.selectChatLog(sessionId, visitorId, agentId, senderType,
                msgType, trim(keyword), beginTime, endTime);
        cipherRowReader.decrypt(rows, "content");
        return rows;
    }

    @Override
    public List<Map<String, Object>> selectChatLogPage(Long sessionId, Long visitorId, Long agentId, String senderType,
            String msgType, String keyword, String beginTime, String endTime, Long cursorMsgId, int pageSize)
    {
        List<Map<String, Object>> rows = monitorMapper.selectChatLogPage(sessionId, visitorId, agentId, senderType,
                msgType, trim(keyword), beginTime, endTime, cursorMsgId, pageSize);
        cipherRowReader.decrypt(rows, "content");
        return rows;
    }

    @Override
    public Map<String, Object> selectMessage(Long msgId)
    {
        Map<String, Object> row = monitorMapper.selectMessageById(msgId);
        if (StringUtils.isNotNull(row))
        {
            cipherRowReader.decrypt(row, "content");
        }
        return row;
    }

    private static String trim(String value)
    {
        return StringUtils.isNull(value) ? null : value.trim();
    }

}
