package com.jchen.rpc.loadbalancer;

import com.alibaba.nacos.api.naming.pojo.Instance;

import java.util.List;

/**
 * 负载均衡通用接口
 *
 * @Auther: jchen
 * @Date: 2021/03/23/15:52
 */
public interface  LoadBalancer {

    //从提供这个服务的服务端信息列表中选择一个
    Instance select(List<Instance> instances);

}
