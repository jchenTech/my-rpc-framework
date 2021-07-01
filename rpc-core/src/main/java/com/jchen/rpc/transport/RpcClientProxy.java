package com.jchen.rpc.transport;

import com.jchen.rpc.entity.RpcRequest;
import com.jchen.rpc.entity.RpcResponse;
import com.jchen.rpc.transport.netty.client.NettyClient;
import com.jchen.rpc.transport.socket.client.SocketClient;
import com.jchen.rpc.util.RpcMessageChecker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * RPC客户端动态代理
 * 由于客户端只有接口，没有具体的实现类，所以没有办法生成实例对象调用方法，因此只能通过动态代理生成代理对象
 * 通过getProxy生成代理对象，当代理类调用接口方法时，执行invoke中的逻辑，向服务端发送请求，接收响应结果
 * @Auther: jchen
 * @Date: 2021/03/15/16:34
 */
public class RpcClientProxy implements InvocationHandler {

    private static final Logger logger = LoggerFactory.getLogger(RpcClientProxy.class);

    private final RpcClient client;

    public RpcClientProxy(RpcClient client) {
        this.client = client;
    }

    /**
     * 生成代理对象，通过传入的clazz类型，通过Proxy.newProxyInstance方法创建代理对象
     * @param clazz 需要代理的接口类型
     * @param <T> 代理类类型
     * @return
     */
    @SuppressWarnings("unchecked")
    public <T> T getProxy(Class<T> clazz) {
        return (T) Proxy.newProxyInstance(clazz.getClassLoader(), new Class<?>[]{clazz}, this);
    }

    /**
     * 继承InvocationHandler接口需要重写invoke方法，方法内是当代理类调用接口的方法时执行的逻辑
     * 1.生成RpcRequest请求对象，包含请求号，接口名，方法名，参数值，参数类型，是否是心跳包
     * 2.选择Netty传输或Socket传输，向服务端发送请求
     * 3.接受服务端的响应结果
     * @param proxy
     * @param method
     * @param args
     * @return 服务端响应数据
     */
    @SuppressWarnings("unchecked")
    @Override
    public Object invoke(Object proxy, Method method, Object[] args) {
        logger.info("调用方法: {}#{}", method.getDeclaringClass().getName(), method.getName());
        //生成request对象，包含请求号，接口名，方法名，参数值，参数类型，是否是心跳包
        RpcRequest rpcRequest = new RpcRequest(UUID.randomUUID().toString(), method.getDeclaringClass().getName(),
                method.getName(), args, method.getParameterTypes(), false);
        RpcResponse rpcResponse = null;
        //1.当客户端通过Netty传输时
        if (client instanceof NettyClient) {
            try {
                //接受服务端响应结果
                CompletableFuture<RpcResponse> completableFuture = (CompletableFuture<RpcResponse>) client.sendRequest(rpcRequest);
                rpcResponse = completableFuture.get();
            } catch (InterruptedException | ExecutionException e) {
                logger.error("方法调用请求发送失败", e);
                return null;
            }
        }
        //2.当客户端通过socket传输时
        if (client instanceof SocketClient) {
            rpcResponse = (RpcResponse) client.sendRequest(rpcRequest);
        }
        //通过requestId检查响应与请求是否匹配
        RpcMessageChecker.check(rpcRequest, rpcResponse);
        return rpcResponse.getData();
    }
}