package com.jchen.rpc.transport;

import com.jchen.rpc.serializer.CommonSerializer;

/**
 * 服务器类通用接口
 *
 * @Auther: jchen
 * @Date: 2021/03/17/9:39
 */
public interface RpcServer {

    int DEFAULT_SERIALIZER = CommonSerializer.KRYO_SERIALIZER;

    void start();

    <T> void publishService(T service, Class<T> serviceClass);
}
