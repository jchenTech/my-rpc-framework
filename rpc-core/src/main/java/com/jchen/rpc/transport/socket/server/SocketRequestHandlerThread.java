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
        try (InputStream inputStream = socket.getInputStream();//这种写法可以在执行完后自动关闭流，不需要手动关闭
             OutputStream outputStream = socket.getOutputStream();) {
            //读取rpcRequest对象
             RpcRequest rpcRequest = (RpcRequest) ObjectReader.readObject(inputStream);
             //通过requestHandler通过反射调用方法执行，返回执行结果
             Object result = requestHandler.handle(rpcRequest);
             //将执行结果封装到RpcResponse对象中，写入输出流，供客户端读取
             RpcResponse<Object> response = RpcResponse.success(result, rpcRequest.getRequestId());
             ObjectWriter.writeObject(outputStream, response, serializer);
        } catch (IOException e) {
            logger.error("调用或发送时有错误发生：", e);
        }
    }

}
