之前我们的服务端地址是固化在代码中的，也就是说，对于一个客户端，它只会去寻找那么一个服务提供者，如果这个提供者挂了或者换了地址，那就没有办法了。

在分布式架构中，有一个重要的组件，就是服务注册中心，它用于保存多个服务提供者的信息，每个服务提供者在启动时都需要向注册中心注册自己所拥有的服务。这样客户端在发起 RPC 时，就可以直接去向注册中心请求服务提供者的信息，如果拿来的这个挂了，还可以重新请求，并且在这种情况下可以很方便地实现负载均衡。

常见的注册中心有 Eureka、Zookeeper 和 Nacos。

## Nacos 介绍

Nacos 是阿里开发的一款服务注册中心，在 SpringCloud Alibaba 逐步替代原始的 SpringCloud 的过程中，Nacos 逐步走红，所以我们就是用 Nacos 作为我们的注册中心。

下载解压的过程略过。注意 Nacos 是依赖数据库的，所以我们需要在配置文件中配置 Mysql 的信息。

为了简单，我们先以单机模式运行：

```xml
<dependency>
    <groupId>com.alibaba.nacos</groupId>
    <artifactId>nacos-client</artifactId>
    <version>1.3.0</version>
</dependency>
```

这里我们修正之前的概念，第二节把本地保存服务的类称为 `ServiceRegistry`，现在更改为 `ServiceProvider`，而 `ServiceRegistry` 作为远程注册表（Nacos）使用，对应的类名也有修改。

这里我们实现一个接口 ServiceRegistry：

```java
public interface ServiceRegistry {

    /**
     * 将一个服务注册进注册表
     * @param serviceName 服务名称
     * @param inetSocketAddress 提供服务的地址
     */
    void register(String serviceName, InetSocketAddress inetSocketAddress);

    /**
     * 根据服务名称查找服务实体
     * @param serviceName
     * @return 服务实体
     */
    InetSocketAddress lookupService(String serviceName);
}
```

两个方法很好理解，`register` 方法将服务的名称和地址注册进服务注册中心，`lookupService` 方法则是根据服务名称从注册中心获取到一个服务提供者的地址。

接口有了，我们就可以写实现类了，我们实现一个 Nacos 作为注册中心的实现类：`NacosServiceRegistry`，我们也可以使用 ZooKeeper 作为注册中心，实现接口就可以

```java
public class NacosServiceRegistry implements ServiceRegistry {
    private static final Logger logger = LoggerFactory.getLogger(NacosServiceRegistry.class);

    private static final String SERVER_ADDR = "127.0.0.1:8848";
    private static final NamingService namingService = null;

    static {
        try {
            NamingFactory.createNamingService(SERVER_ADDR);
        } catch (NacosException e) {
            logger.error("连接到Nacos时有错误发生：" , e);
            throw new RpcException(RpcError.FAILED_TO_CONNECT_TO_SERVICE_REGISTRY);
        }
    }

    @Override
    public void register(String serviceName, InetSocketAddress inetSocketAddress) {
        try {
            namingService.registerInstance(serviceName, inetSocketAddress.getHostName(), inetSocketAddress.getPort());
        } catch (NacosException e) {
            logger.error("注册服务时有错误发生：", e);
            throw new RpcException(RpcError.REGISTER_SERVICE_FAILED);
        }
    }

    @Override
    public InetSocketAddress lookupService(String serviceName) {
        List<Instance> instances = null;
        try {
            instances = namingService.getAllInstances(serviceName);
            Instance instance = instances.get(0);
            return new InetSocketAddress(instance.getIp(), instance.getPort());
        } catch (NacosException e) {
            logger.error("获取服务时有错误发生：", e);
        }
        return null;
    }
}
```

Nacos 的使用很简单，通过 NamingFactory 创建 NamingService 连接 Nacos（连接的时候没有找到修改用户名密码的方式……是不需要吗），连接的过程写在了静态代码块中，在类加载时自动连接。namingService 提供了两个很方便的接口，`registerInstance` 和 `getAllInstances` 方法，前者可以直接向 Nacos 注册服务，后者可以获得提供某个服务的所有提供者的列表。所以接口的这两个方法只需要包装一下就好了。

在 lookupService 方法中，通过 `getAllInstance` 获取到某个服务的所有提供者列表后，需要选择一个，这里就涉及了负载均衡策略，这里我们先选择第 0 个，后面某节会详细讲解负载均衡。


## 注册服务

我们修改 RpcServer 接口，新增一个方法 `publishService`，用于向 Nacos 注册服务：

```java
<T> void publishService(Object service, Class<T> serviceClass);
```

接着只需要实现这个方法即可，以 NettyServer 的实现为例，NettyServer 在创建时需要创建一个 `ServiceRegistry` 了：

```java
public NettyServer(String host, int port) {
    this.host = host;
    this.port = port;
    serviceRegistry = new NacosServiceRegistry();
    serviceProvider = new ServiceProviderImpl();
}
```

接着实现 `publishService` 方法即可：

```java
@Override
public <T> void publishService(Object service, Class<T> serviceClass) {
    if (serializer == null) {
        logger.error("未设置序列化器");
        throw new RpcException(RpcError.SERVICE_NOT_FOUND);
    }
    serviceProvider.addServiceProvider(service);
    serviceRegistry.register(serviceClass.getCanonicalName(), new InetSocketAddress(host, port));
    start();
}
```

`publishService` 需要将服务保存在本地的注册表，同时注册到 Nacos 上。我这里的实现是注册完一个服务后直接调用 start() 方法，这是个不太好的实现……导致一个服务端只能注册一个服务，之后可以多注册几个然后再手动调用 start() 方法。

## 发现服务

客户端的修改就更简单了，以 NettyClient 为例，在过去创建 NettyClient 时，需要传入 host 和 port，现在这个 host 和 port 是通过 Nacos 获取的，sendRequest 修改如下：

```java
public Object sendRequest(RpcRequest rpcRequest) {
        if(serializer == null) {
            logger.error("未设置序列化器");
            throw new RpcException(RpcError.SERIALIZER_NOT_FOUND);
        }
        AtomicReference<Object> result = new AtomicReference<>(null);
        try {
            InetSocketAddress inetSocketAddress = serviceRegistry.lookupService(rpcRequest.getInterfaceName());
            Channel channel = ChannelProvider.get(inetSocketAddress, serializer);
...
```

重点是最后两句，过去是直接使用传入的 host 和 port 直接构造 channel，现在是首先从 ServiceRegistry 中获取到服务的地址和端口，再构造。

## 测试

NettyTestClient 如下：

```java
public class NettyTestClient {
    public static void main(String[] args) {
        RpcClient client = new NettyClient();
        client.setSerializer(new ProtobufSerializer());
        RpcClientProxy rpcClientProxy = new RpcClientProxy(client);
        HelloService helloService = rpcClientProxy.getProxy(HelloService.class);
        HelloObject object = new HelloObject(12, "This is a message");
        String res = helloService.hello(object);
        System.out.println(res);
    }
}
```

构造 RpcClient 时不再需要传入地址和端口。

NettyTestServer 如下：

```java
public class NettyTestServer {
    public static void main(String[] args) {
        HelloService helloService = new HelloServiceImpl();
        NettyServer server = new NettyServer("127.0.0.1", 9999);
        server.setSerializer(new ProtobufSerializer());
        server.publishService(helloService, HelloService.class);
    }
}
```

我这里是把 start 写在了 publishService 中，实际应当分离，否则只能注册一个服务。

分别启动，可以看到和之前相同的结果。

这里如果通过修改不同的端口，启动两个服务的话，会看到即使客户端多次调用，也只是由同一个服务端提供服务，这是因为在 NacosServiceRegistry 中，我们直接选择了服务列表的第 0 个，这个会在之后讲解负载均衡时作出修改。





## 创建Nacos连接工具类

由于客户端只会向Nacos寻找服务，服务端只会向Nacos注册服务，因此我们考虑将Nacos的注册和服务发现功能进行分离，因此我们需要先把Nacos连接，向Nacos注册服务与从Nacos中进行服务发现等相关操作分离，写到一个工具类中，以便于我们的客户端和服务端进行调用。

创建一个NacosUtil类：

```java
public class NacosUtil {
    private static final Logger logger = LoggerFactory.getLogger(NacosUtil.class);

    private static final String SERVER_ADDR = "127.0.0.1:8848";

    public static NamingService getNacosNamingService() {
        try {
            return NamingFactory.createNamingService(SERVER_ADDR);
        } catch (NacosException e) {
            logger.error("连接到Nacos时有错误发生: ", e);
            throw new RpcException(RpcError.FAILED_TO_CONNECT_TO_SERVICE_REGISTRY);
        }
    }

    public static void registerService(NamingService namingService, String serviceName, InetSocketAddress address) throws NacosException {
        namingService.registerInstance(serviceName, address.getHostName(), address.getPort());
    }

    public static List<Instance> getAllInstance(NamingService namingService, String serviceName) throws NacosException {
        return namingService.getAllInstances(serviceName);
    }
}
```

然后将服务发现功能从服务注册中抽象出来，即ServiceDiscovery只负责服务发现，供客户端使用；ServiceRegistry只负责服务注册，供服务端使用。

```java
public interface ServiceDiscovery {
    InetSocketAddress lookupService(String serviceName);
}

public interface ServiceRegistry {
    void register(String serviceName, InetSocketAddress inetSocketAddress);
}
```

同样，将实现类抽象出来：

```java
public class NacosServiceDiscovery implements ServiceDiscovery {

    private static final Logger logger = LoggerFactory.getLogger(NacosServiceDiscovery.class);

    private final NamingService namingService;

    public NacosServiceDiscovery() {
        namingService = NacosUtil.getNacosNamingService();
    }

    @Override
    public InetSocketAddress lookupService(String serviceName) {
        try {
            List<Instance> instances = NacosUtil.getAllInstance(namingService, serviceName);
            Instance instance = instances.get(0);
            return new InetSocketAddress(instance.getIp(), instance.getPort());
        } catch (NacosException e) {
            logger.error("获取服务时有错误发生:", e);
        }
        return null;
    }

}
```

```java
public class NacosServiceRegistry implements ServiceRegistry {
    private static final Logger logger = LoggerFactory.getLogger(NacosServiceRegistry.class);

    public final NamingService namingService;

    public NacosServiceRegistry() {
        this.namingService = NacosUtil.getNacosNamingService();
    }

    @Override
    public void register(String serviceName, InetSocketAddress inetSocketAddress) {
        try {
            NacosUtil.registerService(namingService, serviceName, inetSocketAddress);
        } catch (NacosException e) {
            logger.error("注册服务时有错误发生:", e);
            throw new RpcException(RpcError.REGISTER_SERVICE_FAILED);
        }
    }

}
```

现在服务端和客户端即可分别调用NacosServiceRegistry和NacosServiceDiscovery实现注册和发现功能。