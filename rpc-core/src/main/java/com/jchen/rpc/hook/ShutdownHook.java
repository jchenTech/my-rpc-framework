package com.jchen.rpc.hook;

import com.jchen.rpc.util.NacosUtil;
import com.jchen.rpc.util.ThreadPoolFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;

/**
 * 钩子，用于在服务端关闭之前自动注销服务
 *
 * @Auther: jchen
 * @Date: 2021/03/23/13:07
 */
public class ShutdownHook {

    private static final Logger logger = LoggerFactory.getLogger(ShutdownHook.class);

    private final ExecutorService threadPool = ThreadPoolFactory.createDefaultThreadPool("shutdown-hook");
    private static final ShutdownHook shutdownHook = new ShutdownHook();

    public static ShutdownHook getShutdownHook() {
        return shutdownHook;
    }

    /**
     * 当JVM关闭时，会执行自动注销服务任务
     */
    public void addClearAllHook() {
        logger.info("关闭后将自动注销所有服务");
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            //注销对应服务器的服务
            NacosUtil.clearRegistry();
            //关闭线程池
            ThreadPoolFactory.shutDownAll();
        }));
    }
}
