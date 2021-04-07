## 自动服务注销

上一节我们实现了服务的自动注册和发现，但是我们可能会发现，如果你启动完成服务端后把服务端给关闭了，并不会自动地注销 Nacos 中对应的服务信息，这样就导致了当客户端再次向 Nacos 请求服务时，会获取到已经关闭的服务端信息，最终就有可能因为连接不到服务器而调用失败。

那么我们就需要一种办法，在服务端关闭之前自动向 Nacos 注销服务。但是有一个问题，我们不知道什么时候服务器会关闭，也就不知道这个方法调用的时机，就没有办法手工去调用。这时，我们就需要钩子。

钩子是什么呢？是在某些事件发生后自动去调用的方法。那么我们只需要把注销服务的方法写到关闭系统的钩子方法里就行了。

首先先写向 Nacos 注销所有服务的方法，这部分被放在了 NacosUtils 中作为一个静态方法，NacosUtils 是一个 Nacos 相关的工具类：

```java
public static void clearRegistry() {
        if (!serviceNames.isEmpty() && address != null) {
            String host = address.getHostName();
            int port = address.getPort();
            Iterator<String> iterator = serviceNames.iterator();
            while (iterator.hasNext()) {
                String serviceName = iterator.next();
                try {
                    namingService.deregisterInstance(serviceName, host, port);
                } catch (NacosException e) {
                    logger.error("注销服务{}失败", serviceName, e);
                }
            }
        }
    }
}
```

所有的服务名称都被存储在 `NacosUtils` 类中的 `serviceNames` 中，在注销时只需要用迭代器迭代所有服务名，调用 `deregisterInstance` 即可。

接着就是钩子了，新建一个类，`ShutdownHook`：

```java
public class ShutdownHook {

    private static final Logger logger = LoggerFactory.getLogger(ShutdownHook.class);

    private final ExecutorService threadPool = ThreadPoolFactory.createDefaultThreadPool("shutdown-hook");
    private static final ShutdownHook shutdownHook = new ShutdownHook();

    public static ShutdownHook getShutdownHook() {
        return shutdownHook;
    }

    public void addClearAllHook() {
        logger.info("关闭后将自动注销所有服务");
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            NacosUtil.clearRegistry();
            threadPool.shutdown();
        }));
    }
}
```

使用了单例模式创建其对象，在 `addClearAllHook` 中，Runtime 对象是 JVM 虚拟机的运行时环境，调用其 `addShutdownHook` 方法增加一个钩子函数，创建一个新线程调用 `clearRegistry` 方法完成注销工作。这个钩子函数会在 JVM 关闭之前被调用。

这样在 RpcServer 启动之前，只需要调用 `addClearAllHook`，就可以注册这个钩子了。例如在 NettyServer 中：

```java
  ChannelFuture future = serverBootstrap.bind(host, port).sync();
+ ShutdownHook.getShutdownHook().addClearAllHook();
  future.channel().closeFuture().sync();
```

启动服务端后再关闭，就会发现 Nacos 中的注册信息都被注销了。



## 心跳检查机制

Netty对心跳机制有两个层面实现，第一个是TCP层面，之前给通道初始化设置的值 `.option(ChannelOption.SO_KEEPALIVE, true)` 就是TCP的心跳机制，第二个层面是通过通道中的处理IdleStateHandler来实现，可以自定义心跳检测间隔时长，以及具体检测的逻辑实现。



实现一个简单的心跳检查机制，在RpcRequest中添加心跳包属性：

```java
//是否是心跳包
private Boolean heartBeat;
```

然后在NettyClient和NettyServer中向管道中添加`IdleStateHandler`：

```java
.addLast(new IdleStateHandler(0, 5, 0, TimeUnit.SECONDS))
```

实例化一个 IdleStateHandler 需要提供三个参数:

- `readerIdleTimeSeconds`读超时：即当在指定的时间间隔内没有从 Channel 读取到数据时, 会触发一个 READER_IDLE 的 IdleStateEvent 事件
- `writerIdleTimeSeconds`写超时：即当在指定的时间间隔内没有数据写入到 Channel 时, 会触发一个 WRITER_IDLE 的 IdleStateEvent 事件
- `allIdleTimeSeconds`读/写超时：即当在指定的时间间隔内没有读或写操作时, 会触发一个 ALL_IDLE 的 IdleStateEvent 事件

`IdleStateHandler` 是实现心跳的关键, 它会根据不同的 IO idle 类型来产生不同的 `IdleStateEvent` 事件，而这个事件的捕获，其实就是在 `userEventTriggered` 方法中实现的。

我们来看看`NettyClientHandler.userEventTriggered` 的具体实现：

```java
@Override
public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
    if (evt instanceof IdleStateEvent) {
        IdleState state = ((IdleStateEvent) evt).state();
        if (state == IdleState.WRITER_IDLE) {
            logger.info("发送心跳包 [{}]", ctx.channel().remoteAddress());
            Channel channel = ChannelProvider.get((InetSocketAddress) ctx.channel().remoteAddress(),
                                                  CommonSerializer.getByCode(CommonSerializer.DEFAULT_SERIALIZER));
            RpcRequest rpcRequest = new RpcRequest();
            rpcRequest.setHeartBeat(true);
            channel.writeAndFlush(rpcRequest).addListener(ChannelFutureListener.CLOSE_ON_FAILURE);
        }
    } else {
        super.userEventTriggered(ctx, evt);
    }
}
```

在`userEventTriggered`中根据IdleStateEvent中的state的不同，进行不同的处理，这里，当state为写数据时 `if (state == IdleState.WRITER_IDLE)` 向服务器发送RpcRequest对象。



然后是服务端的实现，在NettyServer类中向管道注册Handler，`.addLast(new IdleStateHandler(30, 0, 0, TimeUnit.SECONDS))`，即设定IdleStateHandler心跳检测每30秒进行一次读检测，如果30秒内ChannelRead()方法未被调用则触发一次userEventTrigger()方法：

```java
@Override
public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
    if (evt instanceof IdleStateEvent) {
        IdleState state = ((IdleStateEvent) evt).state();
        if (state == IdleState.READER_IDLE) {
            logger.info("长时间未收到心跳包，断开连接...");
            ctx.close();
        }
    } else {
        super.userEventTriggered(ctx, evt);
    }
}
```

对于客户端心跳包发来心跳包在channelRead0()中判断，只要服务端收到心跳包，客户端就能监听到，因此不需要额外处理：

```java
if(msg.getHeartBeat()){
    logger.info("接收到客户端心跳包……");
    return;
}
```

但心跳机制暂时不会奏效，因为设置的客户端完成一个调用后立刻关闭连接，接下来会对此进行调整。



## 统一管理Netty客户端请求

我们利用CompletableFuture来异步获取Netty请求的响应结果，将每个请求对应的CompletableFuture实例都保存在一个Map中，key为请求ID，value为创建的CompletableFuture实例，核心代码如下：

```java
public class UnprocessedRequests {

    private static ConcurrentHashMap<String, CompletableFuture<RpcResponse>> unprocessedResponseFutures = new ConcurrentHashMap<>();

    public void put(String requestId, CompletableFuture<RpcResponse> future) {
        unprocessedResponseFutures.put(requestId, future);
    }

    public void remove(String requestId) {
        unprocessedResponseFutures.remove(requestId);
    }

    public void complete(RpcResponse rpcResponse) {
        CompletableFuture<RpcResponse> future = unprocessedResponseFutures.remove(rpcResponse.getRequestId());
        if (null != future) {
            future.complete(rpcResponse);
        } else {
            throw new IllegalStateException();
        }
    }

}
```

这次启动Netty服务端和客户端就会发现，调用执行结束后，控制台会打印出心跳检测的记录信息。



## 负载均衡策略

负载均衡大家应该都熟悉，在上一节中客户端在 `lookupService` 方法中，从 Nacos 获取到的是所有提供这个服务的服务端信息列表，我们就需要从中选择一个，这便涉及到客户端侧的负载均衡策略。我们新建一个接口：`LoadBalancer`：

```java
public interface  LoadBalancer {

    Instance select(List<Instance> instances);

}
```

实现随机算法：

```java
public class RandomLoadBalancer implements LoadBalancer {

    @Override
    public Instance select(List<Instance> instances) {
        return instances.get(new Random().nextInt(instances.size()));
    }

}
```

实现轮询算法，按照顺序依次选择第一个、第二个、第三个……

```java
public class RoundRobinLoadBalancer implements LoadBalancer {

    private int index = 0;

    @Override
    public Instance select(List<Instance> instances) {
        if(index >= instances.size()) {
            index %= instances.size();
        }
        return instances.get(index++);
    }

}
```

最后在NacosServiceDiscovery中调用就可以了，这里选择外部传入的方式使用LoadBalancer，看客户端传入哪个算法就使用哪个：

```java
public class NacosServiceDiscovery implements ServiceDiscovery {

    private static final Logger logger = LoggerFactory.getLogger(NacosServiceDiscovery.class);

    private final LoadBalancer loadBalancer;

    public NacosServiceDiscovery(LoadBalancer loadBalancer) {
        if (loadBalancer == null) {
            this.loadBalancer = new RandomLoadBalancer();
        } else {
            this.loadBalancer = loadBalancer;
        }
    }

    @Override
    public InetSocketAddress lookupService(String serviceName) {
        try {
            List<Instance> instances = NacosUtil.getAllInstance(serviceName);
            if(instances.size() == 0) {
                logger.error("找不到对应的服务: " + serviceName);
                throw new RpcException(RpcError.SERVICE_NOT_FOUND);
            }
            Instance instance = loadBalancer.select(instances);
            return new InetSocketAddress(instance.getIp(), instance.getPort());
        } catch (NacosException e) {
            logger.error("获取服务时有错误发生:", e);
        }
        return null;
    }

}
```

可以同时启动Socket和Netty两个服务端，对负载均衡进行测试（Netty和Socket之前已经通过统一序列化端序实现互通了）