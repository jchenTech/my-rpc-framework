package com.jchen.rpc.socket.server;

import com.jchen.rpc.entity.RpcRequest;
import com.jchen.rpc.entity.RpcResponse;
import com.jchen.rpc.registry.ServiceRegistry;
import com.jchen.rpc.RequestHandler;
import com.jchen.rpc.serializer.CommonSerializer;
import com.jchen.rpc.socket.util.ObjectReader;
import com.jchen.rpc.socket.util.ObjectWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

/**
 * 处理RpcRequest的工作线程，从输入流中获取rpcRequest对象，并获取对应服务，将服务执行后得到的RpcResponse对象写入输出流
 *
 * @Auther: jchen
 * @Date: 2021/03/16/19:26
 */
public class RequestHandlerThread implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(RequestHandlerThread.class);

    private Socket socket;
    private RequestHandler requestHandler;
    private ServiceRegistry serviceRegistry;
    private CommonSerializer serializer;

    public RequestHandlerThread(Socket socket, RequestHandler requestHandler,
                                ServiceRegistry serviceRegistry, CommonSerializer serializer) {
        this.socket = socket;
        this.requestHandler = requestHandler;
        this.serviceRegistry = serviceRegistry;
        this.serializer = serializer;
    }

    /**
     * 处理线程，从输入流中获取rpcRequest对象，并获取对应服务，将服务执行后得到的RpcResponse对象写入输出流
     */
    @Override
    public void run() {
        try (InputStream inputStream = socket.getInputStream();
             OutputStream outputStream = socket.getOutputStream();) {
             RpcRequest rpcRequest = (RpcRequest) ObjectReader.readObject(inputStream);

             //从RpcRequest对象中获取接口名，通过serviceRegistry获取服务（即实现类）
             String interfaceName = rpcRequest.getInterfaceName();
             Object service = serviceRegistry.getService(interfaceName);

             //调用requestHandler获得执行结果，得到RpcResponse对象写入输出流
             Object result = requestHandler.handle(rpcRequest, service);

             RpcResponse<Object> response = RpcResponse.success(result);
             ObjectWriter.writeObject(outputStream, response, serializer);
        } catch (IOException e) {
            logger.error("调用或发送时有错误发生：", e);
        }
    }

}
