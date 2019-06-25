package com.lby.config;

import com.lby.bean.User;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Created by LiBingyi on 2019/6/24 15:41
 */
@Configuration
@ComponentScan("com.lby")
public class AppConfig {

	@Bean
	public User getUser(){
		User user = new User();
		user.setId("1");
		user.setName("hello");
		return user;
	}

}
