package com.jchen.rpc.registry;

import java.net.InetSocketAddress;

/**
 * 服务发现接口
 *
 * @Auther: jchen
 * @Date: 2021/03/22/15:52
 */
public interface ServiceDiscovery {

    /**
     * 根据服务名称查找服务实体
     *
     * @param serviceName 服务名称
     * @return 服务实体
     */
    InetSocketAddress lookupService(String serviceName);

}
