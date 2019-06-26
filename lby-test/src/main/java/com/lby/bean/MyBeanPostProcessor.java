package com.lby.bean;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

/**
 * Created by LiBingyi on 2019/6/22 14:35
 */
@Component
public class MyBeanPostProcessor implements BeanPostProcessor {
    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        if ("myDao".equals(beanName)) {
            System.out.println("MyBeanPostProcessor -> postProcessBeforeInitialization ");
        }
        return null;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if ("myDao".equals(beanName)) {
            System.out.println("MyBeanPostProcessor -> postProcessAfterInitialization " );
        }
        return null;
    }
}
