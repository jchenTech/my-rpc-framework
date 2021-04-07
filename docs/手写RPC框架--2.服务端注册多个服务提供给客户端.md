上一节中，我们使用 JDK 序列化和 Socket 实现了一个最基本的 RPC 框架，服务端测试时是这样的：

```java
public class TestServer {
    public static void main(String[] args) {
        HelloService helloService = new HelloServiceImpl();
        RpcServer rpcServer = new RpcServer();
        rpcServer.register(helloService, 9000);
    }
}
```

在注册完 `helloService` 后，服务器就自行启动了，也就是说，一个服务器只能注册一个服务，这显然是不合理的。因此我们现在将服务的注册和服务器启动分离，使得服务端可以提供多个服务。

## 服务注册表

我们需要一个容器，这个容器很简单，就是保存一些本地服务的信息，并且在获得一个服务名字的时候能够返回这个服务的信息。创建一个 ServiceRegistry 接口：

```java
public interface ServiceRegistry {

    /**
     * 将一个服务注册进注册表
     * @param service 待注册的服务实体
     * @param <T> 服务实体类
     */
    <T> void register(T service);

    /**
     * 根据服务名称获取服务实体
     * @param serviceName 服务名称
     * @return 服务实体
     */
    Object getService(String serviceName);
}
```

一目了然，一个register注册服务信息，一个getService获取服务信息。这里的Service指的就是服务端的实现类实体对象，而serviceName对应于接口名，即一个Service对应于一个接口。

我们新建一个默认的注册表类 `DefaultServiceRegistry` 来实现这个接口，提供服务注册服务，如下：

```java
public class DefaultServiceRegistry implements ServiceRegistry {
    private static final Logger logger = LoggerFactory.getLogger(DefaultServiceRegistry.class);

    //<服务名，服务对象>
    private final Map<String, Object> serviceMap =  new ConcurrentHashMap<>();
    private final Set<String> registeredService  = ConcurrentHashMap.newKeySet();


    @Override
    public synchronized <T> void register(T service) {
        String serviceName = service.getClass().getCanonicalName();
        if (registeredService.contains(serviceName)) return;
        registeredService.add(serviceName);
        Class<?>[] interfaces = service.getClass().getInterfaces();
        //如果该实现类没有对应接口，报异常
        if (interfaces.length == 0) {
            throw new RpcException(RpcError.SERVICE_NOT_IMPLEMENT_ANY_INTERFACE);
        }
        for(Class<?> i : interfaces) {
            serviceMap.put(i.getCanonicalName(), service);
        }
        logger.info("向接口: {} 注册服务: {}", interfaces, serviceName);
    }

    @Override
    public synchronized Object getService(String serviceName) {
        Object service = serviceMap.get(serviceName);
        if (service == null) {
            throw new RpcException(RpcError.SERVICE_NOT_FOUND);
        }
        return service;
    }
}
```

我们将服务名与提供服务的对象的对应关系保存在一个 `ConcurrentHashMap` 中，并且使用一个 Set 来保存当前有哪些对象已经被注册。在注册服务时，默认采用这个对象实现的接口的完整类名作为服务名，例如某个对象 A 实现了接口 X 和 Y，那么将 A 注册进去后，会有两个服务名 X 和 Y 对应于 A 对象。这种处理方式也就说明了某个接口只能有一个对象提供服务。

获得服务的对象就更简单了，直接去 Map 里查找就行了。

这里我们对于服务注册中出现的异常信息进行处理，创建一个`RpcError`枚举类和`RpcExption`类：

```java
public class RpcException extends RuntimeException {

    public RpcException(RpcError error, String detail) {
        super(error.getMessage() + ": " + detail);
    }

    public RpcException(String message, Throwable cause) {
        super(message, cause);
    }

    public RpcException(RpcError error) {
        super(error.getMessage());
    }
}
```

```java
public enum RpcError {
    SERVICE_INVOCATION_FAILURE("服务调用出现失败"),
    SERVICE_NOT_FOUND("找不到对应的服务"),
    SERVICE_NOT_IMPLEMENT_ANY_INTERFACE("注册的服务未实现接口");

    private final String message;
}
```

## 其他处理

为了降低耦合度，我们不会把 ServiceRegistry 和某一个 RpcServer 绑定在一起，而是在创建 RpcServer 对象时，传入一个 ServiceRegistry 作为这个服务的注册表。

那么 RpcServer 这个类现在就变成了这样：

```java
public class RpcServer {
    private static final Logger logger = LoggerFactory.getLogger(RpcServer.class);

    private static final int CORE_POOL_SIZE = 5;
    private static final int MAXIMUM_POLL_SIZE = 50;
    private static final int KEEP_ALIVE_TIME = 60;
    private static final int BLOCKING_QUEUE_CAPACITY = 100;
    private final ExecutorService threadPool;
    private RequestHandler requestHandler = new RequestHandler();
    private final ServiceRegistry serviceRegistry;

    //创建线程池，传入一个已经注册好服务的ServiceRegistry
    public RpcServer(ServiceRegistry serviceRegistry) {
        this.serviceRegistry = serviceRegistry;
        BlockingQueue<Runnable> workingQueue = new ArrayBlockingQueue<>(BLOCKING_QUEUE_CAPACITY);
        ThreadFactory threadFactory = Executors.defaultThreadFactory();
        threadPool = new ThreadPoolExecutor(CORE_POOL_SIZE, MAXIMUM_POLL_SIZE, KEEP_ALIVE_TIME,
                TimeUnit.SECONDS, workingQueue, threadFactory);
    }

    //启动服务端
    public void start(int port) {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            logger.info("服务器启动……");
            Socket socket;
            while((socket = serverSocket.accept()) != null) {
                logger.info("消费者连接: {}:{}", socket.getInetAddress(), socket.getPort());
                threadPool.execute(new RequestHandlerThread(socket, requestHandler, serviceRegistry));
            }
            threadPool.shutdown();
        } catch (IOException e) {
            logger.error("服务器启动时有错误发生:", e);
        }
    }
}
```

在创建 RpcServer 时需要传入一个已经注册好服务的 `ServiceRegistry`，而原来的 `register` 方法也被改成了 `start` 方法，因为服务的注册已经不由 RpcServer 处理了，它只需要启动就行了。

而在每一个请求处理线程（`RequestHandlerThread`）中也就需要传入 `ServiceRegistry` 了，这里把处理线程和处理逻辑分成了两个类：`RequestHandlerThread` 只是一个线程，从ServiceRegistry 获取到提供服务的对象后，就会把 `RpcRequest` 和服务对象直接交给 `RequestHandler` 去处理，反射等过程被放到了 `RequestHandler` 里。


`RequesthandlerThread.java`：处理线程，接受对象等

```java
public class RequestHandlerThread implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(RequestHandlerThread.class);

    private Socket socket;
    private RequestHandler requestHandler;
    private ServiceRegistry serviceRegistry;

    public RequestHandlerThread(Socket socket, RequestHandler requestHandler, ServiceRegistry serviceRegistry) {
        this.socket = socket;
        this.requestHandler = requestHandler;
        this.serviceRegistry = serviceRegistry;
    }

    /**
     * 处理线程，从输入流中获取rpcRequest对象，并获取对应服务，将服务执行后得到的RpcResponse对象写入输出流
     */
    @Override
    public void run() {
        try (ObjectInputStream objectInputStream = new ObjectInputStream(socket.getInputStream());
             ObjectOutputStream objectOutputStream = new ObjectOutputStream(socket.getOutputStream())) {
            RpcRequest rpcRequest = (RpcRequest) objectInputStream.readObject();

            //从RpcRequest对象中获取接口名，通过serviceRegistry获取服务（即实现类）
            String interfaceName = rpcRequest.getInterfaceName();
            Object service = serviceRegistry.getService(interfaceName);

            //调用requestHandler获得执行结果，得到RpcResponse对象写入输出流
            Object result = requestHandler.handle(rpcRequest, service);
            objectOutputStream.writeObject(RpcResponse.success(result));
            objectOutputStream.flush();
        } catch (IOException | ClassNotFoundException e) {
            logger.error("调用或发送时有错误发生：", e);
        }
    }

}
```

`RequestHandler.java`：通过反射进行方法调用

```java
public class RequestHandler {
    private static final Logger logger = LoggerFactory.getLogger(RequestHandler.class);

    /**
     * 通过输入的rpcRequest对象和对应的实现类，返回执行结果
     * @param rpcRequest 客户端发送的rpcRequest对象
     * @param service 服务端的实现类
     * @return 执行结果
     */
    public Object handle(RpcRequest rpcRequest, Object service) {
        Object result = null;
        try {
            result = invokeTargetMethod(rpcRequest, service);
            logger.info("服务:{} 成功调用方法:{}", rpcRequest.getInterfaceName(), rpcRequest.getMethodName());
        } catch (IllegalAccessException | InvocationTargetException e) {
            logger.error("调用或发送时有错误发生：", e);
        } return result;

    }

    /**
     * 通过反射进行方法调用，得到服务的实现方法，得到执行结果
     */
    private Object invokeTargetMethod(RpcRequest rpcRequest, Object service) throws IllegalAccessException, InvocationTargetException {
        Method method;
        try {
            method = service.getClass().getMethod(rpcRequest.getMethodName(), rpcRequest.getParamTypes());
        } catch (NoSuchMethodException e) {
            return RpcResponse.fail(ResponseCode.METHOD_NOT_FOUND);
        }
        return method.invoke(service, rpcRequest.getParameters());
    }

}
```

## 测试

这里简单的进行一个服务的测试，就是测试下兼容性而已。

服务端的测试：

```java
public class TestServer {
    public static void main(String[] args) {
        HelloService helloService = new HelloServiceImpl();
        ServiceRegistry serviceRegistry = new DefaultServiceRegistry();
        serviceRegistry.register(helloService);
        RpcServer rpcServer = new RpcServer(serviceRegistry);
        rpcServer.start(9000);
    }
}
```

客户端不需要变动。执行后应当获得和上次相同的结果，服务端：

```
[main] INFO com.jchen.rpc.registry.DefaultServiceRegistry - 向接口: [interface com.jchen.rpc.api.HelloService] 注册服务: com.jchen.test.HelloServiceImpl
[main] INFO com.jchen.rpc.server.RpcServer - 服务器启动……
[main] INFO com.jchen.rpc.server.RpcServer - 消费者连接: /127.0.0.1:61486
[pool-1-thread-1] INFO com.jchen.test.HelloServiceImpl - 接收到：This is a message
[pool-1-thread-1] INFO com.jchen.rpc.server.RequestHandler - 服务:com.jchen.rpc.api.HelloService 成功调用方法:hello
```

客户端：

```
[main] INFO com.jchen.rpc.client.RpcClientProxy - 调用方法：interface com.jchen.rpc.api.HelloService#hello
这是调用的返回值，id=12
```

