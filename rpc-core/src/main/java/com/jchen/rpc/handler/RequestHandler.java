package com.jchen.rpc.handler;

import com.jchen.rpc.entity.RpcRequest;
import com.jchen.rpc.entity.RpcResponse;
import com.jchen.rpc.enumeration.ResponseCode;
import com.jchen.rpc.provider.ServiceProvider;
import com.jchen.rpc.provider.ServiceProviderImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * 进行方法调用的处理器，通过反射进行方法调用
 *
 * @Auther: jchen
 * @Date: 2021/03/16/19:17
 */
public class RequestHandler {
    private static final Logger logger = LoggerFactory.getLogger(RequestHandler.class);
    private static final ServiceProvider serviceProvider;

    static {
        serviceProvider = new ServiceProviderImpl();
    }

    /**
     * 通过输入的rpcRequest对象，查找对应的提供服务的对象，返回执行结果
     * @param rpcRequest 客户端发送的rpcRequest对象
     * @return 执行结果
     */
    public Object handle(RpcRequest rpcRequest) {
        //查找服务
        Object service = serviceProvider.getServiceProvider(rpcRequest.getInterfaceName());
        return invokeTargetMethod(rpcRequest, service);

    }

    /**
     * 通过反射进行方法调用，得到服务的实现方法，得到执行结果
     * @param rpcRequest 请求对象
     * @param service 提供服务的对象
     * @return
     */
    private Object invokeTargetMethod(RpcRequest rpcRequest, Object service) {
        Object result;
        try {
            Method method = service.getClass().getMethod(rpcRequest.getMethodName(), rpcRequest.getParamTypes());
            result = method.invoke(service, rpcRequest.getParameters());
            logger.info("服务:{} 成功调用方法:{}", rpcRequest.getInterfaceName(), rpcRequest.getMethodName());
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            //如果未找到对应方法或其他异常，则返回错误的响应信息
            return RpcResponse.fail(ResponseCode.METHOD_NOT_FOUND, rpcRequest.getRequestId());
        }
        return result;
    }

}
