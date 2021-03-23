package com.jchen.rpc.util;

import com.jchen.rpc.entity.RpcRequest;
import com.jchen.rpc.entity.RpcResponse;
import com.jchen.rpc.enumeration.ResponseCode;
import com.jchen.rpc.enumeration.RpcError;
import com.jchen.rpc.exception.RpcException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 检查响应与请求
 *
 * @Auther: jchen
 * @Date: 2021/03/21/14:35
 */
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
        if (!rpcRequest.getRequestId().equals(rpcResponse.getRequestId())) {
            throw new RpcException(RpcError.RESPONSE_NOT_MATCH, INTERFACE_NAME + ":" + rpcRequest.getInterfaceName());
        }
        //rpcResponse响应状态码为空或响应状态码不成功
        if (rpcResponse.getStatusCode() == null || !rpcResponse.getStatusCode().equals(ResponseCode.SUCCESS.getCode())) {
            logger.error("调用服务失败，serviceName：{}，RpcResponse：{}", rpcRequest.getInterfaceName(), rpcResponse);
            throw new RpcException(RpcError.SERVICE_INVOCATION_FAILURE, INTERFACE_NAME + ":" + rpcRequest.getInterfaceName());
        }
    }
}
