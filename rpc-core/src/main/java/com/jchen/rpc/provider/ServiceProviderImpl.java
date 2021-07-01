package com.jchen.rpc.provider;

import com.jchen.rpc.enumeration.RpcError;
import com.jchen.rpc.exception.RpcException;
import com.jchen.rpc.registry.ServiceRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 默认的服务注册表，提供注册服务以及获取提供服务对象的功能
 *
 * @Auther: jchen
 * @Date: 2021/03/16/18:27
 */
public class ServiceProviderImpl implements ServiceProvider {
    private static final Logger logger = LoggerFactory.getLogger(ServiceProviderImpl.class);

    //<服务名，提供服务的对象>
    private static final Map<String, Object> serviceMap =  new ConcurrentHashMap<>();
    //已经注册的服务
    private static final Set<String> registeredService  = ConcurrentHashMap.newKeySet();


    /**
     * 向服务表中的添加服务以及提供服务的对象
     * @param service 提供服务的对象
     * @param serviceName 服务名
     * @param <T>
     */
    @Override
    public <T> void addServiceProvider(T service, String serviceName) {
        if (registeredService.contains(serviceName)) return;
        registeredService.add(serviceName);
        serviceMap.put(serviceName, service);
        logger.info("向接口: {} 注册服务: {}", service.getClass().getInterfaces(), serviceName);
    }

    /**
     * 获取提供服务的对象
     * @param serviceName 服务名
     * @return
     */
    @Override
    public synchronized Object getServiceProvider(String serviceName) {
        Object service = serviceMap.get(serviceName);
        if (service == null) {
            throw new RpcException(RpcError.SERVICE_NOT_FOUND);
        }
        return service;
    }
}
