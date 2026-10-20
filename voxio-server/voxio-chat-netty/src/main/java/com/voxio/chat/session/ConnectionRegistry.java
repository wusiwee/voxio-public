package com.voxio.chat.session;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Component;

import com.voxio.chat.crypto.ChatCryptoService;
import com.voxio.chat.protocol.ChatCmd;
import com.voxio.chat.protocol.ChatEnvelope;
import com.voxio.chat.protocol.ChatPrincipal;
import com.voxio.common.core.redis.RedisCache;

import io.netty.channel.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 连接注册表
 * 
 * 进程内维护 principal -> Channel 映射，Redis 记录在线标记供管理端与其他模块查询。
 * 
 * @author voxio
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConnectionRegistry
{
    /** Redis 在线键前缀（客服） */
    private static final String ONLINE_AGENT = "kf:online:agent:";

    /** Redis 在线键前缀（访客） */
    private static final String ONLINE_VISITOR = "kf:online:visitor:";

    /** 在线键有效期（秒），心跳续期 */
    private static final int ONLINE_TTL_SECONDS = 120;

    private final Map<String, Channel> channels = new ConcurrentHashMap<>();

    private final RedisCache redisCache;

    private final ChatCryptoService cryptoService;

    /**
     * 注册连接，返回被替换的旧连接（同账号重复登录）
     * 
     * @param principal 主体
     * @param channel 连接
     * @return 旧连接，可为空
     */
    public Channel register(ChatPrincipal principal, Channel channel)
    {
        Channel previous = channels.put(principal.key(), channel);
        touch(principal);
        return previous;
    }

    /**
     * 注销连接（仅当当前注册的就是该连接，避免旧连接关闭误删新连接）
     * 
     * @param principal 主体
     * @param channel 连接
     */
    /**
     * 注销连接
     *
     * @return 是否确实移除了本连接；同账号重连后旧连接的关闭不应影响新连接
     */
    public boolean unregister(ChatPrincipal principal, Channel channel)
    {
        boolean removed = channels.remove(principal.key(), channel);
        if (!channels.containsKey(principal.key()))
        {
            redisCache.deleteObject(onlineKey(principal));
        }
        return removed;
    }

    /**
     * 心跳续期
     * 
     * @param principal 主体
     */
    public void touch(ChatPrincipal principal)
    {
        redisCache.setCacheObject(onlineKey(principal), principal.key(), ONLINE_TTL_SECONDS, TimeUnit.SECONDS);
    }

    public boolean isOnline(String key)
    {
        Channel channel = channels.get(key);
        return channel != null && channel.isActive();
    }

    public boolean isAgentOnline(Long agentId)
    {
        return isOnline(ChatPrincipal.key(ChatPrincipal.TYPE_AGENT, agentId));
    }

    public boolean isVisitorOnline(Long visitorId)
    {
        return isOnline(ChatPrincipal.key(ChatPrincipal.TYPE_VISITOR, visitorId));
    }

    /**
     * 向已注册连接写入数据
     * 
     * @param key 注册键
     * @param payload 信封
     * @return 是否送达
     */
    public boolean send(String key, Object payload)
    {
        Channel channel = channels.get(key);
        if (channel == null || !channel.isActive())
        {
            return false;
        }
        return send(channel, payload);
    }

    /**
     * 写入并就地完成加密：聊天帧在没有会话密钥时直接拒发，宁可留待 SYNC 补发也不外发明文
     *
     * @param channel 目标连接
     * @param payload 信封
     * @return 是否送达
     */
    public boolean send(Channel channel, Object payload)
    {
        if (payload instanceof ChatEnvelope envelope)
        {
            if (ChatCmd.CHAT.equals(envelope.getCmd()) && cryptoService.isEnabled()
                    && !cryptoService.hasKey(channel, envelope.getSid()))
            {
                log.info("[网关加密] 会话 {} 在连接 {} 尚未协商密钥，本条消息留待 SYNC 补发",
                        envelope.getSid(), channel.id().asShortText());
                return false;
            }
            cryptoService.wrapOutbound(channel, envelope);
        }
        channel.writeAndFlush(payload);
        return true;
    }

    public int activeCount()
    {
        return (int) channels.values().stream().filter(Channel::isActive).count();
    }

    private String onlineKey(ChatPrincipal principal)
    {
        return principal.isAgent() ? ONLINE_AGENT + principal.getId() : ONLINE_VISITOR + principal.getId();
    }
}
