package com.voxio.chat.codec;

import java.util.List;

import com.voxio.chat.protocol.ChatEnvelope;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;
import io.netty.handler.codec.MessageToMessageEncoder;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;

/**
 * WebSocket 文本帧编解码（payload 与 TCP 完全一致的 JSON 信封）
 * 
 * @author voxio
 */
public final class WebSocketCodec
{
    private WebSocketCodec()
    {
    }

    /**
     * 解码：TextWebSocketFrame -> ChatEnvelope
     */
    public static class Decoder extends MessageToMessageDecoder<TextWebSocketFrame>
    {
        @Override
        protected void decode(ChannelHandlerContext ctx, TextWebSocketFrame frame, List<Object> out)
        {
            ChatEnvelope envelope = EnvelopeCodec.parse(frame.text());
            if (envelope != null)
            {
                out.add(envelope);
            }
        }
    }

    /**
     * 编码：ChatEnvelope -> TextWebSocketFrame
     */
    public static class Encoder extends MessageToMessageEncoder<ChatEnvelope>
    {
        @Override
        protected void encode(ChannelHandlerContext ctx, ChatEnvelope msg, List<Object> out)
        {
            out.add(new TextWebSocketFrame(EnvelopeCodec.toJson(msg)));
        }
    }
}
