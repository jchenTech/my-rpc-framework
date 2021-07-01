package com.jchen.rpc.api;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;


/**
 * HelloObject对象，需要实现Serializable接口，保证能序列化，它需要从客户端传递到服务端
 * @Auther: jchen
 * @Date: 2021/03/15/13:08
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class HelloObject implements Serializable {
    private Integer id;
    private String message;
}
