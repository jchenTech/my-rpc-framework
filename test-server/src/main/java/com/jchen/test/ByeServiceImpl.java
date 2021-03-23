package com.jchen.test;

import com.jchen.rpc.annotation.Service;
import com.jchen.rpc.api.ByeService;

/**
 * @Auther: jchen
 * @Date: 2021/03/23/16:19
 */
@Service
public class ByeServiceImpl implements ByeService {

    @Override
    public String bye(String name) {
        return "bye, " + name;
    }
}
