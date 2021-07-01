package com.jchen.rpc.serializer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jchen.rpc.exception.SerializeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.jchen.rpc.entity.RpcRequest;
import com.jchen.rpc.enumeration.SerializerCode;

import java.io.IOException;

/**
 * 使用JSON格式的序列化器，执行序列化和反序列化功能
 * 注意JSON序列化器在反序列化Object对象时会发生发序列化失败现象！！
 *
 * @Auther: jchen
 * @Date: 2021/03/17/11:04
 */
public class JsonSerializer implements CommonSerializer {

    private static final Logger logger = LoggerFactory.getLogger(JsonSerializer.class);

    private ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public byte[] serialize(Object obj) {
        try {
            return objectMapper.writeValueAsBytes(obj);
        } catch (JsonProcessingException e) {
            logger.error("序列化时有错误发生:", e);
            throw new SerializeException("序列化时有错误发生");
        }
    }

    @Override
    public Object deserialize(byte[] bytes, Class<?> clazz) {
        try {
            Object obj = objectMapper.readValue(bytes, clazz);
            if(obj instanceof RpcRequest) {
                obj = handleRequest(obj);
            }
            return obj;
        } catch (IOException e) {
            logger.error("反序列化时有错误发生:", e);
            throw new SerializeException("反序列化时有错误发生");
        }
    }

    /*
        这里由于使用JSON序列化和反序列化Object数组(参数值Object[] parameters;)，无法保证反序列化后仍然为原实例类型，
        因为在序列化时JSON本质上只是转换成JSON字符串，不会记录对象的类型信息，因此需要ParamTypes来获取对象信息，辅助反序列化。
     */
    private Object handleRequest(Object obj) throws IOException {
        RpcRequest rpcRequest = (RpcRequest) obj;
        for(int i = 0; i < rpcRequest.getParamTypes().length; i ++) {
            Class<?> clazz = rpcRequest.getParamTypes()[i];
            if(!clazz.isAssignableFrom(rpcRequest.getParameters()[i].getClass())) {
                byte[] bytes = objectMapper.writeValueAsBytes(rpcRequest.getParameters()[i]);
                rpcRequest.getParameters()[i] = objectMapper.readValue(bytes, clazz);
            }
        }
        return rpcRequest;
    }

    @Override
    public int getCode() {
        return SerializerCode.valueOf("JSON").getCode();
    }

}

