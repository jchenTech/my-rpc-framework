package com.jchen.rpc.exception;

import com.jchen.rpc.enumeration.RpcError;

/**
 * RPC调用异常
 *
 * @Auther: jchen
 * @Date: 2021/03/16/18:34
 */
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
