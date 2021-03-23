package com.jchen.rpc.transport.netty.server;

import com.jchen.rpc.hook.ShutdownHook;
import com.jchen.rpc.provider.ServiceProvider;
import com.jchen.rpc.provider.ServiceProviderImpl;
import com.jchen.rpc.registry.NacosServiceRegistry;
import com.jchen.rpc.registry.ServiceRegistry;
import com.jchen.rpc.transport.AbstractRpcServer;
import com.jchen.rpc.transport.RpcServer;
import com.jchen.rpc.codec.CommonDecoder;
import com.jchen.rpc.codec.CommonEncoder;
import com.jchen.rpc.enumeration.RpcError;
import com.jchen.rpc.exception.RpcException;
import com.jchen.rpc.serializer.CommonSerializer;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.timeout.IdleStateHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

/**
 * NIO方式服务提供者
 *
 * @Auther: jchen
 * @Date: 2021/03/17/10:01
 */
public class NettyServer extends AbstractRpcServer {

    private final CommonSerializer serializer;

    public NettyServer(String host, int port) {
        this(host, port, DEFAULT_SERIALIZER);
    }

    public NettyServer(String host, int port, Integer serializer) {
        this.host = host;
        this.port = port;
        serviceRegistry = new NacosServiceRegistry();
        serviceProvider = new ServiceProviderImpl();
        this.serializer = CommonSerializer.getByCode(serializer);
        scanServices();
    }


    @Override
    public void start() {
        ShutdownHook.getShutdownHook().addClearAllHook();
        EventLoopGroup bossGroup = new NioEventLoopGroup();
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        try {
            ServerBootstrap serverBootstrap = new ServerBootstrap();
            serverBootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .handler(new LoggingHandler(LogLevel.INFO))
                    //当服务器请求处理线程全满时，用于临时存放已完成三次握手的请求的队列的最大长度
                    .option(ChannelOption.SO_BACKLOG, 256)
                    //启用心跳保活机制,在双方TCP套接字建立连接后并且在两个小时左右上层没有任何数据传输的情况下，机制被激活。
                    .option(ChannelOption.SO_KEEPALIVE, true)
                    //Nagle算法会尽可能发送大块数据，避免网络中充斥着许多小数据块。TCP_NODELAY为true关闭Nagle，保证实时性，有数据就发送
                    .childOption(ChannelOption.TCP_NODELAY, true)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) throws Exception {
                            ChannelPipeline pipeline = ch.pipeline();
                            pipeline.addLast(new IdleStateHandler(30, 0, 0, TimeUnit.SECONDS))
                            .addLast(new CommonEncoder(serializer))
                            .addLast(new CommonDecoder())
                            .addLast(new NettyServerHandler());
                        }
                    });
            ChannelFuture future = serverBootstrap.bind(host, port).sync();
            future.channel().closeFuture().sync();

        } catch (InterruptedException e) {
            logger.error("启动服务器时有错误发生: ", e);
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }
}