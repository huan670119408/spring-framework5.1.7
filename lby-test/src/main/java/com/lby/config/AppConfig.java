package com.lby.config;

import com.lby.bean.MyImport;
import com.lby.bean.MyImportSelector;
import com.lby.bean.User;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Created by LiBingyi on 2019/6/24 15:41
 */
@Configuration
@ComponentScan("com.lby")
@Import({MyImport.class, MyImportSelector.class})
public class AppConfig {

}
