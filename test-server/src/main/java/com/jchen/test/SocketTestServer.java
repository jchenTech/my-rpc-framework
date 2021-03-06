package com.jchen.test;

import com.jchen.rpc.annotation.ServiceScan;
import com.jchen.rpc.api.HelloService;
import com.jchen.rpc.provider.ServiceProviderImpl;
import com.jchen.rpc.registry.ServiceRegistry;
import com.jchen.rpc.serializer.CommonSerializer;
import com.jchen.rpc.serializer.HessianSerializer;
import com.jchen.rpc.transport.RpcServer;
import com.jchen.rpc.transport.socket.server.SocketServer;

/**
 * 测试用服务提供方（服务端）
 *
 * @Auther: jchen
 * @Date: 2021/03/15/20:42
 */
@ServiceScan
public class SocketTestServer {
    public static void main(String[] args) {
        RpcServer server = new SocketServer("127.0.0.1", 9998, CommonSerializer.HESSIAN_SERIALIZER);
        server.start();
    }
}
