package com.jchen.test;

import com.jchen.rpc.api.HelloService;
import com.jchen.rpc.server.RpcServer;

/**
 * 测试用服务提供方（服务端）
 *
 * @Auther: jchen
 * @Date: 2021/03/15/20:42
 */
public class TestServer {
    public static void main(String[] args) {
        HelloService helloService = new HelloServiceImpl();
        RpcServer rpcServer = new RpcServer();
        rpcServer.register(helloService, 9000);
    }
}
