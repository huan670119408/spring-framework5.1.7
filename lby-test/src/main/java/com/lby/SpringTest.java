package com.lby;

import com.lby.bean.MyBeanDefinitionRegistryPostProcessor;
import com.lby.bean.MyDao;
import com.lby.config.AppConfig;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * Created by LiBingyi on 2019/6/24 15:41
 */
public class SpringTest {

	public static void main(String[] args){
		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
		context.register(AppConfig.class);
		context.addBeanFactoryPostProcessor(new MyBeanDefinitionRegistryPostProcessor());
		context.refresh();
		MyDao myDao = (MyDao)context.getBean("myDao");
		MyDao myService = (MyDao)context.getBean("myService");
//		System.out.println(myService.toString());
	}

}
