package com.jchen.rpc.socket.server;

import com.jchen.rpc.RpcServer;
import com.jchen.rpc.registry.ServiceRegistry;
import com.jchen.rpc.RequestHandler;
import com.jchen.rpc.serializer.CommonSerializer;
import com.jchen.rpc.util.ThreadPoolFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.*;

/**
 * Socket方式远程方法调用的提供者（服务端）
 *
 * @Auther: jchen
 * @Date: 2021/03/15/20:06
 */
public class SocketServer implements RpcServer {
    private static final Logger logger = LoggerFactory.getLogger(SocketServer.class);


    private final ExecutorService threadPool;
    private RequestHandler requestHandler = new RequestHandler();
    private final ServiceRegistry serviceRegistry;
    private CommonSerializer serializer;

    //创建线程池，传入一个已经注册好服务的ServiceRegistry
    public SocketServer(ServiceRegistry serviceRegistry) {
        this.serviceRegistry = serviceRegistry;
        threadPool = ThreadPoolFactory.createDefaultThreadPool("socket-rpc-server");
    }

    //启动服务端
    @Override
    public void start(int port) {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            logger.info("服务器启动……");
            Socket socket;
            while((socket = serverSocket.accept()) != null) {
                logger.info("消费者连接: {}:{}", socket.getInetAddress(), socket.getPort());
                threadPool.execute(new RequestHandlerThread(socket, requestHandler, serviceRegistry, serializer));
            }
            threadPool.shutdown();
        } catch (IOException e) {
            logger.error("服务器启动时有错误发生:", e);
        }
    }

    @Override
    public void setSerializer(CommonSerializer serializer) {
        this.serializer = serializer;
    }
}
