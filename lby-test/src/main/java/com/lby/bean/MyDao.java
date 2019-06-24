package com.lby.bean;

import org.springframework.stereotype.Repository;

@Repository
public class MyDao {

    public String printSomething(String str) {
        System.out.println(str);
        return "";
    }

}
