package com.jchen.rpc.server;

import com.jchen.rpc.entity.RpcRequest;
import com.jchen.rpc.entity.RpcResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.Socket;

/**
 * 实际进行过程调用的工作线程
 *
 * @Auther: jchen
 * @Date: 2021/03/15/20:31
 */
public class WorkerThread implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(WorkerThread.class);

    private Socket socket;
    private Object service;

    public WorkerThread(Socket socket, Object service) {
        this.socket = socket;
        this.service = service;
    }

    /**
     * 接收RpcRequest对象，解析并且调用，生成RpcResponse对象并传输回去
     */
    @Override
    public void run() {
        try {
            ObjectInputStream objectInputStream = new ObjectInputStream(socket.getInputStream());
            ObjectOutputStream objectOutputStream = new ObjectOutputStream(socket.getOutputStream());
            RpcRequest rpcRequest = (RpcRequest) objectInputStream.readObject();
            //传入方法名和参数类型，获得服务端实现类的实现方法Method对象，这里的service.getClass()即为服务端的实现方法
            Method method = service.getClass().getMethod(rpcRequest.getMethodName(), rpcRequest.getParamTypes());
            //method.invoke传入对象实例和参数，调用并且获得返回值
            Object returnObject = method.invoke(service, rpcRequest.getParameters());
            //将方法调用结果生成RpcResponse对象并写入输出流
            objectOutputStream.writeObject(RpcResponse.success(returnObject));
            objectOutputStream.flush();
        } catch (IOException | ClassNotFoundException | NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            logger.error("调用或发送时有错误发生：", e);
        }
    }
}
