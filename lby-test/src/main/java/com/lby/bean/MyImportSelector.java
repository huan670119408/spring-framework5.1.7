package com.lby.bean;

import org.springframework.context.annotation.ImportSelector;
import org.springframework.core.type.AnnotationMetadata;

/**
 * Created by LiBingyi on 2019/6/26 10:16
 */
public class MyImportSelector implements ImportSelector {

	public void printSomething(){
		System.out.println("MyImportSelector");
	}

	@Override
	public String[] selectImports(AnnotationMetadata annotationMetadata) {
		// 当然这里也可以依赖annotationMetadata的内容，执行一些特殊逻辑
		// annotationMetadata可以获得打上@Import类的元数据信息
		return new String[]{MyImportSelectorAAA.class.getName(), MyImportSelectorBBB.class.getName()};
	}
}
