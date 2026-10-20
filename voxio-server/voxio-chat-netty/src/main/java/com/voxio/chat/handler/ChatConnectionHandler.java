package com.voxio.chat.handler;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.voxio.chat.crypto.ChatCryptoService;
import com.voxio.chat.dispatch.ChatMessageDispatcher;
import com.voxio.chat.metrics.GatewayMetrics;
import com.voxio.chat.protocol.ChatCmd;
import com.voxio.chat.protocol.ChatEnvelope;
import com.voxio.chat.protocol.ChatError;
import com.voxio.chat.protocol.ChatPrincipal;
import com.voxio.chat.session.ChatAuthService;
import com.voxio.chat.session.ConnectionRegistry;
import com.voxio.chat.session.SimpleRateLimiter;
import com.voxio.kf.service.IKfSessionService;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.AttributeKey;

/**
 * 单连接会话处理器：AUTH 前置校验、心跳、限流、踢线与生命周期管理
 * 
 * 每条连接独立实例，业务处理在所属 Netty worker 线程内串行执行，保证同一连接消息不乱序。
 * 
 * @author voxio
 */
public class ChatConnectionHandler extends SimpleChannelInboundHandler<ChatEnvelope>
{
    private static final Logger log = LoggerFactory.getLogger("kf.gateway");

    private static final AttributeKey<ChatPrincipal> PRINCIPAL = AttributeKey.valueOf("kf.principal");

    private static final AttributeKey<SimpleRateLimiter> LIMITER = AttributeKey.valueOf("kf.limiter");

    /** 下发给客户端的建议心跳间隔 */
    private static final int HEARTBEAT_SECONDS = 30;

    private final ConnectionRegistry registry;

    private final ChatAuthService authService;

    private final ChatMessageDispatcher dispatcher;

    private final GatewayMetrics metrics;

    private final IKfSessionService sessionService;

    private final ChatCryptoService cryptoService;

    private final int authTimeoutSeconds;

    private final int readerIdleSeconds;

    private final int agentRatePerMinute;

    private final int visitorRatePerMinute;

    public ChatConnectionHandler(ConnectionRegistry registry, ChatAuthService authService,
            ChatMessageDispatcher dispatcher, GatewayMetrics metrics, IKfSessionService sessionService,
            ChatCryptoService cryptoService,
            int authTimeoutSeconds, int readerIdleSeconds, int agentRatePerMinute, int visitorRatePerMinute)
    {
        this.registry = registry;
        this.authService = authService;
        this.dispatcher = dispatcher;
        this.metrics = metrics;
        this.sessionService = sessionService;
        this.cryptoService = cryptoService;
        this.authTimeoutSeconds = authTimeoutSeconds;
        this.readerIdleSeconds = readerIdleSeconds;
        this.agentRatePerMinute = agentRatePerMinute;
        this.visitorRatePerMinute = visitorRatePerMinute;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx)
    {
        metrics.onConnect();
        ctx.executor().schedule(() -> {
            if (principal(ctx) == null && ctx.channel().isActive())
            {
                log.warn("[网关鉴权] 连接 {} 未在 {}s 内完成鉴权，已断开", ctx.channel().id().asShortText(),
                        authTimeoutSeconds);
                sendError(ctx, null, ChatError.AUTH_FAILED, "鉴权超时");
                ctx.close();
            }
        }, authTimeoutSeconds, TimeUnit.SECONDS);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ChatEnvelope envelope)
    {
        String cmd = envelope.getCmd();
        if (ChatCmd.AUTH.equals(cmd))
        {
            handleAuth(ctx, envelope);
            return;
        }
        ChatPrincipal principal = principal(ctx);
        if (principal == null)
        {
            sendError(ctx, envelope, ChatError.AUTH_FAILED, "请先完成鉴权");
            ctx.close();
            return;
        }
        // 二次校验令牌：管理后台强制下线或退出登录后，已有连接必须立刻失效
        if (!authService.stillValid(principal))
        {
            sendError(ctx, envelope, ChatError.TOKEN_EXPIRED, "令牌已失效，请重新登录");
            ctx.close();
            return;
        }
        if (ChatCmd.KEY_EXCHANGE.equals(cmd))
        {
            handleKeyExchange(ctx, principal, envelope);
            return;
        }
        ChatCryptoService.Inbound inbound = cryptoService.unwrapInbound(ctx.channel(), envelope);
        if (inbound != ChatCryptoService.Inbound.OK)
        {
            rejectInbound(ctx, envelope, inbound);
            return;
        }
        if (ChatCmd.PING.equals(cmd))
        {
            handlePing(ctx, principal, envelope);
            return;
        }
        if (ChatCmd.KICKED.equals(cmd) || ChatCmd.ACK.equals(cmd) || ChatCmd.PONG.equals(cmd))
        {
            // 客户端误发服务端下行命令，忽略即可
            return;
        }
        if (!acquire(ctx, principal))
        {
            sendError(ctx, envelope, ChatError.RATE_LIMITED, "消息发送过快，请稍后再试");
            metrics.onRateLimited();
            return;
        }
        metrics.onMessageIn();
        if (ChatCmd.AGENT_STATUS.equals(cmd))
        {
            handleAgentStatus(ctx, principal, envelope);
            return;
        }
        dispatcher.dispatch(principal, envelope);
    }

    private void handleAuth(ChannelHandlerContext ctx, ChatEnvelope envelope)
    {
        if (principal(ctx) != null)
        {
            sendError(ctx, envelope, ChatError.DUPLICATE_CONNECTION, "该连接已鉴权");
            return;
        }
        String token = envelope.bodyText("token");
        ChatPrincipal principal = authService.resolve(token);
        if (principal == null)
        {
            metrics.onAuthFailure();
            sendError(ctx, envelope, ChatError.AUTH_FAILED, "令牌无效或已过期");
            ctx.close();
            return;
        }

        io.netty.channel.Channel old = registry.register(principal, ctx.channel());
        ctx.channel().attr(PRINCIPAL).set(principal);
        ctx.channel().attr(LIMITER).set(new SimpleRateLimiter(
                principal.isAgent() ? agentRatePerMinute : visitorRatePerMinute));
        // 令牌派生连接级 HMAC 密钥：鉴权之后的每一帧都带完整性摘要
        cryptoService.attach(ctx.channel(), token);
        if (old != null && old != ctx.channel())
        {
            // 同账号重复连接：踢掉旧连接
            ChatEnvelope kicked = new ChatEnvelope();
            kicked.setCmd(ChatCmd.KICKED);
            kicked.setTs(System.currentTimeMillis());
            kicked.getBody().put("reason", "同一账号在别处接入");
            old.writeAndFlush(sealOutbound(old, kicked));
            old.close();
            log.info("[网关连接] {} 重复接入，旧连接已踢下线", principal.key());
        }

        if (principal.isAgent())
        {
            sessionService.changeAgentOnlineStatus(principal.getId(), "1");
        }

        ChatEnvelope ok = new ChatEnvelope();
        ok.setCmd(ChatCmd.AUTH_OK);
        ok.setSeq(envelope.getSeq());
        ok.setTs(System.currentTimeMillis());
        ok.getBody().put("connId", UUID.randomUUID().toString().replace("-", ""));
        ok.getBody().put("principalType", principal.getType());
        ok.getBody().put("principalId", principal.getId());
        ok.getBody().put("name", principal.getName());
        ok.getBody().put("sessionId", principal.getSessionId());
        ok.getBody().put("serverTime", System.currentTimeMillis());
        ok.getBody().put("heartbeatSeconds", HEARTBEAT_SECONDS);
        ok.getBody().put("encryptEnabled", cryptoService.isEnabled());
        if (cryptoService.isEnabled())
        {
            // 客户端据此决定要不要做密钥协商；公钥与算法口径和 /kf/open/security 完全一致
            ok.getBody().put("hmacAlgo", "HMAC-SM3");
            ok.getBody().put("algo", cryptoService.algo());
            ok.getBody().put("keyVersion", cryptoService.serverKeyVersion());
            ok.getBody().put("sm2PublicKey", cryptoService.serverPublicKeyHex());
        }
        ctx.writeAndFlush(sealOutbound(ctx.channel(), ok));
        log.info("[网关连接] {} 接入成功，当前在线 {}", principal.key(), registry.activeCount());
    }

    private void handlePing(ChannelHandlerContext ctx, ChatPrincipal principal, ChatEnvelope envelope)
    {
        registry.touch(principal);
        ChatEnvelope pong = new ChatEnvelope();
        pong.setCmd(ChatCmd.PONG);
        pong.setSeq(envelope.getSeq());
        pong.setTs(System.currentTimeMillis());
        pong.getBody().put("serverTime", System.currentTimeMillis());
        ctx.writeAndFlush(sealOutbound(ctx.channel(), pong));
    }

    private void handleAgentStatus(ChannelHandlerContext ctx, ChatPrincipal principal, ChatEnvelope envelope)
    {
        if (!principal.isAgent())
        {
            sendError(ctx, envelope, ChatError.NO_PERMISSION, "仅客服可变更在线状态");
            return;
        }
        String onlineStatus = envelope.bodyText("onlineStatus");
        if (!"0".equals(onlineStatus) && !"1".equals(onlineStatus) && !"2".equals(onlineStatus))
        {
            sendError(ctx, envelope, ChatError.PROTOCOL_ERROR, "在线状态只能为 0/1/2");
            return;
        }
        sessionService.changeAgentOnlineStatus(principal.getId(), onlineStatus);
        ChatEnvelope ack = new ChatEnvelope();
        ack.setCmd(ChatCmd.ACK);
        ack.setSeq(envelope.getSeq());
        ack.setTs(System.currentTimeMillis());
        ack.getBody().put("action", "agentStatus");
        ack.getBody().put("onlineStatus", onlineStatus);
        ctx.writeAndFlush(sealOutbound(ctx.channel(), ack));
    }

    private boolean acquire(ChannelHandlerContext ctx, ChatPrincipal principal)
    {
        SimpleRateLimiter limiter = ctx.channel().attr(LIMITER).get();
        return limiter == null || limiter.tryAcquire();
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception
    {
        if (evt instanceof IdleStateEvent && ((IdleStateEvent) evt).state() == IdleState.READER_IDLE)
        {
            ChatPrincipal principal = principal(ctx);
            log.warn("[网关心跳] 连接 {} 超过 {}s 无读数据，已断开",
                    principal == null ? ctx.channel().id().asShortText() : principal.key(), readerIdleSeconds);
            ctx.close();
            return;
        }
        super.userEventTriggered(ctx, evt);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx)
    {
        ChatPrincipal principal = principal(ctx);
        if (principal == null)
        {
            return;
        }
        boolean removed = registry.unregister(principal, ctx.channel());
        if (!removed)
        {
            // 已被同账号的新连接替换，在线状态归新连接维护
            log.info("[网关连接] {} 旧连接关闭，已被新连接接管", principal.key());
            return;
        }
        if (principal.isAgent())
        {
            sessionService.changeAgentOnlineStatus(principal.getId(), "0");
        }
        log.info("[网关连接] {} 已断开，当前在线 {}", principal.key(), registry.activeCount());
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause)
    {
        metrics.onError();
        ChatPrincipal principal = principal(ctx);
        log.error("[网关异常] 连接 {} 处理失败：{}", principal == null ? ctx.channel().id().asShortText()
                : principal.key(), cause.getMessage());
        sendError(ctx, null, ChatError.PROTOCOL_ERROR, "服务端处理异常");
        ctx.close();
    }

    private ChatPrincipal principal(ChannelHandlerContext ctx)
    {
        return ctx.channel().attr(PRINCIPAL).get();
    }

    private void sendError(ChannelHandlerContext ctx, ChatEnvelope envelope, int code, String msg)
    {
        ChatEnvelope error = new ChatEnvelope();
        error.setCmd(ChatCmd.ERROR);
        error.setSeq(envelope == null ? null : envelope.getSeq());
        error.setSid(envelope == null ? null : envelope.getSid());
        // 回带被拒那条消息的 clientMsgId：两端都靠它把失败落到具体气泡上。不带的话，客户端只能等
        // 15 秒 ACK 超时把气泡翻成「发送超时」，客服看到的是网络问题，真实原因（超长/会话已结束）
        // 只在一条转瞬即逝的提示里，还会顺手点重发——那条注定再被拒一次。
        error.setMid(envelope == null ? null : envelope.getMid());
        error.setTs(System.currentTimeMillis());
        error.getBody().put("code", code);
        error.getBody().put("msg", msg);
        ctx.writeAndFlush(sealOutbound(ctx.channel(), error));
    }

    /**
     * 会话密钥协商：客户端用服务端 SM2 公钥加密 SM4 会话密钥上送
     *
     * 首写者建立会话密钥；若会话已有密钥（对端先协商），服务端用本次上送的临时公钥把既有密钥回传，
     * 两端最终收敛到同一把，客服与访客之间不需要任何第三方中转。
     */
    private void handleKeyExchange(ChannelHandlerContext ctx, ChatPrincipal principal, ChatEnvelope envelope)
    {
        ChatCryptoService.Inbound inbound = cryptoService.unwrapInbound(ctx.channel(), envelope);
        if (inbound != ChatCryptoService.Inbound.OK)
        {
            rejectInbound(ctx, envelope, inbound);
            return;
        }
        Long sessionId = envelope.getSid() == null ? envelope.bodyLong("sessionId") : envelope.getSid();
        if (sessionId == null)
        {
            sendError(ctx, envelope, ChatError.SESSION_INVALID, "密钥协商必须带会话ID");
            return;
        }
        if (!cryptoService.isEnabled())
        {
            sendError(ctx, envelope, ChatError.PROTOCOL_ERROR, "当前部署未启用加密，无需协商密钥");
            return;
        }
        ChatCryptoService.Negotiation negotiation = cryptoService.negotiate(sessionId,
                envelope.bodyText("cipher"), envelope.bodyText("clientPublicKey"));
        if (negotiation == null)
        {
            sendError(ctx, envelope, ChatError.CRYPTO_FAILED, "会话密钥协商失败，请重新连接");
            return;
        }
        cryptoService.establish(ctx.channel(), sessionId, negotiation.keyHex());

        ChatEnvelope ack = new ChatEnvelope();
        ack.setCmd(ChatCmd.ACK);
        ack.setSeq(envelope.getSeq());
        ack.setSid(sessionId);
        ack.setTs(System.currentTimeMillis());
        ack.getBody().put("action", "keyExchange");
        ack.getBody().put("sid", sessionId);
        ack.getBody().put("algo", cryptoService.algo());
        ack.getBody().put("keyVersion", negotiation.keyVersion());
        if (negotiation.keyCipher() != null)
        {
            // 会话密钥已由对端建立，本端改用回传的那把
            ack.getBody().put("cipher", negotiation.keyCipher());
        }
        ctx.writeAndFlush(sealOutbound(ctx.channel(), ack));
        log.info("[会话密钥] {} 会话 {} 密钥就绪（版本 {}，回传既有密钥 {}）",
                principal.key(), sessionId, negotiation.keyVersion(), negotiation.keyCipher() != null);
    }

    private void rejectInbound(ChannelHandlerContext ctx, ChatEnvelope envelope, ChatCryptoService.Inbound inbound)
    {
        metrics.onCryptoRejected();
        switch (inbound)
        {
            case HMAC_FAILED:
                sendError(ctx, envelope, ChatError.CRYPTO_FAILED, "帧完整性校验失败，本帧已丢弃");
                break;
            case KEY_MISSING:
                sendError(ctx, envelope, ChatError.KEY_REQUIRED, "请先完成会话密钥协商");
                break;
            case PLAINTEXT_REJECTED:
                sendError(ctx, envelope, ChatError.KEY_REQUIRED, "加密已启用，聊天正文必须密文传输");
                break;
            default:
                sendError(ctx, envelope, ChatError.CRYPTO_FAILED, "消息解密失败");
                break;
        }
    }

    /**
     * 本连接直接写出的帧补帧 hmac；走注册表的下行由注册表统一加密与签名
     *
     * 未鉴权连接（如旧连接被踢）没有加密上下文，原样发出。
     */
    private ChatEnvelope sealOutbound(Channel channel, ChatEnvelope envelope)
    {
        if (cryptoService.isEnabled() && cryptoService.context(channel) != null)
        {
            // 这些帧都不带正文（AUTH_OK/PONG/ACK/ERROR/KICKED），封包等价于补帧 hmac
            cryptoService.wrapOutbound(channel, envelope);
        }
        return envelope;
    }
}
