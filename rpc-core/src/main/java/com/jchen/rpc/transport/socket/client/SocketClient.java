package com.jchen.rpc.transport.socket.client;

import com.jchen.rpc.registry.NacosServiceDiscovery;
import com.jchen.rpc.registry.NacosServiceRegistry;
import com.jchen.rpc.registry.ServiceDiscovery;
import com.jchen.rpc.registry.ServiceRegistry;
import com.jchen.rpc.transport.RpcClient;
import com.jchen.rpc.entity.RpcRequest;
import com.jchen.rpc.entity.RpcResponse;
import com.jchen.rpc.enumeration.ResponseCode;
import com.jchen.rpc.enumeration.RpcError;
import com.jchen.rpc.exception.RpcException;
import com.jchen.rpc.serializer.CommonSerializer;
import com.jchen.rpc.transport.socket.util.ObjectReader;
import com.jchen.rpc.transport.socket.util.ObjectWriter;
import com.jchen.rpc.util.RpcMessageChecker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * Socket方式远程方法调用的消费者（客户端）
 *
 * @Auther: jchen
 * @Date: 2021/03/15/18:19
 */
public class SocketClient implements RpcClient {
    private static final Logger logger = LoggerFactory.getLogger(SocketClient.class);

    private final ServiceDiscovery serviceDiscovery;
    private CommonSerializer serializer;

    public SocketClient() {
        this.serviceDiscovery = new NacosServiceDiscovery();
    }

    @Override
    public Object sendRequest(RpcRequest rpcRequest) {
        if(serializer == null) {
            logger.error("未设置序列化器");
            throw new RpcException(RpcError.SERIALIZER_NOT_FOUND);
        }
        InetSocketAddress inetSocketAddress = serviceDiscovery.lookupService(rpcRequest.getInterfaceName());
        try (Socket socket = new Socket()) {
            socket.connect(inetSocketAddress);
            OutputStream outputStream = socket.getOutputStream();
            InputStream inputStream = socket.getInputStream();
            ObjectWriter.writeObject(outputStream, rpcRequest, serializer);
            Object obj = ObjectReader.readObject(inputStream);
            RpcResponse rpcResponse = (RpcResponse) obj;
            if (rpcResponse == null) {
                logger.error("服务调用失败，service：{}", rpcRequest.getInterfaceName());
                throw new RpcException(RpcError.SERVICE_INVOCATION_FAILURE, " service:" + rpcRequest.getInterfaceName());
            }
            if (rpcResponse.getStatusCode() == null || rpcResponse.getStatusCode() != ResponseCode.SUCCESS.getCode()) {
                logger.error("调用服务失败, service: {}, response:{}", rpcRequest.getInterfaceName(), rpcResponse);
                throw new RpcException(RpcError.SERVICE_INVOCATION_FAILURE, " service:" + rpcRequest.getInterfaceName());
            }
            RpcMessageChecker.check(rpcRequest, rpcResponse);
            return rpcResponse.getData();
        } catch (IOException e) {
            logger.error("调用时有错误发生：", e);
            throw new RpcException("服务调用失败: ", e);
        }
    }

    @Override
    public void setSerializer(CommonSerializer serializer) {
        this.serializer = serializer;
    }

}
