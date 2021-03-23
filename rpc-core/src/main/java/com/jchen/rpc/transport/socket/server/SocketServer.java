package com.jchen.rpc.transport.socket.server;

import com.jchen.rpc.enumeration.RpcError;
import com.jchen.rpc.exception.RpcException;
import com.jchen.rpc.hook.ShutdownHook;
import com.jchen.rpc.provider.ServiceProvider;
import com.jchen.rpc.provider.ServiceProviderImpl;
import com.jchen.rpc.registry.NacosServiceRegistry;
import com.jchen.rpc.transport.RpcServer;
import com.jchen.rpc.registry.ServiceRegistry;
import com.jchen.rpc.handler.RequestHandler;
import com.jchen.rpc.serializer.CommonSerializer;
import com.jchen.rpc.util.ThreadPoolFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
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


    private final ExecutorService threadPool;;
    private final String host;
    private final int port;
    private RequestHandler requestHandler = new RequestHandler();

    private final CommonSerializer serializer;


    private final ServiceRegistry serviceRegistry;
    private final ServiceProvider serviceProvider;

    public SocketServer(String host, int port) {
        this(host, port, DEFAULT_SERIALIZER);
    }

    public SocketServer(String host, int port, Integer serializer) {
        this.host = host;
        this.port = port;
        threadPool = ThreadPoolFactory.createDefaultThreadPool("socket-rpc-server");
        this.serviceRegistry = new NacosServiceRegistry();
        this.serviceProvider = new ServiceProviderImpl();
        this.serializer = CommonSerializer.getByCode(serializer);
    }

    @Override
    public <T> void publishService(T service, Class<T> serviceClass) {
        if(serializer == null) {
            logger.error("未设置序列化器");
            throw new RpcException(RpcError.SERIALIZER_NOT_FOUND);
        }
        serviceProvider.addServiceProvider(service, serviceClass);
        serviceRegistry.register(serviceClass.getCanonicalName(), new InetSocketAddress(host, port));
        start();
    }

    //启动服务端
    @Override
    public void start() {
        try (ServerSocket serverSocket = new ServerSocket()) {
            serverSocket.bind(new InetSocketAddress(host, port));
            logger.info("服务器启动……");
            ShutdownHook.getShutdownHook().addClearAllHook();
            Socket socket;
            while((socket = serverSocket.accept()) != null) {
                logger.info("消费者连接: {}:{}", socket.getInetAddress(), socket.getPort());
                threadPool.execute(new SocketRequestHandlerThread(socket, requestHandler, serializer));
            }
            threadPool.shutdown();
        } catch (IOException e) {
            logger.error("服务器启动时有错误发生:", e);
        }
    }
}
