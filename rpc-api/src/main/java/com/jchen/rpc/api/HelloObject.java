package com.jchen.rpc.api;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serializable;


/**
 * @Auther: jchen
 * @Date: 2021/03/15/13:08
 */
@Data
@AllArgsConstructor
public class HelloObject implements Serializable {
    private Integer id;
    private String message;
}
