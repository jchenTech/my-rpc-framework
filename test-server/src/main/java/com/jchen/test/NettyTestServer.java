package com.jchen.test;

import com.jchen.rpc.api.HelloService;
import com.jchen.rpc.transport.netty.server.NettyServer;
import com.jchen.rpc.provider.ServiceProviderImpl;
import com.jchen.rpc.registry.ServiceRegistry;
import com.jchen.rpc.serializer.ProtobufSerializer;

/**
 * @Auther: jchen
 * @Date: 2021/03/17/11:40
 */
public class NettyTestServer {
    public static void main(String[] args) {
        HelloService helloService = new HelloServiceImpl();
        NettyServer server = new NettyServer("127.0.0.1", 9999);
        server.setSerializer(new ProtobufSerializer());
        server.publishService(helloService, HelloService.class);
    }
}
