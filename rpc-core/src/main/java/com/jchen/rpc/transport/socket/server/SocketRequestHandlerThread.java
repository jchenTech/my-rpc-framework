package com.jchen.rpc.transport.socket.server;

import com.jchen.rpc.entity.RpcRequest;
import com.jchen.rpc.entity.RpcResponse;
import com.jchen.rpc.registry.ServiceRegistry;
import com.jchen.rpc.handler.RequestHandler;
import com.jchen.rpc.serializer.CommonSerializer;
import com.jchen.rpc.transport.socket.util.ObjectReader;
import com.jchen.rpc.transport.socket.util.ObjectWriter;
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
public class SocketRequestHandlerThread implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(SocketRequestHandlerThread.class);

    private Socket socket;
    private RequestHandler requestHandler;
    private CommonSerializer serializer;

    public SocketRequestHandlerThread(Socket socket, RequestHandler requestHandler, CommonSerializer serializer) {
        this.socket = socket;
        this.requestHandler = requestHandler;
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
             Object result = requestHandler.handle(rpcRequest);
             RpcResponse<Object> response = RpcResponse.success(result, rpcRequest.getRequestId());
             ObjectWriter.writeObject(outputStream, response, serializer);
        } catch (IOException e) {
            logger.error("调用或发送时有错误发生：", e);
        }
    }

}
