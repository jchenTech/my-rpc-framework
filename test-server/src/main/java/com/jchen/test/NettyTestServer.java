package com.jchen.test;

import com.jchen.rpc.annotation.ServiceScan;
import com.jchen.rpc.api.HelloService;
import com.jchen.rpc.serializer.CommonSerializer;
import com.jchen.rpc.transport.RpcServer;
import com.jchen.rpc.transport.netty.server.NettyServer;
import com.jchen.rpc.provider.ServiceProviderImpl;
import com.jchen.rpc.registry.ServiceRegistry;
import com.jchen.rpc.serializer.ProtobufSerializer;

/**
 * @Auther: jchen
 * @Date: 2021/03/17/11:40
 */
@ServiceScan
public class NettyTestServer {
    public static void main(String[] args) {
        RpcServer server = new NettyServer("127.0.0.1", 9999, CommonSerializer.PROTOBUF_SERIALIZER);
        server.start();
    }
}
