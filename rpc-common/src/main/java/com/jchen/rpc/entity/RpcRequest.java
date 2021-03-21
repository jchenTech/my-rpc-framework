package com.jchen.rpc.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 消费者向提供者发送的请求对象
 *
 * @Auther: jchen
 * @Date: 2021/03/15/13:18
 */
@Data
@AllArgsConstructor
public class RpcRequest implements Serializable {

    //请求号
    private String requestId;

    //待调用接口名
    private String interfaceName;

    //待调用方法名
    private String methodName;

    //待调用方法参数值
    private Object[] parameters;

    //待调用方法参数类型
    private Class<?>[] paramTypes;

    public RpcRequest() {

    }
}
