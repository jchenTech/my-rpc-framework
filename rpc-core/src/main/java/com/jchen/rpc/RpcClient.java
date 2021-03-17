package com.jchen.rpc;

import com.jchen.rpc.entity.RpcRequest;

/**
 * 客户端类通用接口
 *
 * @Auther: jchen
 * @Date: 2021/03/17/9:39
 */
public interface RpcClient {
    Object sendRequest(RpcRequest rpcRequest);
}
