package com.jchen.rpc.transport;

import com.jchen.rpc.entity.RpcRequest;
import com.jchen.rpc.serializer.CommonSerializer;

/**
 * 客户端类通用接口
 *
 * @Auther: jchen
 * @Date: 2021/03/17/9:39
 */
public interface RpcClient {
    Object sendRequest(RpcRequest rpcRequest);

    void setSerializer(CommonSerializer serializer);
}
