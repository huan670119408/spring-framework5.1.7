package com.lby.bean;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

/**
 * Created by LiBingyi on 2019/6/22 14:47
 */
@Service
public class MyService {

    @Autowired
    private MyDao myDao;

    public MyService(){
    	System.out.println("MyService 构造方法");
	}

    @PostConstruct
    public void postConstruct(){
    	System.out.println("MyService  @PostConstruct");
	}

	@PreDestroy
	public void preDestroy(){
		System.out.println("MyService  @PreDestroy");
	}

}
