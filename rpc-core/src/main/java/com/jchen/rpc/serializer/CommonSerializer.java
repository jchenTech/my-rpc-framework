package com.jchen.rpc.serializer;

/**
 * 通用的序列化反序列化接口
 *
 * @Auther: jchen
 * @Date: 2021/03/17/10:58
 */
public interface CommonSerializer {

    static CommonSerializer getByCode(int code) {
        switch (code) {
            case 0:
                return new KryoSerializer();
            case 1:
                return new JsonSerializer();
            case 2:
                return new HessianSerializer();
            case 3:
                return new ProtobufSerializer();
            default:
                return null;
        }
    }

    byte[] serialize(Object obj);

    Object deserialize(byte[] bytes, Class<?> clazz);

    int getCode();
}
