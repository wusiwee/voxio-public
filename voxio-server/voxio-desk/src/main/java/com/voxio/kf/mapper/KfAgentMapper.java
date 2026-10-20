package com.voxio.kf.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.voxio.kf.domain.KfAgent;

/**
 * 客服账号 数据层
 * 
 * @author voxio
 */
public interface KfAgentMapper extends BaseMapper<KfAgent>
{
    /**
     * 逻辑删除并释放登录账号占用的唯一键，保证同一账号名可重新启用
     * 
     * @param agentId 客服ID
     * @return 结果
     */
    @Update("update kf_agent set del_flag = '2', agent_no = concat(agent_no, '#del#', agent_id) "
            + "where agent_id = #{agentId} and del_flag = '0'")
    public int deleteAgentById(Long agentId);

    /**
     * 更新在线状态（由网关在连接建立/断开时调用）
     * 
     * @param agentId 客服ID
     * @param onlineStatus 在线状态（0离线 1在线 2忙碌）
     * @return 结果
     */
    @Update("update kf_agent set online_status = #{onlineStatus}, update_time = now() "
            + "where agent_id = #{agentId} and del_flag = '0'")
    public int updateOnlineStatus(@Param("agentId") Long agentId, @Param("onlineStatus") String onlineStatus);

    /**
     * 接待中会话数增减（结果不小于 0）
     * 
     * @param agentId 客服ID
     * @param delta 增量
     * @return 结果
     */
    @Update("update kf_agent set current_load = greatest(current_load + #{delta}, 0) where agent_id = #{agentId}")
    public int addLoad(@Param("agentId") Long agentId, @Param("delta") int delta);

    /**
     * 挑选可接待的客服：在线、启用、未达个人上限与全局上限，按负载升序（空闲优先），同负载按ID升序
     * 
     * @param globalMax 全局单客服最大并发
     * @return 客服ID，无可用客服返回空
     */
    @Select("select agent_id from kf_agent where del_flag = '0' and status = '0' and online_status = '1' "
            + "and current_load < least(accept_max, #{globalMax}) "
            + "order by current_load asc, agent_id asc limit 1")
    public Long selectAvailableAgent(@Param("globalMax") int globalMax);

    /**
     * 按会话表重算接待中数量（自愈用，避免异常路径导致负载计数漂移）
     * 
     * @param agentId 客服ID
     * @return 结果
     */
    @Update("update kf_agent set current_load = (select count(*) from kf_session where agent_id = #{agentId} and status = '1') "
            + "where agent_id = #{agentId}")
    public int recalcLoad(@Param("agentId") Long agentId);
}
