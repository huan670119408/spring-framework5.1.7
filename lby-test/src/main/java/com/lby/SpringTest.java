package com.lby;

import com.lby.bean.*;
import com.lby.config.AppConfig;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * Created by LiBingyi on 2019/6/24 15:41
 */

public class SpringTest {

	public static void main(String[] args){
		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(AppConfig.class);
		MyImport myImport = (MyImport)context.getBean(MyImport.class.getName());
		myImport.printSomething();
		MyImportSelectorAAA myImportSelectorAAA = (MyImportSelectorAAA)context.getBean(MyImportSelectorAAA.class.getName());
		myImportSelectorAAA.printSomething();
		MyImportSelectorBBB myImportSelectorBBB = (MyImportSelectorBBB)context.getBean(MyImportSelectorBBB.class.getName());
		myImportSelectorBBB.printSomething();
	}

}
