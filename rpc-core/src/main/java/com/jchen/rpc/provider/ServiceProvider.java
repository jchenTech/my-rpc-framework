package com.jchen.rpc.provider;

/**
 * 保存和提供服务实例对象
 *
 * @Auther: jchen
 * @Date: 2021/03/22/14:12
 */
public interface ServiceProvider {
    <T> void addServiceProvider(T service, String serviceName);

    Object getServiceProvider(String serviceName);
}
