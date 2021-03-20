package com.jchen.rpc.socket.util;

import com.jchen.rpc.entity.RpcRequest;
import com.jchen.rpc.enumeration.PackageType;
import com.jchen.rpc.serializer.CommonSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;

/**
 * 为了Socket方式也能够使用多种序列化器，创建一个通用接口
 * Socket方式将对象序列化并写入输出流
 *
 * @Auther: jchen
 * @Date: 2021/03/20/16:40
 */
public class ObjectWriter {
    private static final Logger logger = LoggerFactory.getLogger(ObjectWriter.class);
    private static final int MAGIC_NUMBER = 0xCAFEBABE;

    public static void writeObject(OutputStream outputStream, Object object, CommonSerializer serializer) throws IOException {
        outputStream.write(intToByte(MAGIC_NUMBER));
        if (object instanceof RpcRequest) {
            outputStream.write(intToByte(PackageType.REQUEST_PACK.getCode()));
        }else {
            outputStream.write(intToByte(PackageType.RESPONSE_PACK.getCode()));
        }
        outputStream.write(intToByte(serializer.getCode()));
        byte[] bytes = serializer.serialize(object);
        outputStream.write(intToByte(bytes.length));
        outputStream.write(bytes);
        outputStream.flush();

    }

    private static byte[] intToByte(int value) {
        byte[] des = new byte[4];
        des[3] = (byte) ((value >> 24) & 0xFF);
        des[2] = (byte) ((value >> 16) & 0xFF);
        des[1] = (byte) ((value >> 8) & 0xFF);
        des[0] = (byte) (value & 0xFF);
        return des;
    }

}
