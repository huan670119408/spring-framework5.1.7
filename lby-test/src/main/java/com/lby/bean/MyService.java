package com.lby.bean;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Created by LiBingyi on 2019/6/22 14:47
 */
@Service
public class MyService {

    @Autowired
    private MyDao myDao;

}
