package com.jchen.test;

import com.jchen.rpc.api.HelloService;
import com.jchen.rpc.registry.DefaultServiceRegistry;
import com.jchen.rpc.registry.ServiceRegistry;
import com.jchen.rpc.socket.server.SocketServer;

/**
 * 测试用服务提供方（服务端）
 *
 * @Auther: jchen
 * @Date: 2021/03/15/20:42
 */
public class SocketTestServer {
    public static void main(String[] args) {
        HelloService helloService = new HelloServiceImpl();
        ServiceRegistry serviceRegistry = new DefaultServiceRegistry();
        serviceRegistry.register(helloService);
        SocketServer socketServer = new SocketServer(serviceRegistry);
        socketServer.start(9000);
    }
}
