package com.jchen.rpc.exception;

/**
 * 序列化异常
 *
 * @Auther: jchen
 * @Date: 2021/03/18/14:16
 */
public class SerializeException extends RuntimeException {
    public SerializeException(String msg) {
        super(msg);
    }
}
