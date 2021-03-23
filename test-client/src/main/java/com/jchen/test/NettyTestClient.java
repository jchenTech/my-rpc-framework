package com.jchen.test;

import com.jchen.rpc.serializer.CommonSerializer;
import com.jchen.rpc.transport.RpcClient;
import com.jchen.rpc.api.HelloObject;
import com.jchen.rpc.api.HelloService;
import com.jchen.rpc.transport.RpcClientProxy;
import com.jchen.rpc.transport.netty.client.NettyClient;
import com.jchen.rpc.serializer.ProtobufSerializer;

/**
 * 测试用Netty消费者
 *
 * @Auther: jchen
 * @Date: 2021/03/17/11:41
 */
public class NettyTestClient {
    public static void main(String[] args) {
        RpcClient client = new NettyClient(CommonSerializer.PROTOBUF_SERIALIZER);
        RpcClientProxy rpcClientProxy = new RpcClientProxy(client);
        HelloService helloService = rpcClientProxy.getProxy(HelloService.class);
        HelloObject object = new HelloObject(12, "This is a message");
        String res = helloService.hello(object);
        System.out.println(res);
    }
}
