package com.jchen.rpc.registry;

import java.net.InetSocketAddress;

/**
 * 服务注册接口
 *
 * @Auther: jchen
 * @Date: 2021/03/16/18:20
 */
public interface ServiceRegistry {

    /**
     * 将一个服务注册进注册表
     * @param serviceName 服务名称
     * @param inetSocketAddress 提供服务的地址
     */
    void register(String serviceName, InetSocketAddress inetSocketAddress);
}
