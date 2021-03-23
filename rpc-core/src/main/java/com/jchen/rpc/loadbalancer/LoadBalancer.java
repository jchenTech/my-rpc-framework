package com.jchen.rpc.loadbalancer;

import com.alibaba.nacos.api.naming.pojo.Instance;

import java.util.List;

/**
 * @Auther: jchen
 * @Date: 2021/03/23/15:52
 */
public interface  LoadBalancer {

    Instance select(List<Instance> instances);

}
