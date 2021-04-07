前面我们进行传输的时候是采用最简单的BIO方法，这一节我们会将传输换成效率更高的 NIO 方式，当然不会使用 Java 原生的 NIO，而是采用更为简单的 Netty。本节还会实现一个通用的序列化接口，为多种序列化支持做准备，并且，本节还会自定义传输的协议。

## Netty 服务端与客户端

首先就需要在 `pom.xml` 中加入 Netty 依赖：

```xml
<dependency>
    <groupId>io.netty</groupId>
    <artifactId>netty-all</artifactId>
	<version>${netty-version}</version>
</dependency>
```

netty 的最新版本可以在 mavenrepository查到，注意使用 netty 4 而不是 netty 5。

为了保证通用性，我们可以把 Server 和 Client 抽象成两个接口，分别是 RpcServer 和 RpcClient，这样我们以后就可以通过不同方式实现RPC，如Socket，Netty等：

```java
public interface RpcServer {
    void start(int port);
}

public interface RpcClient {
    Object sendRequest(RpcRequest rpcRequest);
}
```

而原来的 RpcServer 和 RpcClient 类实际上是上述两个接口的 Socket 方式实现类，改成 SocketServer 和 SocketClient 并实现上面两个接口即可，几乎不需要做什么修改。

我们的任务，就是要实现 NettyServer 和 NettyClient。

这里提一个改动，就是在 `DefaultServiceRegistry.java` 中，将包含注册信息的 serviceMap 和 registeredService 都改成了 **`static`** ，这样就能保证全局唯一的注册信息，保证每个对象的注册信息相同，并且在创建 RpcServer 时也就不需要传入了。

```java
//<服务名，服务对象>
private static final Map<String, Object> serviceMap =  new ConcurrentHashMap<>();
private static final Set<String> registeredService  = ConcurrentHashMap.newKeySet();
```

那么我们现在来实现一个 NettyServer：

```java
public class NettyServer implements RpcServer {

    private static final Logger logger = LoggerFactory.getLogger(NettyServer.class);

    @Override
    public void start(int port) {
        //用于处理客户端新连接的主”线程池“
        EventLoopGroup bossGroup = new NioEventLoopGroup();
        //用于连接后处理IO事件的从”线程池“
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        try {
			//初始化Netty服务端启动器，作为服务端入口
            ServerBootstrap serverBootstrap = new ServerBootstrap();
            //将主从“线程池”初始化到启动器中
            serverBootstrap.group(bossGroup, workerGroup)
                	//设置服务端通道类型
                    .channel(NioServerSocketChannel.class)
                	//日志打印方式
                    .handler(new LoggingHandler(LogLevel.INFO))
                    //当服务器请求处理线程全满时，用于临时存放已完成三次握手的请求的队列的最大长度，如果队列已满，客户端连接将被拒绝
                    .option(ChannelOption.SO_BACKLOG, 256)
                    //启用心跳保活机制,在双方TCP套接字建立连接后并且在两个小时左右上层没有任何数据传输的情况下，机制被激活。
                    .option(ChannelOption.SO_KEEPALIVE, true)
                    //Nagle算法会尽可能发送大块数据，避免网络中充斥着许多小数据块。TCP_NODELAY为true关闭Nagle，保证实时性，有数据就发送
                    .childOption(ChannelOption.TCP_NODELAY, true)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) throws Exception {
                            ChannelPipeline pipeline = ch.pipeline();
                            pipeline.addLast(new CommonEncoder(new JsonSerializer()));
                            pipeline.addLast(new CommonDecoder());
                            pipeline.addLast(new NettyServerHandler());
                        }
                    });
            //绑定端口，启动Netty，sync()代表阻塞主Server线程，以执行Netty线程，如果不阻塞Netty就直接被下面shutdown了
            ChannelFuture future = serverBootstrap.bind(port).sync();
            //等确定通道关闭了，关闭future回到主Server线程
            future.channel().closeFuture().sync();

        } catch (InterruptedException e) {
            logger.error("启动服务器时有错误发生: ", e);
        } finally {
            //优雅关闭Netty服务端且清理掉内存
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }

}
```

了解过 Netty 的同学可能知道，Netty 中有一个很重要的设计模式——责任链模式，责任链上有多个处理器，每个处理器都会对数据进行加工，并将处理后的数据传给下一个处理器。代码中的 CommonEncoder、CommonDecoder和NettyServerHandler 分别就是编码器，解码器和数据处理器。因为数据从外部传入时需要解码，而传出时需要编码，类似计算机网络的分层模型，每一层向下层传递数据时都要加上该层的信息，而向上层传递时则需要对本层信息进行解码。

```java
public class NettyClient implements RpcClient {

    private static final Logger logger = LoggerFactory.getLogger(NettyClient.class);

    private String host;
    private int port;
    private static final Bootstrap bootstrap;

    public NettyClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    static {
        EventLoopGroup group = new NioEventLoopGroup();
        bootstrap = new Bootstrap();
        bootstrap.group(group)
                .channel(SocketChannel.class)
                .option(ChannelOption.SO_KEEPALIVE, true)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ChannelPipeline pipeline = ch.pipeline();
                        pipeline.addLast(new CommonEncoder());
                        pipeline.addLast(new CommonDecoder());
                        pipeline.addLast(new NettyClientHandler());
                    }
                });
    }

    @Override
    public Object sendRequest(RpcRequest rpcRequest) {
        try {
            ChannelFuture future = bootstrap.connect(host, port).sync();
            Channel channel = future.channel();
            if (channel != null) {
                channel.writeAndFlush(rpcRequest).addListener(future1 -> {
                    if (future1.isSuccess()) {
                        logger.info((String.format("客户端发送消息： %s", rpcRequest.toString())));
                    }else {
                        logger.error("发送消息时有错误发生：", future1.cause());
                    }
                });
                channel.closeFuture().sync();
                //AttributeMap<AttributeKey, AttributeValue>是绑定在Channel上的，可以设置用来获取通道对象
                AttributeKey<RpcResponse> key = AttributeKey.valueOf("rpcResponse");
                //get()阻塞获取value
                RpcResponse rpcResponse = channel.attr(key).get();
                return rpcResponse.getData();
            }
        } catch (InterruptedException e) {
            logger.error("发送消息时有错误发生：", e);
        }
        return null;
    }
}

```

在静态代码块中就直接配置好了 Netty 客户端，等待发送数据时启动，channel 将 RpcRequest 对象写出，并且等待服务端返回的结果。注意这里的发送是非阻塞的，所以发送后会立刻返回，而无法得到结果。这里通过 `AttributeKey` 的方式阻塞获得返回结果：

```java
AttributeKey<RpcResponse> key = AttributeKey.valueOf("rpcResponse");
RpcResponse rpcResponse = channel.attr(key).get();
```

通过这种方式获得全局可见的返回结果，在获得返回结果 `RpcResponse` 后，将这个对象以 key 为 `rpcResponse` 放入 `ChannelHandlerContext` 中，这里就可以立刻获得结果并返回，我们会在 `NettyClientHandler` 中看到放入的过程。

## 自定义协议与编解码器

在传输过程中，我们可以在发送的数据上加上各种必要的数据，形成自定义的协议，而自动加上这个数据就是编码器的工作，解析数据获得原始数据就是解码器的工作。

我们定义的协议包含：

- `Magic Number`：4 字节魔数，表识一个协议包
- `Package Type`：标明这是一个调用请求还是调用响应
- `Serializer Type`：标明了实际数据使用的序列化器，这个服务端和客户端应当使用统一标准
- `Data Length`：就是实际数据的长度，设置这个字段主要防止**粘包**
- `Data Bytes`：经过序列化后的实际数据，可能是 RpcRequest 也可能是 RpcResponse 经过序列化后的字节，取决于 Package Type

```
+---------------+---------------+-----------------+-------------+
|  Magic Number |  Package Type | Serializer Type | Data Length |
|    4 bytes    |    4 bytes    |     4 bytes     |   4 bytes   |
+---------------+---------------+-----------------+-------------+
|                          Data Bytes                           |
|                   Length: ${Data Length}                      |
+---------------------------------------------------------------+
```

规定好协议后，我们就可以来看看 `CommonEncoder` 了：

```java
public class CommonEncoder extends MessageToByteEncoder {

    private static final int MAGIC_NUMBER = 0xCAFEBABE;

    private final CommonSerializer serializer;

    public CommonEncoder(CommonSerializer serializer) {
        this.serializer = serializer;
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, Object msg, ByteBuf out) throws Exception {
        //1.写入协议包标识
        out.writeInt(MAGIC_NUMBER);
        //2.写入请求还是响应请求
        if(msg instanceof RpcRequest) {
            out.writeInt(PackageType.REQUEST_PACK.getCode());
        } else {
            out.writeInt(PackageType.RESPONSE_PACK.getCode());
        }
        //3.写入序列化器标识
        out.writeInt(serializer.getCode());
        //4.写入序列化后的数据长度
        byte[] bytes = serializer.serialize(msg);
        out.writeInt(bytes.length);
        //4.写入序列化后的数据
        out.writeBytes(bytes);
    }

}
```

`CommonEncoder` 继承了`MessageToByteEncoder` 类(实现的是`ChannelOutBoundHandler`接口)，见名知义，就是把 Message（实际要发送的对象）转化成 Byte 数组。`CommonEncoder` 的工作很简单，就是把 `RpcRequest` 或者 `RpcResponse` 包装成协议包。 根据上面提到的协议格式，将各个字段写到管道里就可以了，这里`serializer.getCode()` 获取序列化器的编号，之后使用传入的序列化器将请求或响应包序列化为字节数组写入管道即可。

而 `CommonDecoder` 的工作就更简单了：

```java
public class CommonDecoder extends ReplayingDecoder {

    private static final Logger logger = LoggerFactory.getLogger(CommonDecoder.class);
    private static final int MAGIC_NUMBER = 0xCAFEBABE;

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        int magic = in.readInt();
        if(magic != MAGIC_NUMBER) {
            logger.error("不识别的协议包: {}", magic);
            throw new RpcException(RpcError.UNKNOWN_PROTOCOL);
        }
        int packageCode = in.readInt();
        Class<?> packageClass;
        if(packageCode == PackageType.REQUEST_PACK.getCode()) {
            packageClass = RpcRequest.class;
        } else if(packageCode == PackageType.RESPONSE_PACK.getCode()) {
            packageClass = RpcResponse.class;
        } else {
            logger.error("不识别的数据包: {}", packageCode);
            throw new RpcException(RpcError.UNKNOWN_PACKAGE_TYPE);
        }
        int serializerCode = in.readInt();
        CommonSerializer serializer = CommonSerializer.getByCode(serializerCode);
        if(serializer == null) {
            logger.error("不识别的反序列化器: {}", serializerCode);
            throw new RpcException(RpcError.UNKNOWN_SERIALIZER);
        }
        int length = in.readInt();
        byte[] bytes = new byte[length];
        in.readBytes(bytes);
        Object obj = serializer.deserialize(bytes, packageClass);
        out.add(obj);
    }

}
```

主要就是四个方法，序列化，反序列化，获得该序列化器的编号，已经根据编号获取序列化器，这里先简单通过一个 JSON 序列化器进行序列化和反序列化，Kryo 序列化器会在后面讲解：

```java
public class JsonSerializer implements CommonSerializer {

    private static final Logger logger = LoggerFactory.getLogger(JsonSerializer.class);

    private ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public byte[] serialize(Object obj) {
        try {
            return objectMapper.writeValueAsBytes(obj);
        } catch (JsonProcessingException e) {
            logger.error("序列化时有错误发生: {}", e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public Object deserialize(byte[] bytes, Class<?> clazz) {
        try {
            Object obj = objectMapper.readValue(bytes, clazz);
            if(obj instanceof RpcRequest) {
                obj = handleRequest(obj);
            }
            return obj;
        } catch (IOException e) {
            logger.error("反序列化时有错误发生: {}", e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /*
        这里由于使用JSON序列化和反序列化Object数组，无法保证反序列化后仍然为原实例类型
        需要重新判断处理
     */
    private Object handleRequest(Object obj) throws IOException {
        RpcRequest rpcRequest = (RpcRequest) obj;
        for(int i = 0; i < rpcRequest.getParamTypes().length; i ++) {
            Class<?> clazz = rpcRequest.getParamTypes()[i];
            if(!clazz.isAssignableFrom(rpcRequest.getParameters()[i].getClass())) {
                byte[] bytes = objectMapper.writeValueAsBytes(rpcRequest.getParameters()[i]);
                rpcRequest.getParameters()[i] = objectMapper.readValue(bytes, clazz);
            }
        }
        return rpcRequest;
    }

    @Override
    public int getCode() {
        return SerializerCode.valueOf("JSON").getCode();
    }

}
```

JSON 序列化工具我使用的是 `Jackson`，在 pom.xml 中添加依赖即可。序列化和反序列化都比较循规蹈矩，把对象翻译成字节数组，和根据字节数组和 Class 反序列化成对象。这里有一个需要注意的点，就是在 `RpcRequest` 反序列化时，由于其中有一个字段是 Object 数组，在反序列化时序列化器会根据字段类型进行反序列化，而 Object 就是一个十分模糊的类型，会出现反序列化失败的现象，这时就需要 `RpcRequest` 中的另一个字段 `ParamTypes` 来获取到 Object 数组中的每个实例的实际类，辅助反序列化，这就是 `handleRequest()` 方法的作用。

上面提到的这种情况不会在其他序列化方式中出现，因为其他序列化方式是转换成字节数组，会记录对象的信息，而 JSON 方式本质上只是转换成 JSON 字符串，会丢失对象的类型信息。

## NettyServerHandler 和 NettyClientHandler

NettyServerHandler 和 NettyClientHandler 都分别位于服务器端和客户端责任链的尾部，直接和 RpcServer 对象或 RpcClient 对象打交道，而无需关心字节序列的情况。

NettyServerhandler 用于接收 RpcRequest，并且执行调用，将调用结果返回封装成 RpcResponse 发送出去。

```java
public class NettyServerHandler extends SimpleChannelInboundHandler<RpcRequest> {

    private static final Logger logger = LoggerFactory.getLogger(NettyServerHandler.class);
    private static RequestHandler requestHandler;
    private static ServiceRegistry serviceRegistry;

    static {
        requestHandler = new RequestHandler();
        serviceRegistry = new DefaultServiceRegistry();
    }

    /**
     * 接受RpcRequest对象，并获得实现类方法后执行，将结果发送出去
     */
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, RpcRequest msg) throws Exception {
        try {
            logger.info("服务器接收到请求: {}", msg);
            String interfaceName = msg.getInterfaceName();
            Object service = serviceRegistry.getService(interfaceName);
            Object result = requestHandler.handle(msg, service);
            ChannelFuture future = ctx.writeAndFlush(RpcResponse.success(result));
            //添加一个监听器到channelfuture来检测是否所有的数据包都发出，然后关闭通道
            future.addListener(ChannelFutureListener.CLOSE);
        } finally {
            ReferenceCountUtil.release(msg);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.error("处理过程调用时有错误发生:");
        cause.printStackTrace();
        ctx.close();
    }

}
```

NettyClientHandler：

```java
public class NettyClientHandler extends SimpleChannelInboundHandler<RpcResponse> {

    private static final Logger logger = LoggerFactory.getLogger(NettyClientHandler.class);

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, RpcResponse msg) throws Exception {
        try {
            logger.info(String.format("客户端接收到消息: %s", msg));
            AttributeKey<RpcResponse> key = AttributeKey.valueOf("rpcResponse");
            ctx.channel().attr(key).set(msg);
            ctx.channel().close();
        } finally {
            ReferenceCountUtil.release(msg);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.error("过程调用时有错误发生:");
        cause.printStackTrace();
        ctx.close();
    }
}
```

这里只需要处理收到的消息，即 RpcResponse 对象，由于前面已经有解码器解码了，这里就直接将返回的结果放入 ctx 中即可。

## 测试

NettyTestServer 如下：

```java
public class NettyTestServer {

    public static void main(String[] args) {
        HelloService helloService = new HelloServiceImpl();
        ServiceRegistry registry = new DefaultServiceRegistry();
        registry.register(helloService);
        NettyServer server = new NettyServer();
        server.start(9999);
    }

}
```

NettyTestClient如下：

```java
public class NettyTestClient {

    public static void main(String[] args) {
        RpcClient client = new NettyClient("127.0.0.1", 9999);
        RpcClientProxy rpcClientProxy = new RpcClientProxy(client);
        HelloService helloService = rpcClientProxy.getProxy(HelloService.class);
        HelloObject object = new HelloObject(12, "This is a message");
        String res = helloService.hello(object);
        System.out.println(res);
    }

}
```

执行得到结果与之前相同。注意这里 `rpcClientProxy` 通过传入不同的Client（`SocketClient`、`NettyClient`）来切换客户端不同的传输方式。

