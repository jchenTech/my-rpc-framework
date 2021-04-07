## 介绍

my-rpc-framework 是一款基于 Nacos 实现的 RPC 框架。网络传输实现了基于 Java 原生 Socket 与 Netty 版本，并且实现了多种序列化与负载均衡算法。

RPC（Remote Procedure Call）远程过程调用，简单的理解是一个节点请求另一个节点提供的服务。

原理很简单，客户端和服务端都可以访问到通用的接口，但是只有服务端有这个接口的实现类，客户端调用这个接口的方式，是通过网络传输，告诉服务端我要调用这个接口，服务端收到之后找到这个接口的实现类，并且执行，将执行的结果返回给客户端，作为客户端调用接口方法的返回值。

![RPC框架思路](https://imgconvert.csdnimg.cn/aHR0cHM6Ly9jbi1ndW96aXlhbmcuZ2l0aHViLmlvL015LVJQQy1GcmFtZXdvcmsvaW1nL1JQQyVFNiVBMSU4NiVFNiU5RSVCNiVFNiU4MCU5RCVFOCVCNyVBRi5qcGVn?x-oss-process=image/format,png)

消费者调用提供者的方式取决于消费者的客户端选择，如选用原生 Socket 则该步调用使用 BIO，如选用 Netty 方式则该步调用使用 NIO。如该调用有返回值，则提供者向消费者发送返回值的方式同理。



## 项目模块概览

- **roc-api** —— 通用接口
- **rpc-common** —— 实体对象、工具类等公用类
- **rpc-core** —— 框架的核心实现
- **test-client** —— 测试用消费侧
- **test-server** —— 测试用提供侧



## 实现功能

- 实现了基于 Java 原生 Socket 传输与 Netty 传输两种网络传输方式
- 实现了四种序列化算法，Json 方式、Kryo 算法、Hessian 算法与 Google Protobuf 方式（默认采用 Kryo方式序列化）
- 实现了两种负载均衡算法：随机算法与轮转算法
- 使用 Nacos 作为注册中心，管理服务提供者信息
- 消费端如采用 Netty 方式，会复用 Channel 避免多次连接
- 如消费端和提供者都采用 Netty 方式，会采用 Netty 的心跳机制，保证连接
- 接口抽象良好，模块耦合度低，网络传输、序列化器、负载均衡算法可配置
- 实现自定义的通信协议
- 服务提供侧自动注册服务



## 传输协议（MRF协议）

调用参数与返回值的传输采用了如下 MRF 协议（ my-rpc-framework 首字母）以防止粘包：

```
+---------------+---------------+-----------------+-------------+
|  Magic Number |  Package Type | Serializer Type | Data Length |
|    4 bytes    |    4 bytes    |     4 bytes     |   4 bytes   |
+---------------+---------------+-----------------+-------------+
|                          Data Bytes                           |
|                   Length: ${Data Length}                      |
+---------------------------------------------------------------+
```

| 字段            | 解释                                                         |
| --------------- | ------------------------------------------------------------ |
| Magic Number    | 魔数，表识一个 MRF 协议包，0xCAFEBABE                        |
| Package Type    | 包类型，标明这是一个调用请求还是调用响应                     |
| Serializer Type | 序列化器类型，标明这个包的数据的序列化方式                   |
| Data Length     | 数据字节的长度                                               |
| Data Bytes      | 传输的对象，通常是一个`RpcRequest`或`RpcClient`对象，取决于`Package Type`字段，对象的序列化方式取决于`Serializer Type`字段。 |



## 运行

1、定义客户端调用接口

```java
public interface HelloService {
    String hello(HelloObject object);
}
```

2、在服务提供侧实现接口

```java
@Service
public class HelloServiceImpl implements HelloService {
    @Override
    public String hello(HelloObject object) {
        logger.info("接收到消息：{}", object.getMessage());
        return "这是Impl1方法";
    }
}
```

3、编写服务提供者，这里选用Netty传输方式，并且指定序列化方式为Google Protobuf方式。

```java
@ServiceScan
public class NettyTestServer {
    public static void main(String[] args) {
        RpcServer server = new NettyServer("127.0.0.1", 9999, CommonSerializer.PROTOBUF_SERIALIZER);
        server.start();
    }
}
```

4、在消费侧远程调用，这里客户端也选用了 Netty 的传输方式，序列化方式采用 Kryo 方式，负载均衡策略指定为轮转方式。

```java
public class NettyTestClient {
    public static void main(String[] args) {
        RpcClient client = new NettyClient(CommonSerializer.PROTOBUF_SERIALIZER);
        RpcClientProxy rpcClientProxy = new RpcClientProxy(client);
        HelloService helloService = rpcClientProxy.getProxy(HelloService.class);
        HelloObject object = new HelloObject(12, "This is a message");
        String res = helloService.hello(object);
        System.out.println(res);
        ByeService byeService = rpcClientProxy.getProxy(ByeService.class);
        System.out.println(byeService.bye("Netty"));
    }
}
```

启动时先启动服务端，再启动客户端，在此之前确保Nacos运行在本地8848端口