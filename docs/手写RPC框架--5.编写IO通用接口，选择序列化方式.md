前面我们完成了多种序列化器的实现，每次在Client和Server类中需要选择序列化器时需要在源码中修改，这肯定是不符合面向对象的思想的。因此我们需要设计一个通用接口，从而在创建Client对象和Server对象时能够自由完成对序列化器的选择操作，我们需要对Netty方式和Socket传输方式采用通用的操作接口，因此本节是对之前的代码的一些重构。

## 编写IO通用接口，选择序列化方式

首先我们在`RpcClient`接口和`RpcServer`接口中，创建一个`setSerializer`方法，用于在创建Client和Server对象时，根据自己的需要设置序列化器。

```java
public interface RpcClient {
    Object sendRequest(RpcRequest rpcRequest);

    void setSerializer(CommonSerializer serializer);
}

public interface RpcServer {
    void start(int port);
    
    void setSerializer(CommonSerializer serializer);
}
```

必然的我们需要在这两个接口的实现类中重写这个方法：

```java
@Override
public void setSerializer(CommonSerializer serializer) {
    this.serializer = serializer;
}
```

基于Netty的方式修改很简单，我们以NettyClient为例：

```java
public class NettyClient implements RpcClient {

    private static final Logger logger = LoggerFactory.getLogger(NettyClient.class);

    private static final Bootstrap bootstrap;
    private CommonSerializer serializer;

    static {
        EventLoopGroup group = new NioEventLoopGroup();
        bootstrap = new Bootstrap();
        bootstrap.group(group)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.SO_KEEPALIVE, true);
    }

    private String host;
    private int port;

    public NettyClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    @Override
    public Object sendRequest(RpcRequest rpcRequest) {
        if (serializer == null) {
            logger.error("未设置序列化器");
            throw new RpcException(RpcError.SERIALIZER_NOT_FOUND);
        }
        bootstrap.handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        ChannelPipeline pipeline = ch.pipeline();
                        pipeline.addLast(new CommonDecoder())
                                .addLast(new CommonEncoder(serializer))
                                .addLast(new NettyClientHandler());
                    }
                });
        try {
            ChannelFuture future = bootstrap.connect(host, port).sync();
            logger.info("客户端连接到服务器 {}:{}", host, port);
            Channel channel = future.channel();
            if(channel != null) {
                channel.writeAndFlush(rpcRequest).addListener(future1 -> {
                    if(future1.isSuccess()) {
                        logger.info(String.format("客户端发送消息: %s", rpcRequest.toString()));
                    } else {
                        logger.error("发送消息时有错误发生: ", future1.cause());
                    }
                });
                channel.closeFuture().sync();
                AttributeKey<RpcResponse> key = AttributeKey.valueOf("rpcResponse");
                RpcResponse rpcResponse = channel.attr(key).get();
                return rpcResponse.getData();
            }

        } catch (InterruptedException e) {
            logger.error("发送消息时有错误发生: ", e);
        }
        return null;
    }

    @Override
    public void setSerializer(CommonSerializer serializer) {
        this.serializer = serializer;
    }

}
```

基于Socket的传输方式，我们前面使用Socket进行传输时，采用的就是默认的IO序列化方式，用输入输出流来序列化和反序列化。该方法与我们之前实现的一些序列化方法如：Json，Kryo，Hessian等相比，序列化后的二进制字节流较大，不利于网络传输。我们的Netty可以将序列化和反序列化的handler添加到pipeline中。而Socket方式我们需要写一个`ObjectWriter`类和`ObjectReader`类来将输入输出流中的对象根据跟定的序列化器进行序列化和反序列化操作：

```java
Socket socket = new Socket(host, port);
OutputStream outputStream = socket.getOutputStream();
InputStream inputStream = socket.getInputStream();
ObjectWriter.writeObject(outputStream, rpcRequest, serializer);
Object obj = ObjectReader.readObject(inputStream);
```

ObjectWriter：

```java
public class ObjectWriter {
    private static final Logger logger = LoggerFactory.getLogger(ObjectWriter.class);
    private static final int MAGIC_NUMBER = 0xCAFEBABE;

    public static void writeObject(OutputStream outputStream, Object object, CommonSerializer serializer) throws IOException {
        outputStream.write(intToByte(MAGIC_NUMBER));
        if (object instanceof RpcRequest) {
            outputStream.write(intToByte(PackageType.REQUEST_PACK.getCode()));
        }else {
            outputStream.write(intToByte(PackageType.RESPONSE_PACK.getCode()));
        }
        outputStream.write(intToByte(serializer.getCode()));
        byte[] bytes = serializer.serialize(object);
        outputStream.write(intToByte(bytes.length));
        outputStream.write(bytes);
        outputStream.flush();
    }

    private static byte[] intToByte(int value) {
        byte[] des = new byte[4];
        des[3] = (byte) ((value >> 24) & 0xFF);
        des[2] = (byte) ((value >> 16) & 0xFF);
        des[1] = (byte) ((value >> 8) & 0xFF);
        des[0] = (byte) (value & 0xFF);
        return des;
    }
}
```

ObjectReader：

```java
public class ObjectReader {
    private static final Logger logger = LoggerFactory.getLogger(ObjectReader.class);
    private static final int MAGIC_NUMBER = 0xCAFEBABE;

    public static Object readObject(InputStream in) throws IOException {
        byte[] numberBytes = new byte[4];
        in.read(numberBytes);
        int magic = bytesToInt(numberBytes);
        if (magic != MAGIC_NUMBER) {
            logger.error("不识别的协议包: {}", magic);
            throw new RpcException(RpcError.UNKNOWN_PROTOCOL);
        }
        in.read(numberBytes);
        int packageCode = bytesToInt(numberBytes);
        Class<?> packageClass;
        if (packageCode == PackageType.REQUEST_PACK.getCode()) {
            packageClass = RpcRequest.class;
        } else if (packageCode == PackageType.RESPONSE_PACK.getCode()) {
            packageClass = RpcResponse.class;
        } else {
            logger.error("不识别的数据包: {}", packageCode);
            throw new RpcException(RpcError.UNKNOWN_PACKAGE_TYPE);
        }
        in.read(numberBytes);
        int serializerCode = bytesToInt(numberBytes);
        CommonSerializer serializer = CommonSerializer.getByCode(serializerCode);
        if (serializer == null) {
            logger.error("不识别的反序列化器: {}", serializerCode);
            throw new RpcException(RpcError.UNKNOWN_SERIALIZER);
        }
        in.read(numberBytes);
        int length = bytesToInt(numberBytes);
        byte[] bytes = new byte[length];
        in.read(bytes);
        return serializer.deserialize(bytes, packageClass);
    }

    public static int bytesToInt(byte[] src) {
        int value;
        value = (src[0] & 0xFF)
                | ((src[1] & 0xFF)<<8)
                | ((src[2] & 0xFF)<<16)
                | ((src[3] & 0xFF)<<24);
        return value;
    }

}
```

## 测试

我们以Socket方式为例，测试以通用接口设置序列化器进行远程方法调用：

```java
public class SocketTestClient {

    public static void main(String[] args) {
        SocketClient client = new SocketClient("127.0.0.1", 9000);
        client.setSerializer(new HessianSerializer());
        RpcClientProxy proxy = new RpcClientProxy(client);
        HelloService helloService = proxy.getProxy(HelloService.class);
        HelloObject object = new HelloObject(12, "This is a message");
        String res = helloService.hello(object);
        System.out.println(res);
    }

}

public class SocketTestServer {
    
    public static void main(String[] args) {
        HelloService helloService = new HelloServiceImpl();
        ServiceRegistry serviceRegistry = new DefaultServiceRegistry();
        serviceRegistry.register(helloService);
        SocketServer socketServer = new SocketServer(serviceRegistry);
        socketServer.setSerializer(new HessianSerializer());
        socketServer.start(9000);
    }
}

```

返回结果正常：

```java
//SocketTestClient：
这是调用的返回值，id=12
//SocketTestServer:
[main] INFO com.jchen.rpc.registry.DefaultServiceRegistry - 向接口: [interface com.jchen.rpc.api.HelloService] 注册服务: com.jchen.test.HelloServiceImpl
[main] INFO com.jchen.rpc.socket.server.SocketServer - 服务器启动……
[main] INFO com.jchen.rpc.socket.server.SocketServer - 消费者连接: /127.0.0.1:57566
[pool-1-thread-1] INFO com.jchen.test.HelloServiceImpl - 接收到：This is a message
[pool-1-thread-1] INFO com.jchen.rpc.RequestHandler - 服务:com.jchen.rpc.api.HelloService 成功调用方法:hello

```

