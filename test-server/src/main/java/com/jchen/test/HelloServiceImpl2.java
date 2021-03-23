package com.jchen.test;

import com.jchen.rpc.api.HelloObject;
import com.jchen.rpc.api.HelloService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @Auther: jchen
 * @Date: 2021/03/22/15:58
 */
public class HelloServiceImpl2 implements HelloService {

    private static final Logger logger = LoggerFactory.getLogger(HelloServiceImpl2.class);

    @Override
    public String hello(HelloObject object) {
        logger.info("接收到消息：{}", object.getMessage());
        return "本次处理来自Socket服务";
    }
}