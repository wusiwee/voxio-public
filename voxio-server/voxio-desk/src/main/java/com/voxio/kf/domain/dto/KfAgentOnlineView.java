package com.voxio.kf.domain.dto;

import com.voxio.kf.domain.KfAgent;

/**
 * 在线客服的响应视图
 *
 * 之前直接回 {@link KfAgent} 实体：SQL 虽然只 select 了六列，序列化出来的 JSON 却仍带着实体其余字段
 * （口令哈希、加密联系方式、登录 IP 等都是恒为 null 的键），既让契约里多了些"看着有其实没有"的字段，
 * 也让"这个接口会不会把口令带出去"变成需要解释的事。视图只声明真正要用的六列，问题不再存在。
 *
 * @author voxio
 */
public record KfAgentOnlineView(Long agentId, String agentNo, String nickName, String onlineStatus,
        Integer currentLoad, Integer acceptMax)
{
    public static KfAgentOnlineView from(KfAgent agent)
    {
        return new KfAgentOnlineView(agent.getAgentId(), agent.getAgentNo(), agent.getNickName(),
                agent.getOnlineStatus(), agent.getCurrentLoad(), agent.getAcceptMax());
    }
}
