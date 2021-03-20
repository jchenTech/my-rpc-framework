package com.jchen.rpc;

import com.jchen.rpc.serializer.CommonSerializer;

/**
 * 服务器类通用接口
 *
 * @Auther: jchen
 * @Date: 2021/03/17/9:39
 */
public interface RpcServer {
    void start(int port);

    void setSerializer(CommonSerializer serializer);
}
