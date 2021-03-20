package com.jchen.test;

import com.jchen.rpc.RpcClient;
import com.jchen.rpc.api.HelloObject;
import com.jchen.rpc.api.HelloService;
import com.jchen.rpc.RpcClientProxy;
import com.jchen.rpc.netty.client.NettyClient;
import com.jchen.rpc.serializer.HessianSerializer;

/**
 * 测试用Netty消费者
 *
 * @Auther: jchen
 * @Date: 2021/03/17/11:41
 */
public class NettyTestClient {
    public static void main(String[] args) {
        RpcClient client = new NettyClient("127.0.0.1", 9999);
        client.setSerializer(new HessianSerializer());
        RpcClientProxy rpcClientProxy = new RpcClientProxy(client);
        HelloService helloService = rpcClientProxy.getProxy(HelloService.class);
        HelloObject object = new HelloObject(12, "This is a message");
        String res = helloService.hello(object);
        System.out.println(res);
    }
}
