## 什么是RPC框架

RPC（Remote Procedure Call）远程过程调用，简单的理解是一个节点请求另一个节点提供的服务。

原理很简单，客户端和服务端都可以访问到通用的接口，但是只有服务端有这个接口的实现类，客户端调用这个接口的方式，是通过网络传输，告诉服务端我要调用这个接口，服务端收到之后找到这个接口的实现类，并且执行，将执行的结果返回给客户端，作为客户端调用接口方法的返回值。

知道了原理之后，我们该如何实现呢？客户端如何直到服务端的地址？客户端怎么告诉服务端我要调用的接口？客户端怎么传递参数？只有接口客户端怎么生成实现类……等等等等。

这一章，我们就来探讨一个最简单的实现。一个最简单的实现，基于这样一个**假设，那就是客户端已经知道了服务端的地址**，这部分会由后续的服务发现机制完善。

![RPC框架思路](https://imgconvert.csdnimg.cn/aHR0cHM6Ly9jbi1ndW96aXlhbmcuZ2l0aHViLmlvL015LVJQQy1GcmFtZXdvcmsvaW1nL1JQQyVFNiVBMSU4NiVFNiU5RSVCNiVFNiU4MCU5RCVFOCVCNyVBRi5qcGVn?x-oss-process=image/format,png)

## API通用接口

我们先把通用的接口写好，然后再来看怎么实现客户端和服务端。其中接口中的hello方法即为客户端需要向远程服务端调用实现类的方法。

接口如下：

```java
public interface HelloService {
    String hello(HelloObject object);
}
```

hello方法需要传递一个对象，HelloObject对象，定义如下：

```java
@Data
@AllArgsConstructor
public class HelloObject implements Serializable {
    private Integer id;
    private String message;
}
```

注意这个对象**需要实现`Serializable`接口**，因为它需要在调用过程中从客户端传递给服务端。

接着我们在服务端对这个接口进行实现，该实现类中的实现方法即为客户端需要调用的方法。实现的方式也很简单，返回一个字符串就行：

```java
public class HelloServiceImpl implements HelloService {
	//使用sl4j打印日志
    private static Logger logger = LoggerFactory.getLogger(HelloServiceImpl.class);

    @Override
    public String hello(HelloObject object) {
        logger.info("接收到：{}", object.getMessage());
        return "这是调用的返回值，id=" + object.getId();
    }
}
```



## 传输协议

就是在客户端和服务端之间的传输的数据规定一个固定格式，具体采用什么格式，可以考虑服务端需要哪些信息，才能唯一确定服务端需要调用哪个接口的哪个方法呢？

我们来思考一下，服务端需要哪些信息，才能唯一确定服务端需要调用的接口的方法呢？

首先，就是**接口的名字**，和**方法的名字**，但是由于方法重载的缘故，我们还需要这个方法的**所有参数的类型**，最后，客户端调用时，还需要**传递参数的实际值**，那么服务端知道以上四个条件，就可以找到这个方法并且调用了。我们把这四个条件写到一个对象里，到时候传输时传输这个对象就行了。即`RpcRequest`对象：

```java
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RpcRequest implements Serializable {

    //待调用接口名
    private String interfaceName;

    //待调用方法名
    private String methodName;

    //待调用方法参数值
    private Object[] parameters;

    //待调用方法参数类型
    private Class<?>[] paramTypes;
}
```

`RpcRequest`对象在调用过程中也需要从客户端传递给服务端，因此要实现Serializable接口。参数类型这里直接使用Class对象，当然这里也可以用String字符串。

那么服务器调用完这个方法后，需要给客户端返回哪些信息呢？如果调用成功的话，显然需要返回值，如果调用失败了，就需要失败的信息，这里封装成一个`RpcResponse`对象：

```java
@Data
public class RpcResponse<T> implements Serializable {

    //响应状态码
    private Integer statusCode;

    //响应状态补充信息
    private String message;

    //响应数据
    private T data;

    /**
     * 生成远程调用成功的响应对象
     */
    public static <T> RpcResponse<T> success(T data) {
        RpcResponse<T> response = new RpcResponse<>();
        response.setStatusCode(ResponseCode.SUCCESS.getCode());
        response.setData(data);
        return response;
    }

    /**
     * 生成远程调用失败的响应对象
     */
    public static <T> RpcResponse<T> fail(ResponseCode code) {
        RpcResponse<T> response = new RpcResponse<>();
        response.setStatusCode(code.getCode());
        response.setMessage(code.getMessage());
        return response;
    }

```

这里有两个静态方法，用于快速的生成成功与失败的响应对象。其中，statusCode属性可以自行定义，客户端服务端一致即可。

这里为了设置状态码statusCode和message属性，写了一个枚举类：

```java
@AllArgsConstructor
@Getter
public enum ResponseCode {
    SUCCESS(200, "调用方法成功"),
    FAIL(500, "调用方法失败"),
    METHOD_NOT_FOUND(500, "未找到指定方法"),
    CLASS_NOT_FOUND(500, "未找到指定类");

    private final int code;
    private final String message;
}
```

## 客户端的实现——动态代理

客户端方面，由于在客户端这一侧我们并没有接口的具体实现类，就没有办法直接生成实例对象。这时，我们可以通过动态代理的方式生成实例，并且调用方法时生成需要的RpcRequest对象并且发送给服务端。

这里我们采用JDK动态代理，我们先来回顾一下动态代理需要解决的问题是什么：

* 问题一：如何根据加载到内存中的被代理类，动态的创建一个代理类及其对象。 （通过`Proxy.newProxyInstance()`实现）
* 问题二：当通过代理类的对象调用方法a时，应该执行什么操作。(通过实现`InvocationHandler`接口并重写其`invoke()` 方法)

下面是具体实现：

```java
public class RpcClientProxy implements InvocationHandler {

    private static final Logger logger = LoggerFactory.getLogger(RpcClientProxy.class);

    private String host;
    private int port;

    public RpcClientProxy(String host, int port) {
        this.host = host;
        this.port = port;
    }

    @SuppressWarnings("unchecked")
    public <T> T getProxy(Class<T> clazz) {
        return (T) Proxy.newProxyInstance(clazz.getClassLoader(), new Class<?>[]{clazz}, this);
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        logger.info("调用方法：{}#{}", method.getDeclaringClass(), method.getName());
        RpcRequest rpcRequest = new RpcRequest(method.getDeclaringClass().getName(),
                method.getName(), args, method.getParameterTypes());
        RpcClient rpcClient = new RpcClient();
        return ((RpcResponse) rpcClient.sendRequest(rpcRequest, host, port)).getData();
    }
}

```

我们需要传递host和port来指明服务端的位置。并且使用`getProxy()`方法来生成代理对象。

`InvocationHandler`接口需要实现`invoke()`方法，来指明代理对象的方法被调用时的动作。在这里，我们显然就需要生成一个RpcRequest对象，发送出去，然后返回从服务端接收到的结果即可。

更直白的说就是，当客户端的代理对象调用hello方法时，代理对象此时的任务是发送RpcRequest对象（里面包含方法调用需要的各个属性，如接口名，方法名，参数类型，参数值），然后服务端接收到后调用实现类方法返回服务端结果给代理对象。

生成RpcRequest对象很简单，这里我直接采用了默认的构造器构造了一个RpcRequest对象。发送RpcRequest对象的逻辑我使用一个RpcClient对象来实现。这个对象的作用是将一个对象发过去，并且接受返回的对象：

```java
public class RpcClient {
    private static final Logger logger = LoggerFactory.getLogger(RpcClient.class);

    public Object sendRequest(RpcRequest rpcRequest, String host, int port) {
        try {
            Socket socket = new Socket(host, port);
            ObjectOutputStream objectOutputStream = new ObjectOutputStream(socket.getOutputStream());
            ObjectInputStream objectInputStream = new ObjectInputStream(socket.getInputStream());
            objectOutputStream.writeObject(rpcRequest);
            objectOutputStream.flush();
            return objectInputStream.readObject();
        } catch (IOException | ClassNotFoundException e) {
            logger.error("调用时有错误发生：", e);
            return null;
        }
    }
}
```

实现的方式很简单，直接使用Java的序列化方式，通过Socket传输。通过ip和端口创建一个Socket，获取ObjectOutputStream对象，然后把需要发送的对象传进去即可，接收时获取ObjectInputStream对象，readObject()方法就可以获得一个返回的对象。

## 服务端的实现——反射调用

服务端的实现就简单多了，使用一个`ServerSocket`监听某个端口，循环接收连接请求，如果发来了请求就创建一个线程，在新线程中处理调用。这里创建线程采用线程池：

```java
public class RpcServer {
    private final ExecutorService threadPool;
    private static final Logger logger = LoggerFactory.getLogger(RpcServer.class);

    public RpcServer() {
        int corePoolSize = 5;
        int maximumPoolSize = 50;
        long keepAliveTime = 60;
        ArrayBlockingQueue<Runnable> workingQueue = new ArrayBlockingQueue<>(100);
        ThreadFactory threadFactory = Executors.defaultThreadFactory();
        threadPool = new ThreadPoolExecutor(corePoolSize, maximumPoolSize,
                keepAliveTime, TimeUnit.SECONDS, workingQueue, threadFactory);
    }
}
```

这里简化了一下，`RpcServer`暂时只能注册一个接口，即对外提供一个接口的调用服务，添加register方法，在注册完一个服务后立刻开始监听：

```java
public void register(Object service, int port) {
    try {
        ServerSocket serverSocket = new ServerSocket(port);
        logger.info("服务器正在启动");
        Socket socket;
        while ((socket = serverSocket.accept()) != null) {
            logger.info("客户端连接！IP为：" + socket.getInetAddress());
            threadPool.execute(new WorkerThread(socket, service));
        }
    } catch (IOException e) {
        logger.error("连接时有错误发生：", e);
    }
}
```

这里向工作线程`WorkerThread`传入了socket和用于服务端实例service。

`WorkerThread`实现了`Runnable`接口，用于接收`RpcRequest`对象，解析并且调用，生成`RpcResponse`对象并传输回去。run方法如下：

```java
public class WorkerThread implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(WorkerThread.class);

    private Socket socket;
    private Object service;

    public WorkerThread(Socket socket, Object service) {
        this.socket = socket;
        this.service = service;
    }

    /**
     * 接收RpcRequest对象，解析并且调用，生成RpcResponse对象并传输回去
     */
    @Override
    public void run() {
        try {
            ObjectInputStream objectInputStream = new ObjectInputStream(socket.getInputStream());
            ObjectOutputStream objectOutputStream = new ObjectOutputStream(socket.getOutputStream());
            RpcRequest rpcRequest = (RpcRequest) objectInputStream.readObject();
            //传入方法名和参数类型，获得服务端实现类的实现方法Method对象，这里的service.getClass()即为服务端的实现方法
            Method method = service.getClass().getMethod(rpcRequest.getMethodName(), rpcRequest.getParamTypes());
            //method.invoke传入对象实例和参数，调用并且获得返回值
            Object returnObject = method.invoke(service, rpcRequest.getParameters());
            //将方法调用结果生成RpcResponse对象并写入输出流
            objectOutputStream.writeObject(RpcResponse.success(returnObject));
            objectOutputStream.flush();
        } catch (IOException | ClassNotFoundException | NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            logger.error("调用或发送时有错误发生：", e);
        }
    }
}
```

## 测试

服务端侧，我们已经在上面实现了一个`HelloService`的实现类`HelloServiceImpl`的实现类了，我们只需要创建一个`RpcServer`并且把这个实现类注册进去就行了：

```java
public class TestServer {
    public static void main(String[] args) {
        HelloService helloService = new HelloServiceImpl();
        RpcServer rpcServer = new RpcServer();
        rpcServer.register(helloService, 9000);
    }
}
```

服务端开放在9000端口。

客户端方面，我们需要通过动态代理，生成代理对象，并且调用，动态代理会自动帮我们向服务端发送请求的：

```java
public class TestClient {
    public static void main(String[] args) {
        RpcClientProxy proxy = new RpcClientProxy("127.0.0.1", 9000);
        HelloService helloService = proxy.getProxy(HelloService.class);
        HelloObject object = new HelloObject(12, "This is a message");
        String res = helloService.hello(object);
        System.out.println(res);
    }
}
```

我们这里生成了一个HelloObject对象作为方法的参数。

首先启动服务端，再启动客户端，服务端输出：

```
[main] INFO com.jchen.rpc.server.RpcServer - 服务器正在启动
[main] INFO com.jchen.rpc.server.RpcServer - 客户端连接！IP为：/127.0.0.1
[pool-1-thread-1] INFO com.jchen.test.HelloServiceImpl - 接收到：This is a message
```

客户端输出：

```
[main] INFO com.jchen.rpc.client.RpcClientProxy - 调用方法：interface com.jchen.rpc.api.HelloService#hello
这是调用的返回值，id=12
```



第一阶段到此结束，假设了只有一个接口服务，客户端已知服务端地址，对RPC框架进行了简单实现，大致逻辑走向如图：

![img](https://img-blog.csdnimg.cn/20210206181657749.png?x-oss-process=image/watermark,type_ZmFuZ3poZW5naGVpdGk,shadow_10,text_aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L3FxXzM4Njg1NTAz,size_16,color_FFFFFF,t_70)