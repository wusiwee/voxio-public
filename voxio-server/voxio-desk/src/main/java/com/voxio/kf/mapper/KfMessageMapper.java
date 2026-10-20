package com.voxio.kf.mapper;

import java.util.Date;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.voxio.kf.domain.KfMessage;

/**
 * 消息 数据层
 * 
 * @author voxio
 */
public interface KfMessageMapper extends BaseMapper<KfMessage>
{
    /**
     * 将某发送方在会话内的历史消息标记为已送达
     * 
     * @param sessionId 会话ID
     * @param senderType 发送方类型
     * @return 结果
     */
    @Update("update kf_message set deliver_status = '1' where session_id = #{sessionId} "
            + "and sender_type = #{senderType} and deliver_status = '0'")
    public int markSentBySender(@Param("sessionId") Long sessionId, @Param("senderType") String senderType);

    /**
     * 将某发送方在会话内 seq 以下的消息标记为已读
     * 
     * @param sessionId 会话ID
     * @param senderType 被阅读的消息发送方类型
     * @param lastSeq 已读到的序号
     * @param readTime 已读时间
     * @return 结果
     */
    @Update("update kf_message set deliver_status = '2', read_time = #{readTime} where session_id = #{sessionId} "
            + "and sender_type = #{senderType} and seq <= #{lastSeq} and deliver_status <> '2'")
    public int markReadBySender(@Param("sessionId") Long sessionId, @Param("senderType") String senderType,
            @Param("lastSeq") Integer lastSeq, @Param("readTime") Date readTime);
}
