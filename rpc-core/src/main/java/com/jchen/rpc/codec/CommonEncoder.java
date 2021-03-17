package com.jchen.rpc.codec;

import com.jchen.rpc.entity.RpcRequest;
import com.jchen.rpc.enumeration.PackageType;
import com.jchen.rpc.serializer.CommonSerializer;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import io.netty.util.concurrent.EventExecutorGroup;

/**
 * 通用的编码拦截器
 *
 * @Auther: jchen
 * @Date: 2021/03/17/10:12
 */
public class CommonEncoder extends MessageToByteEncoder {

    private static final int MAGIC_NUMBER = 0xCAFEBABE;

    private final CommonSerializer serializer;

    public CommonEncoder(CommonSerializer serializer) {
        this.serializer = serializer;
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, Object msg, ByteBuf out) throws Exception {
        //1.写入协议包标识
        out.writeInt(MAGIC_NUMBER);
        //2.写入请求还是响应请求
        if(msg instanceof RpcRequest) {
            out.writeInt(PackageType.REQUEST_PACK.getCode());
        } else {
            out.writeInt(PackageType.RESPONSE_PACK.getCode());
        }
        //3.写入序列化器标识
        out.writeInt(serializer.getCode());
        //4.写入序列化后的数据长度
        byte[] bytes = serializer.serialize(msg);
        out.writeInt(bytes.length);
        //4.写入序列化后的数据
        out.writeBytes(bytes);
    }

}