package com.jchen.rpc.transport;

import com.jchen.rpc.serializer.CommonSerializer;

/**
 * 服务器类通用接口
 *
 * @Auther: jchen
 * @Date: 2021/03/17/9:39
 */
public interface RpcServer {

    //序列化器
    int DEFAULT_SERIALIZER = CommonSerializer.KRYO_SERIALIZER;

    //启动方法
    void start();

    //注册服务
    <T> void publishService(T service, String serviceName);

}
