package com.jchen.rpc;

import com.jchen.rpc.entity.RpcRequest;
import com.jchen.rpc.entity.RpcResponse;
import com.jchen.rpc.enumeration.ResponseCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * 进行过程调用的处理器，通过反射进行方法调用
 *
 * @Auther: jchen
 * @Date: 2021/03/16/19:17
 */
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
        }
        return result;

    }

    /**
     * 通过反射进行方法调用，得到服务的实现方法，得到执行结果
     */
    private Object invokeTargetMethod(RpcRequest rpcRequest, Object service) throws IllegalAccessException, InvocationTargetException {
        Method method;
        try {
            method = service.getClass().getMethod(rpcRequest.getMethodName(), rpcRequest.getParamTypes());
        } catch (NoSuchMethodException e) {
            return RpcResponse.fail(ResponseCode.METHOD_NOT_FOUND, rpcRequest.getRequestId());
        }
        return method.invoke(service, rpcRequest.getParameters());
    }

}
