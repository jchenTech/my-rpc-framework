package com.jchen.test;

import com.jchen.rpc.api.HelloService;
import com.jchen.rpc.provider.ServiceProviderImpl;
import com.jchen.rpc.registry.ServiceRegistry;
import com.jchen.rpc.serializer.HessianSerializer;
import com.jchen.rpc.transport.socket.server.SocketServer;

/**
 * 测试用服务提供方（服务端）
 *
 * @Auther: jchen
 * @Date: 2021/03/15/20:42
 */
public class SocketTestServer {
    public static void main(String[] args) {
        HelloService helloService = new HelloServiceImpl();
        SocketServer socketServer = new SocketServer("127.0.0.1", 9998);
        socketServer.setSerializer(new HessianSerializer());
        socketServer.publishService(helloService, HelloService.class);
    }
}
