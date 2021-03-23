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
 * 默认的服务注册表
 *
 * @Auther: jchen
 * @Date: 2021/03/16/18:27
 */
public class ServiceProviderImpl implements ServiceProvider {
    private static final Logger logger = LoggerFactory.getLogger(ServiceProviderImpl.class);

    //<服务名，服务对象>
    private static final Map<String, Object> serviceMap =  new ConcurrentHashMap<>();
    private static final Set<String> registeredService  = ConcurrentHashMap.newKeySet();


    @Override
    public synchronized <T> void addServiceProvider(T service, Class<T> serviceClass) {
        String serviceName = serviceClass.getCanonicalName();
        if (registeredService.contains(serviceName)) return;
        registeredService.add(serviceName);
        serviceMap.put(serviceName, service);
        logger.info("向接口: {} 注册服务: {}", service.getClass().getInterfaces(), serviceName);
    }

    @Override
    public synchronized Object getServiceProvider(String serviceName) {
        Object service = serviceMap.get(serviceName);
        if (service == null) {
            throw new RpcException(RpcError.SERVICE_NOT_FOUND);
        }
        return service;
    }
}
