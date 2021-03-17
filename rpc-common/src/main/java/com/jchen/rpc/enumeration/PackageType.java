package com.jchen.rpc.enumeration;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;

/**
 * @Auther: jchen
 * @Date: 2021/03/17/10:59
 */
@AllArgsConstructor
@Getter
public enum PackageType {
    REQUEST_PACK(0),
    RESPONSE_PACK(1);

    private final int code;
}
