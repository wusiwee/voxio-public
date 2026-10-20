package com.voxio.chat.codec;

import java.nio.charset.StandardCharsets;

import com.voxio.chat.protocol.ChatEnvelope;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import io.netty.handler.codec.MessageToMessageDecoder;

/**
 * TCP 帧编解码
 * 
 * 帧格式：int32(大端) 报文长度 + UTF-8 JSON 报文。
 * 
 * @author voxio
 */
public final class TcpCodec
{
    private TcpCodec()
    {
    }

    /**
     * 解码：ByteBuf -> ChatEnvelope
     */
    public static class Decoder extends MessageToMessageDecoder<ByteBuf>
    {
        @Override
        protected void decode(ChannelHandlerContext ctx, ByteBuf buf, java.util.List<Object> out)
        {
            byte[] bytes = new byte[buf.readableBytes()];
            buf.readBytes(bytes);
            ChatEnvelope envelope = EnvelopeCodec.parse(new String(bytes, StandardCharsets.UTF_8));
            if (envelope != null)
            {
                out.add(envelope);
            }
            else
            {
                ctx.close();
            }
        }
    }

    /**
     * 编码：ChatEnvelope -> 长度前缀 + JSON
     */
    public static class Encoder extends MessageToByteEncoder<ChatEnvelope>
    {
        @Override
        protected void encode(ChannelHandlerContext ctx, ChatEnvelope msg, ByteBuf out)
        {
            byte[] payload = EnvelopeCodec.toBytes(msg);
            out.writeInt(payload.length);
            out.writeBytes(payload);
        }
    }
}
