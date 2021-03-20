package com.jchen.test;

import com.jchen.rpc.api.HelloObject;
import com.jchen.rpc.api.HelloService;
import com.jchen.rpc.RpcClientProxy;
import com.jchen.rpc.serializer.HessianSerializer;
import com.jchen.rpc.socket.client.SocketClient;

/**
 * 测试用消费者（客户端）
 *
 * @Auther: jchen
 * @Date: 2021/03/15/20:43
 */
public class SocketTestClient {

    public static void main(String[] args) {
        SocketClient client = new SocketClient("127.0.0.1", 9000);
        client.setSerializer(new HessianSerializer());
        RpcClientProxy proxy = new RpcClientProxy(client);
        HelloService helloService = proxy.getProxy(HelloService.class);
        HelloObject object = new HelloObject(12, "This is a message");
        String res = helloService.hello(object);
        System.out.println(res);
    }

}
