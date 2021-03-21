package com.jchen.rpc.enumeration;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 字节流中标识序列化和反序列化器
 *
 * @Auther: jchen
 * @Date: 2021/03/17/11:19
 */
@AllArgsConstructor
@Getter
public enum SerializerCode {

    KRYO(0),
    JSON(1),
    HESSIAN(2),
    PROTOBUF(3);

    private final int code;
}
