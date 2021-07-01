package com.jchen.rpc.transport.socket.util;

import com.jchen.rpc.entity.RpcRequest;
import com.jchen.rpc.enumeration.PackageType;
import com.jchen.rpc.serializer.CommonSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;

/**
 * 为了Socket方式也能够使用多种序列化器，创建一个通用类
 * Socket方式将对象序列化并写入输出流，该方式支持MRF协议，包含：
 * MAGIC_NUMBER魔数；Package Type包类型；Serializer Type序列化器；Data Length数据字节长度；Data Bytes数据内容
 * @Auther: jchen
 * @Date: 2021/03/20/16:40
 */
public class ObjectWriter {
    private static final Logger logger = LoggerFactory.getLogger(ObjectWriter.class);
    private static final int MAGIC_NUMBER = 0xCAFEBABE;

    /**
     * 根据MRF协议将object写入输出流，支持多种序列化器
     * @param outputStream 输出流
     * @param object 写入输出流的对象，至rpcRequest对象或rpcResponse对象
     * @param serializer 序列化器
     * @throws IOException
     */
    public static void writeObject(OutputStream outputStream, Object object, CommonSerializer serializer) throws IOException {
        //1.MAGIC_NUMBER魔数
        outputStream.write(intToByte(MAGIC_NUMBER));
        if (object instanceof RpcRequest) {
            //2.Package Type包类型，是请求类型还是相应类型
            outputStream.write(intToByte(PackageType.REQUEST_PACK.getCode()));
        }else {
            outputStream.write(intToByte(PackageType.RESPONSE_PACK.getCode()));
        }
        //3.Serializer Type序列化器类型
        outputStream.write(intToByte(serializer.getCode()));
        //4.Data Length序列化后数据字节长度
        byte[] bytes = serializer.serialize(object);
        outputStream.write(intToByte(bytes.length));
        //5.Data Bytes序列化后数据内容
        outputStream.write(bytes);
        outputStream.flush();

    }

    /**
     * 将int数转换为byte数组类型
     * @param value
     * @return
     */
    private static byte[] intToByte(int value) {
        byte[] src = new byte[4];
        src[0] = (byte) ((value>>24) & 0xFF);
        src[1] = (byte) ((value>>16)& 0xFF);
        src[2] = (byte) ((value>>8)&0xFF);
        src[3] = (byte) (value & 0xFF);
        return src;
    }

}
