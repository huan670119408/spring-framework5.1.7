package com.lby;

import com.lby.bean.*;
import com.lby.config.AppConfig;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * Created by LiBingyi on 2019/6/24 15:41
 */

public class SpringTest {

	public static void main(String[] args) {
		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(AppConfig.class);
		User u = (User) context.getBean("myFactoryBean");
		System.out.println(u);
		MyFactoryBean myFactoryBean = (MyFactoryBean) context.getBean("&myFactoryBean");
		System.out.println(myFactoryBean);
	}

}
