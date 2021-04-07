这一节我们来实现一个Netty连接失败重试机制，话不多说，我们直接来看如何实现。

## Netty连接失败重试机制

首先定义一个连接失败Error：

```java
CLIENT_CONNECT_SERVER_FAILURE("客户端连接服务端失败");
```

然后我们编写一个`ChannelProvider` 类，用于提供Channel：

```java
public class ChannelProvider {
    private static final Logger logger = LoggerFactory.getLogger(ChannelProvider.class);
    private static EventLoopGroup eventLoopGroup;
    private static Bootstrap bootstrap = initializeBootstrap();

    private static final int MAX_RETRY_COUNT = 5;
    private static Channel channel = null;

    public static Channel get(InetSocketAddress inetSocketAddress, CommonSerializer serializer) {
        bootstrap.handler(new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel ch) {
                /*自定义序列化编解码器*/
                // RpcResponse -> ByteBuf
                ch.pipeline().addLast(new CommonEncoder(serializer))
                        .addLast(new CommonDecoder())
                        .addLast(new NettyClientHandler());
            }
        });
        CountDownLatch countDownLatch = new CountDownLatch(1);
        try {
            connect(bootstrap, inetSocketAddress, countDownLatch);
            countDownLatch.await();
        } catch (InterruptedException e) {
            logger.error("获取channel时有错误发生:", e);
        }
        return channel;
    }

    private static void connect(Bootstrap bootstrap, InetSocketAddress inetSocketAddress, CountDownLatch countDownLatch) {
        connect(bootstrap, inetSocketAddress, MAX_RETRY_COUNT, countDownLatch);
    }

    private static void connect(Bootstrap bootstrap, InetSocketAddress inetSocketAddress, int retry, CountDownLatch countDownLatch) {
        bootstrap.connect(inetSocketAddress).addListener((ChannelFutureListener) future -> {
            if (future.isSuccess()) {
                logger.info("客户端连接成功!");
                channel = future.channel();
                countDownLatch.countDown();
                return;
            }
            if (retry == 0) {
                logger.error("客户端连接失败:重试次数已用完，放弃连接！");
                countDownLatch.countDown();
                throw new RpcException(RpcError.CLIENT_CONNECT_SERVER_FAILURE);
            }
            // 第几次重连
            int order = (MAX_RETRY_COUNT - retry) + 1;
            // 本次重连的间隔
            int delay = 1 << order;
            logger.error("{}: 连接失败，第 {} 次重连……", new Date(), order);
            bootstrap.config().group().schedule(() -> connect(bootstrap, inetSocketAddress,
                    retry - 1, countDownLatch), delay, TimeUnit.SECONDS);
        });
    }

    private static Bootstrap initializeBootstrap() {
        eventLoopGroup = new NioEventLoopGroup();
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(eventLoopGroup)
                .channel(NioSocketChannel.class)
                //连接的超时时间，超过这个时间还是建立不上的话则代表连接失败
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
                //是否开启 TCP 底层心跳机制
                .option(ChannelOption.SO_KEEPALIVE, true)
                //TCP默认开启了 Nagle 算法，该算法的作用是尽可能的发送大数据快，减少网络传输。TCP_NODELAY 参数的作用就是控制是否启用 Nagle 算法。
                .option(ChannelOption.TCP_NODELAY, true);
        return bootstrap;
    }
}
```

实现的Netty连接失败重试机制中，先把bootstrap初始化，然后尝试进行连接，通过一个ChannelFutureListener监听器对连接情况进行监听。设置一个最大尝试连接次数变量`MAX_RETRY_COUNT`，并用order变量记录重连次数。在此过程中通过countDownLatch来保证线程安全，使一个线程等待其他线程各自执行完毕后再执行。我们将count初始值设置为1，只有当连接成功或者重试达到最大次数失败时，计数器 -1 即为0后，其他线程才能再执行。`countDownLatch.await();`即为只有当当前线程的计数器为0时，程序才能继续执行。

接下来我们修改NettyClient类，由于我们已经通过上面的ChannelProvider提供了Netty的Channel，因此我们不用再在NettyClient类中对BootStrap进行初始化，直接将请求对象写入channel中，这里主要修改sendRequest方法，相应的代码为：

```java
@Override
public Object sendRequest(RpcRequest rpcRequest) {
    if (serializer == null) {
        logger.error("未设置序列化器");
        throw new RpcException(RpcError.SERIALIZER_NOT_FOUND);
    }
    //AtomicReference可以保证在修改对象引用时的线程安全性
    //多个线程试图改变同一个AtomicReference(例如比较和交换操作)将不会使得AtomicReference处于不一致的状态。
    AtomicReference<Object> result = new AtomicReference<>(null);
    try {
        Channel channel = ChannelProvider.get(new InetSocketAddress(host, port), serializer);
        if (channel.isActive()) {
            channel.writeAndFlush(rpcRequest).addListener(future1 -> {
                if (future1.isSuccess()) {
                    logger.info(String.format("客户端发送消息: %s", rpcRequest.toString()));
                } else {
                    logger.error("发送消息时有错误发生: ", future1.cause());
                }
            });
            channel.closeFuture().sync();
            AttributeKey<RpcResponse> key = AttributeKey.valueOf("rpcResponse" + rpcRequest.getRequestId());
            RpcResponse rpcResponse = channel.attr(key).get();
            RpcMessageChecker.check(rpcRequest, rpcResponse);
            result.set(rpcResponse.getData());
        } else {
            System.exit(0);
        }
    } catch (InterruptedException e) {
        logger.error("发送消息时有错误发生: ", e);
    }
    return result.get();
}
```

我们使用了`AtomicReference`对象，可以保证在修改对象引用时的线程安全性，即多个线程试图改变同一个`AtomicReference`(例如比较和交换操作)将不会使得`AtomicReference`处于不一致的状态。

## Socket与Netty相互通信

最后我们再来改变一下Socket方式传输数据的端序，使得其能够与Netty进行通信：

ObjectWriter：

```java
private static byte[] intToByte(int value) {
    byte[] src = new byte[4];
    src[0] = (byte) ((value>>24) & 0xFF);
    src[1] = (byte) ((value>>16)& 0xFF);
    src[2] = (byte) ((value>>8)&0xFF);
    src[3] = (byte) (value & 0xFF);
    return src;
}
```

ObjectReader：

```java
public static int bytesToInt(byte[] src) {
    int value;
    value = ((src[0] & 0xFF)<<24)
        |((src[1] & 0xFF)<<16)
        |((src[2] & 0xFF)<<8)
        |(src[3] & 0xFF);
    return value;
}
```

测试：

现在我们先来测试连接失败重试机制，我们将Netty连接的服务端地址设置为非本机地址，然后尝试连接，此时：

```java
[nioEventLoopGroup-3-1] ERROR com.jchen.rpc.netty.client.ChannelProvider - Sun Mar 21 17:06:02 CST 2021: 连接失败，第 1 次重连……
[nioEventLoopGroup-3-3] ERROR com.jchen.rpc.netty.client.ChannelProvider - Sun Mar 21 17:06:09 CST 2021: 连接失败，第 2 次重连……
[nioEventLoopGroup-3-5] ERROR com.jchen.rpc.netty.client.ChannelProvider - Sun Mar 21 17:06:18 CST 2021: 连接失败，第 3 次重连……
[nioEventLoopGroup-3-7] ERROR com.jchen.rpc.netty.client.ChannelProvider - Sun Mar 21 17:06:31 CST 2021: 连接失败，第 4 次重连……
[nioEventLoopGroup-3-9] ERROR com.jchen.rpc.netty.client.ChannelProvider - Sun Mar 21 17:06:52 CST 2021: 连接失败，第 5 次重连……
[nioEventLoopGroup-3-11] ERROR com.jchen.rpc.netty.client.ChannelProvider - 客户端连接失败:重试次数已用完，放弃连接！
```

现在把服务器ip设置正常后测试Socket与Netty通信，现在我们将Netty和Socket的端口设为一致，然后启动SocketTestServer以及NettyTestClient，此时两者是可以相互通信的，可以进行方法调用。