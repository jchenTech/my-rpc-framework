考虑到客户端调用方法的时候发送的请求与响应可能不一致，因此我们为请求添加请求号，在客户端调用方法时请求和响应的请求号检查机制。

首先我们在RpcRequest类和RpcReponse类中添加我们的请求号：

```java
public class RpcRequest implements Serializable {
    //请求号
    private String requestId;
    ...
    }
}
```

`RpcError`中添加一个请求与响应不一致错误：

```java
RESPONSE_NOT_MATCH("响应与请求号不匹配");
```

添加一个`RpcMessageChecker`工具类，用于检查请求与响应的请求号是否一致：

```java
public class RpcMessageChecker {
    public static final String INTERFACE_NAME = "interfaceName";
    private static final Logger logger = LoggerFactory.getLogger(RpcMessageChecker.class);

    private RpcMessageChecker() {

    }

    public static void check(RpcRequest rpcRequest, RpcResponse rpcResponse) {
        //rpcResponse为空
        if (rpcResponse == null) {
            logger.error("调用服务失败，serviceName：{}", rpcRequest.getInterfaceName());
            throw new RpcException(RpcError.SERVICE_INVOCATION_FAILURE, INTERFACE_NAME + ":" + rpcRequest.getInterfaceName());
        }
        //rpcRequest和rpcResponse的请求号不一致
        if (!rpcRequest.getRequestId().equals(rpcResponse.getRquestId())) {
            throw new RpcException(RpcError.RESPONSE_NOT_MATCH, INTERFACE_NAME + ":" + rpcRequest.getInterfaceName());
        }
        //rpcResponse响应状态码为空或响应状态码不成功
        if (rpcResponse.getStatusCode() == null || !rpcResponse.getStatusCode().equals(ResponseCode.SUCCESS.getCode())) {
            logger.error("调用服务失败，serviceName：{}，RpcResponse：{}", rpcRequest.getInterfaceName(), rpcResponse);
            throw new RpcException(RpcError.SERVICE_INVOCATION_FAILURE, INTERFACE_NAME + ":" + rpcRequest.getInterfaceName());
        }
    }
}

```

在客户端调用方法时会发送请求，此时的请求对象中包含了请求号，而服务端在接收到请求后，调用方法返回响应对象，响应对象中不仅包含执行结果，同时也包含了接收到的请求号。客户端在接受服务端的响应时会验证发送的请求与接受的响应中的请求号是否一致。

