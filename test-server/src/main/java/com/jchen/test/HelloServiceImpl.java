package com.jchen.test;


import com.jchen.rpc.api.HelloObject;
import com.jchen.rpc.api.HelloService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @Auther: jchen
 * @Date: 2021/03/15/13:14
 */
public class HelloServiceImpl implements HelloService {

    private static Logger logger = LoggerFactory.getLogger(HelloServiceImpl.class);

    @Override
    public String hello(HelloObject object) {
        logger.info("接收到：{}", object.getMessage());
        return "这是调用的返回值，id=" + object.getId();
    }
}
