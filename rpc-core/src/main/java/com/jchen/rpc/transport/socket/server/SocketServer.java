package com.jchen.rpc.transport.socket.server;

import com.jchen.rpc.enumeration.RpcError;
import com.jchen.rpc.exception.RpcException;
import com.jchen.rpc.hook.ShutdownHook;
import com.jchen.rpc.provider.ServiceProvider;
import com.jchen.rpc.provider.ServiceProviderImpl;
import com.jchen.rpc.registry.NacosServiceRegistry;
import com.jchen.rpc.transport.AbstractRpcServer;
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
public class SocketServer extends AbstractRpcServer {

    private final ExecutorService threadPool;
    private final CommonSerializer serializer;
    private RequestHandler requestHandler = new RequestHandler();

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
        scanServices();
    }

    //启动服务端
    @Override
    public void start() {
        try (ServerSocket serverSocket = new ServerSocket()) {
            serverSocket.bind(new InetSocketAddress(host, port));
            logger.info("服务器启动……");
            //添加自动注销服务的钩子，在服务端关闭时，将自动注销服务
            ShutdownHook.getShutdownHook().addClearAllHook();
            Socket socket;
            //监听到消费者连接
            while((socket = serverSocket.accept()) != null) {
                logger.info("消费者连接: {}:{}", socket.getInetAddress(), socket.getPort());
                //创建工作线程，处理rpcRequest对象，获取对应服务，将执行结果写入rpcResponse写入输出流中供客户端读取
                threadPool.execute(new SocketRequestHandlerThread(socket, requestHandler, serializer));
            }
            threadPool.shutdown();
        } catch (IOException e) {
            logger.error("服务器启动时有错误发生:", e);
        }
    }
}
