package com.jchen.test;

import com.jchen.rpc.api.HelloService;
import com.jchen.rpc.netty.server.NettyServer;
import com.jchen.rpc.registry.DefaultServiceRegistry;
import com.jchen.rpc.registry.ServiceRegistry;

/**
 * @Auther: jchen
 * @Date: 2021/03/17/11:40
 */
public class NettyTestServer {
    public static void main(String[] args) {
        HelloService helloService = new HelloServiceImpl();
        ServiceRegistry registry = new DefaultServiceRegistry();
        registry.register(helloService);
        NettyServer server = new NettyServer();
        server.start(9999);
    }
}
