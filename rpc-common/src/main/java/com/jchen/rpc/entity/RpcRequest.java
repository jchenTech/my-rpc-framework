package com.jchen.rpc.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 消费者向提供者发送的请求对象：
 * 客户端需要调用服务端的对应方法，那么服务端如何才能确定哪个方法呢？
 * 因此客户端应该发送接口名，方法名，同时为了避免重载影响，还应该发送参数值，参数类型，有这四个参数能够唯一确定一个服务
 * @Auther: jchen
 * @Date: 2021/03/15/13:18
 */
@Data
@NoArgsConstructor
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

    //是否是心跳包
    private Boolean heartBeat;

}
