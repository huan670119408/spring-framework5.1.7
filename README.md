[源博文](https://juejin.im/post/5d0d8642e51d45108c59a558)
## 写在前面
我对Spring原理的理解处于“点到为止”的状态：对于主线流程不太重要的逻辑跳过，或者只关注它做了什么。

学习Spring源码查找了很多资料，分享几篇大佬写的文章，对我Spring的学习起到很大帮助

[Spring IOC源码分析](https://javadoop.com/post/spring-ioc)，是以ClassPathXmlApplicationContext为例

[剑指Srping源码](https://www.cnblogs.com/CodeBear/p/10336704.html)，是以AnnotationConfigApplicationContext为例

学习Spring源码建议搭建Spring-framemwork源码环境（这是一个比较麻烦的过程，可能遇到各种问题，需要有耐心..），新建个Module打断点一步步调试。

举个例子，这是Spring的.class反编译的结果，不但没有注释，可读性也很差。
![](https://user-gold-cdn.xitu.io/2019/6/24/16b87fea916bb0eb?w=1440&h=813&f=png&s=128228)

从细节来看不同ApplicationContext生命周期略有不同，以AnnotationConfigApplicationContext和ClassPathXmlApplicationContext为例，抛开Bean的解析逻辑，比如他们俩创建BeanFactory的时机也不一样。

这篇文章我主要以spring-framework5.1.7的AnnotationConfigApplicationContext为例分析。

写这篇文章花了很多时间，如有错误，还请批评指正。

分析Spring源码是一个漫长的过程，如果在某个节点卡住，不妨先放一放，过段时间回头再看没准会茅塞顿开。另外Spring某些知识点、特殊类都可以百度一下，这些知识点的集合构成了Spring原理。

## Spring中的一些重要的类及概念
在解析源码之前，需要先弄清楚Spring中比较重要的类，在源码中可能经常遇到，如果不清楚是做什么的会很懵，这块有个大致印象即可。
### ApplicationContext

ApplicationContext就是加载Spring容器的入口，Spring的一切从这里开始。

基于加载配置方式的不同，常见的有：

#### AnnotationConfigApplicationContext
基于注解获取配置也是本文会详细说明的。

#### ClassPathXmlApplicationContext、FileSystemXmlApplicationContext

基于XML获取配置，指定配置文件路径来获取配置。
#### XmlWebApplicationContext

在SpringBoot以前，我们的Web应用要配置Spring，通常是在web.xml配置listener和applicationContext.xml
```
<listener>  
     <listener-class>org.springframework.web.context.ContextLoaderListener
     </listener-class>  
</listener> 
<context-param>  
    <param-name>contextConfigLocation</param-name>  
    <param-value>classpath:applicationContext.xml</param-value>  
</context-param> 
```
这时web应用依据加载web.xml的顺序，ContextLoaderListener监听到ServletContext加载事件，如果跟踪源码最终默认会取到
Spring内置如下配置

![](https://user-gold-cdn.xitu.io/2019/6/22/16b7d26f4a5535d3?w=1249&h=556&f=png&s=66109)
为我们创建一个XmlWebApplicationContext实例。

#### 继承结构

![](https://user-gold-cdn.xitu.io/2019/6/22/16b7d334de73077e?w=770&h=448&f=png&s=35207)

另外从继承结构可以看出，Xml和Annotation在AbstractApplicationContext处开始是两个分支，其实就是由于继承结构的不同，在容器创建的一些节点上，执行的是同一个接口方法的不同实例方法，从而一些实现逻辑略有不同。这里先有个大致印象就行。
### BeanDefinition
就是用来描述Bean定义的类。在Java中每个类都有个Class对象，类似的，Spring中每个我们配置的Bean都有个BeanDefinition对象。
比如Bean对应的类、是否懒加载、作用域等都在这里描述。
```
public interface BeanDefinition extends AttributeAccessor, BeanMetadataElement {
    
	// ... 简单截取一部分
	
	
	// 设置 Bean 的类名称，将来是要通过反射来生成实例的
	void setBeanClassName(@Nullable String beanClassName);

	// 获取 Bean 的类名称
	@Nullable
	String getBeanClassName();

	// scope
	void setScope(@Nullable String scope);

	// scope
	@Nullable
	String getScope();

	// 懒加载
	void setLazyInit(boolean lazyInit);

	// 是否懒加载
	boolean isLazyInit();

	// depends-on="" 属性设置的值。
	// @DependsOn
	void setDependsOn(@Nullable String... dependsOn);

	// @DependsOn
	@Nullable
	String[] getDependsOn();

	// 设置该 Bean 是否可以注入到其他 Bean 中，只对根据类型注入有效，
	// 如果根据名称注入，即使这边设置了 false，也是可以的
	void setAutowireCandidate(boolean autowireCandidate);

	// 该 Bean 是否可以注入到其他 Bean 中
	boolean isAutowireCandidate();

	// @Primary
	void setPrimary(boolean primary);

	// 是否是 primary
	boolean isPrimary();

	// FactoryBean
	void setFactoryBeanName(@Nullable String factoryBeanName);

	// FactoryBean
	@Nullable
	String getFactoryBeanName();

	// ... 后面太长就不写了

}
```

### BeanFactory
BeanFactory就是生产Bean的工厂。BeanFactory的最终目的就是为我们产生Bean实例，只不过在产生Bean实例之前需要解析注解或XML，产生BeanDefinition并注册到BeanFactory。我们常说的Spring容器中的Bean，一方面是指Bean实例，一方面也是指Bean定义，我觉得都不算错。

### Spring容器
上面提到BeanFactory，可能会联想到Spring容器。

宏观上看，BeanFactory、ApplicationContext、包括后面会提到的DefaultListableBeanFactory、DefaultSingletonBeanRegistry，都可以算作Spring容器，或者他们的集合是Spring容器。这个容器一方面存储了Bean实例，另一方面也存储了Bean的定义。

微观上看，容器是存储我们Bean定义的是DefaultListableBeanFactory的一个map属性、存储Bean实例的是DefaultSingletonBeanRegistry的一个map属性，都是map。
### DefaultListableBeanFactory
我们具体操作的BeanFactory实例就是DefaultListableBeanFactory类型的。
这里列出DefaultListableBeanFactory两个重要的属性，就是用来存储BeanDefinition的容器。
```
//  bean定义的映射，以name为key
private final Map<String, BeanDefinition> beanDefinitionMap = new ConcurrentHashMap<>(256);

// bean定义名字List，以注册顺序排序
private volatile List<String> beanDefinitionNames = new ArrayList<>(256);
```

### FactoryBean
通过实现FactoryBean，会为我们实例化两个Bean。

一个是当前Bean，只不过name前加了个&，标识出这个是FactoryBean的实例

一个是基于接口方法返回的实例来为我们创建Bean实例。

FactoryBean可以将一些复杂依赖的类合并成一个对象返回，可以用于封装。

典型应用：mybatis的SqlSessionFactoryBean

使用示例：
```
@Component
public class MyFactoryBean implements FactoryBean {

	@Override
	public Object getObject() throws Exception {
		return new User();
	}

	@Override
	public Class<?> getObjectType() {
		return User.class;
	}

	@Override
	public boolean isSingleton() {
		return true;
	}
}
```
```
public class User {

	private String id;

	private String name;

	public User(){
		id = "001";
		name = "lby";
	}

}
```
功能验证
```
public class SpringTest {

	public static void main(String[] args) {
		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(AppConfig.class);
		User u = (User) context.getBean("myFactoryBean");
		MyFactoryBean myFactoryBean = (MyFactoryBean) context.getBean("&myFactoryBean");
	}
	
}
```
输出结果

![](https://user-gold-cdn.xitu.io/2019/6/27/16b983bd6930f0ee?w=1176&h=263&f=png&s=25685)

### BeanPostProcessor
后置处理器，他有两个方法postProcessBeforeInitialization和postProcessAfterInitialization。

Bean在实例化之后、初始化前后会执行。

这只是个笼统的说法。所谓初始化就是调用Spring的InitMethods方法。

初始化Spring的Bean有三种方式：

init-method，可以在XML里配置也可以在java config里配置

实现InitializingBean接口

@PostConstruct注解，解析执行该注解的本身就是一个BeanPostProcessor，所以Spring的InitMethods不包括@PostConstruct

看过Bean的实例化部分的源码会发现，Bean在实例化之后这三种初始化方式的执行顺序是

@PostConstruct -> InitializingBean -> init-method

解析@PostConstruct注解的是CommonAnnotationBeanPostProcessor后置处理器

而后置处理器是在InitMethods的前后执行。而InitMethods里的执行顺序是先执行InitializingBean后执行init-method，所以最终是这个执行顺序。

不过按现在编程风格，一般都用@PostConstruct，另外两个不常用

典型应用：CommonAnnotationBeanPostProcessor，就是解析@PostConstruct的后置处理器

使用示例：
```
@Component
public class MyBeanPostProcessor implements BeanPostProcessor {
    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        if ("myDao".equals(beanName)) {
            System.out.println("BeforeInitialization " + beanName + "...");
        }
        return null;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if ("myDao".equals(beanName)) {
            System.out.println("AfterInitialization " + beanName + "...");
        }
        return null;
    }
}
```
### BeanFactoryPostProcessor
后置处理器，BeanFactoryPostProcessor可以在bean实例化之前可以读取bean的定义并修改它。同时可以定义多个BeanFactoryPostProcessor，通过设置@Order来确定各个BeanFactoryPostProcessor执行顺序

典型应用：ConfigrationClassPostProcessor，这是解析Bean的核心，后面会有解释

使用示例
```
@Component
public class MyBeanFactoryPostProcessor implements BeanFactoryPostProcessor {
    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory configurableListableBeanFactory) 
    throws BeansException {
        BeanDefinition bd = configurableListableBeanFactory.getBeanDefinition("myService");
        bd.setBeanClassName("com.spring.bean.MyDao");
    }
}
```
这里我们将myService的class由com.spring.bean.MyService改为com.spring.bean.MyDao，
```
public class SpringTest {

    public static void main(String[] args){
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(Config.class);
        ClassPathXmlApplicationContext context1 = new ClassPathXmlApplicationContext();
        MyDao myDao = (MyDao)context.getBean("myDao");
        MyDao myService = (MyDao)context.getBean("myService");
        System.out.println(myService.toString());
    }

}
```
再将myService的实例输出出来，结果已经变成com.spring.bean.MyDao的实例

![](https://user-gold-cdn.xitu.io/2019/6/22/16b7e54c9b3e355e?w=1377&h=308&f=png&s=66488)

### BeanDefinitionRegistryPostProcessor
后置处理器，它继承了BeanFactoryPostProcessor，可以认为是特别的BeanFactoryPostProcessor，通过实现BeanDefinitionRegistryPostProcessor可以介入容器的初始化过程，他的调用时机与BeanFactoryPostProcessor一样，只不过优先于BeanFactoryPostProcessor调用。

典型应用：ConfigrationClassPostProcessor

使用示例：

```
@Component
public class MyBeanDefinitionRegistryPostProcessor implements BeanDefinitionRegistryPostProcessor {

	@Override
	public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
		System.out.println("MyBeanDefinitionRegistryPostProcessor  -> postProcessBeanFactory");
	}

	@Override
	public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
		String[] beanDefinitionNames = registry.getBeanDefinitionNames();
		for (String name : beanDefinitionNames) {
			System.out.println("MyBeanDefinitionRegistryPostProcessor  -> " + name);
		}
	}
}
```


![](https://user-gold-cdn.xitu.io/2019/6/25/16b8c643b1ae9988?w=596&h=239&f=png&s=24128)

可以看出BeanDefinitionRegistryPostProcessor要先于BeanFactoryPostProcessor执行，postProcessBeanDefinitionRegistry方法要先于postProcessBeanFactory执行。

至于为什么是这个结果，如果看了源码，实际上Spring内部的调用顺序就是这样。

### ConfigurationClassPostProcessor
它继承了BeanDefinitionRegistryPostProcessor，所以他也是后置处理器。在Spring解析Bean的过程中，他是最重要的也是第一个执行的BeanFactoryPostProcessor，我们的自定义的@Compent、@Bean、@Import等都是由这个类开始解析并注册的。


### ImportSelector
是一个接口，功能上可以简单把它理解为动态版的@Import

典型应用：@EnableTransactionManagement

使用示例：
```
public class MyImport {

    public void printSomething() {
        System.out.println("MyImport");
    }

}

public class MyImportSelector implements ImportSelector {

	public void printSomething(){
		System.out.println("MyImportSelector");
	}

	@Override
	public String[] selectImports(AnnotationMetadata annotationMetadata) {
		// 当然这里也可以依赖annotationMetadata的内容，执行一些特殊逻辑
		// annotationMetadata可以获得打上@Import类的元数据信息
		return new String[]{MyImportSelectorAAA.class.getName(), 
		MyImportSelectorBBB.class.getName()};
	}
}

public class MyImportSelectorAAA {

    public void printSomething(){
        System.out.println("MyImportSelectorAAA");
    }

}

public class MyImportSelectorBBB {

    public void printSomething(){
        System.out.println("MyImportSelectorAAA");
    }

}
```
JavaConfig
```
@Configuration
@ComponentScan("com.lby")
@Import({MyImport.class, MyImportSelector.class})
public class AppConfig {

}
```
功能验证
```
public class SpringTest {

	public static void main(String[] args){
		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(AppConfig.class);
		MyImport myImport = (MyImport)context.getBean(MyImport.class.getName());
		myImport.printSomething();
		MyImportSelectorAAA myImportSelectorAAA =
		(MyImportSelectorAAA)context.getBean(MyImportSelectorAAA.class.getName());
		myImportSelectorAAA.printSomething();
		MyImportSelectorBBB myImportSelectorBBB =
		(MyImportSelectorBBB)context.getBean(MyImportSelectorBBB.class.getName());
		myImportSelectorBBB.printSomething();
	}

}
```
输出结果
![](https://user-gold-cdn.xitu.io/2019/6/26/16b91bb98ae74bb1?w=583&h=258&f=png&s=21887)
可以看到上面Imoprt的类已经成功被实例化。

MyImport是普通类，MyImportSelector实现了ImportSelector接口，重写selectImports方法返回MyImportSelectorAAA、MyImportSelectorBBB

@Import(MyImport.class)是直接将引入的MyImport注册到BeanFactory

@Import(MyImportSelector.class)则是将MyImportSelectorAAA、MyImportSelectorBBB注册到BeanFactory，但MyImportSelector本身不会注册。

### ImportBeanDefinitionResgistor  
典型应用：@EnableAspectAutoProxy

### ApplicationContextAware

## IOC容器的初始化
IOC容器的初始化我这里粗略的分成三步走：

1、BeanFactory的准备工作

2、Bean的解析

3、Bean的实例化

这三步可以说一步比一步难，先有个心理准备。
### BeanFactory的准备工作
从AnnotationConfigApplicationContext开始，走进SpringIOC容器
```
AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(Config.class);
```
```
public AnnotationConfigApplicationContext(Class<?>... annotatedClasses) {
	// 构造方法
	this();
	// 注册类，所谓注册，把它理解为把这个bean放到容器里即可
	register(annotatedClasses);
	// 刷新容器的内容，核心方法
	// 不论是AnnotationConfigApplicationContext还是ClassPathXmlApplicationContext都会走这同一个方法
	refresh();
}
```
我们先看构造方法，
```
public AnnotationConfigApplicationContext() {
    // 重点，从类名字上可以看出是注解式bean定义读取器
    this.reader = new AnnotatedBeanDefinitionReader(this);
    // 不是重点，忽略
    this.scanner = new ClassPathBeanDefinitionScanner(this);
}
```
调用子类构造方法前会调用父类构造方法。AnnotationConfigApplicationContext继承链路上有很多类，我们重点关注GenericApplicationContext的构造方法
```
public GenericApplicationContext() {
    // 实例化了一个BeanFactory
    this.beanFactory = new DefaultListableBeanFactory();
}
```
可以看到AnnotationConfigApplicationContext的第一步就是通过构造方法为我们实例化一个空的BeanFactory即DefaultListableBeanFactory实例，我们常说的BeanFactory一般就是DefaultListableBeanFactory实例。

我们看一下DefaultListableBeanFactory的成员属性
```
//  是否允许bean覆盖
private boolean allowBeanDefinitionOverriding = true;

// 是否允许即使是懒加载的Bean立刻加载
private boolean allowEagerClassLoading = true;

// 比较器
@Nullable
private Comparator<Object> dependencyComparator;

// 用于检查一个类定义是否有自动注入的解析器
private AutowireCandidateResolver autowireCandidateResolver = new SimpleAutowireCandidateResolver();

// 依赖的类与实例的map映射
private final Map<Class<?>, Object> resolvableDependencies = new ConcurrentHashMap<>(16);

//  bean定义的映射，以name为key
private final Map<String, BeanDefinition> beanDefinitionMap = new ConcurrentHashMap<>(256);

//  单例和非单例bean名称映射，以类型为key
private final Map<Class<?>, String[]> allBeanNamesByType = new ConcurrentHashMap<>(64);

//  单例的bean名称映射，以类型为key
private final Map<Class<?>, String[]> singletonBeanNamesByType = new ConcurrentHashMap<>(64);

// bean定义名字List，以注册顺序排序
private volatile List<String> beanDefinitionNames = new ArrayList<>(256);

// 人工注册的单例bean定义的有序Set，以注册顺序排序
private volatile Set<String> manualSingletonNames = new LinkedHashSet<>(16);

// 缓存bean定义名字的数组
@Nullable
private volatile String[] frozenBeanDefinitionNames;

// 是否允许bean定义的元数据被缓存，以用于所有的bean
private volatile boolean configurationFrozen = false;
```
可以说DefaultListableBeanFactory实例就是我们的BeanDefinition容器。更具体点就是beanDefinitionMap。

回到这里
```
public AnnotationConfigApplicationContext() {
    // 重点，从类名字上可以看出是注解式bean定义读取器
    this.reader = new AnnotatedBeanDefinitionReader(this);
    // 不是重点，忽略
    this.scanner = new ClassPathBeanDefinitionScanner(this);
}
```
调用父类的构造方法生成DefaultListableBeanFactory对象之后，重点就是
```
this.reader = new AnnotatedBeanDefinitionReader(this);
```
顺着调用链路一路下钻，只挑重点，执行到了AnnotationConfigUtils类的registerAnnotationConfigProcessors方法
```
AnnotationConfigUtils.registerAnnotationConfigProcessors(this.registry);
```

这里BeanDefinitionRegistry类型的registry就是我们的当前实例。前面通过构造方法实例化了GenericApplicationContext对象，而GenericApplicationContext实现了BeanDefinitionRegistry接口，相当于BeanDefinitionRegistry的子类。
```
public static Set<BeanDefinitionHolder> registerAnnotationConfigProcessors(
		BeanDefinitionRegistry registry, @Nullable Object source) {
	// 不是重点
	// 其实就是为我们返回当前GenericApplicationContext实例(registry)的DefaultListableBeanFactory类型beanFactory属性
	DefaultListableBeanFactory beanFactory = unwrapDefaultListableBeanFactory(registry);
	if (beanFactory != null) {// 不是重点，前面刚实例化的DefaultListableBeanFactory，这里必不为空
		if (!(beanFactory.getDependencyComparator() instanceof AnnotationAwareOrderComparator)) {
			// 设置比较器，往后看就知道排序器是用来干啥的了
			// 其实这里就算不初始化比较器也没事，后面比较的地方也会有默认的比较器
			beanFactory.setDependencyComparator(AnnotationAwareOrderComparator.INSTANCE);
		}
		if (!(beanFactory.getAutowireCandidateResolver() instanceof ContextAnnotationAutowireCandidateResolver)){
			// 设置自动注入的解析器
			beanFactory.setAutowireCandidateResolver(new ContextAnnotationAutowireCandidateResolver());
		}
	}
	Set<BeanDefinitionHolder> beanDefs = new LinkedHashSet<>(8);
	if (!registry.containsBeanDefinition(CONFIGURATION_ANNOTATION_PROCESSOR_BEAN_NAME)) {
		// 注册ConfigurationClassPostProcessor后置处理器
		// ConfigurationClassPostProcessor是非常重要的类
		RootBeanDefinition def = new RootBeanDefinition(ConfigurationClassPostProcessor.class);
		def.setSource(source);
		beanDefs.add(registerPostProcessor(registry, def, CONFIGURATION_ANNOTATION_PROCESSOR_BEAN_NAME));
	}
	if (!registry.containsBeanDefinition(AUTOWIRED_ANNOTATION_PROCESSOR_BEAN_NAME)) {
		// 注册AutowiredAnnotationBeanPostProcessor后置处理器
		RootBeanDefinition def = new RootBeanDefinition(AutowiredAnnotationBeanPostProcessor.class);
		def.setSource(source);
		beanDefs.add(registerPostProcessor(registry, def, AUTOWIRED_ANNOTATION_PROCESSOR_BEAN_NAME));
	}
	// Check for JSR-250 support, and if present add the CommonAnnotationBeanPostProcessor.
	if (jsr250Present && !registry.containsBeanDefinition(COMMON_ANNOTATION_PROCESSOR_BEAN_NAME)) {
		// JSR-250一种规范，需要支持@Resource、@PostConstruct以及@PreDestroy注解
		// CommonAnnotationBeanPostProcessor后置处理器就是处理这些注解的
		RootBeanDefinition def = new RootBeanDefinition(CommonAnnotationBeanPostProcessor.class);
		def.setSource(source);
		beanDefs.add(registerPostProcessor(registry, def, COMMON_ANNOTATION_PROCESSOR_BEAN_NAME));
	}
	// Check for JPA support, and if present add the PersistenceAnnotationBeanPostProcessor.
	if (jpaPresent && !registry.containsBeanDefinition(PERSISTENCE_ANNOTATION_PROCESSOR_BEAN_NAME)) {
		// JPA支持，注册  PersistenceAnnotationProcessor后置处理器
		RootBeanDefinition def = new RootBeanDefinition();
		try {
			def.setBeanClass(ClassUtils.forName(PERSISTENCE_ANNOTATION_PROCESSOR_CLASS_NAME,
					AnnotationConfigUtils.class.getClassLoader()));
		}
		catch (ClassNotFoundException ex) {
			...
		}
		def.setSource(source);
		beanDefs.add(registerPostProcessor(registry, def, PERSISTENCE_ANNOTATION_PROCESSOR_BEAN_NAME));
	}
	if (!registry.containsBeanDefinition(EVENT_LISTENER_PROCESSOR_BEAN_NAME)) {
		// 注册 EventListenerProcessor后置处理器
		RootBeanDefinition def = new RootBeanDefinition(EventListenerMethodProcessor.class);
		def.setSource(source);
		beanDefs.add(registerPostProcessor(registry, def, EVENT_LISTENER_PROCESSOR_BEAN_NAME));
	}
	if (!registry.containsBeanDefinition(EVENT_LISTENER_FACTORY_BEAN_NAME)) {
		// 注册 EventListenerFactory
		RootBeanDefinition def = new RootBeanDefinition(DefaultEventListenerFactory.class);
		def.setSource(source);
		beanDefs.add(registerPostProcessor(registry, def, EVENT_LISTENER_FACTORY_BEAN_NAME));
	}
	return beanDefs;
}
```
registerAnnotationConfigProcessors主要就是为我们注册了一些Spring默认的后置处理器。这里所谓的注册，主要就是将这些后置处理器转换成BeanDefinition对象，并存入前面我们实例化的Bean工厂中（DefaultListableBeanFactory对象的beanDefinitionMap、beanDefinitionNames属性）。

此处注册了这几个后置处理器

ConfigurationClassPostProcessor

AutowiredAnnotationBeanPostProcessor

CommonAnnotationBeanPostProcessor

PersistenceAnnotationProcessor

EventListenerProcessor

EventListenerFactory

回到我们前面AnnotationConfigApplicationContext的构造方法
```
// AnnotationConfigApplicationContext类的AnnotationConfigApplicationContext(Class<?>... annotatedClasses)
public AnnotationConfigApplicationContext(Class<?>... annotatedClasses) {
	// 构造方法
	this();
	// 注册类，所谓注册，把它理解为把这个bean放到容器里即可
	register(annotatedClasses);
	// 刷新，核心方法
	// 不论是AnnotationConfigApplicationContext还是ClassPathXmlApplicationContext都会走这同一个方法
	// AnnotationConfigApplicationContext继承自GenericApplicationContext
	// GenericApplicationContext只能refresh一次 否则会抛异常
	refresh();
}
```
无参构造方法this主要流程已经走完，register(annotatedClasses)其实就是把我们传入的Bean，比如将我们的JavaConfig注册到BeanFactory中而已。

至此，三步走中最简单的一步：BeanFactory准备工作，大致流程已经走完。

总结下，核心逻辑就是为我们实例化BeanFactory，并将内置的几个重要的后置处理器注册到BeanFactory中。这里算是个入门，重中之重是后面的refresh方法。


### Bean的解析
查看refresh方法
```
@Override
public void refresh() throws BeansException, IllegalStateException {
	synchronized (this.startupShutdownMonitor) {
		// 不是重点，容器刷新前的准备工作，记录下容器的启动时间、标记“已启动”状态、close状态等。
		prepareRefresh();
		// 获取我们的beanFactory，这里Annotation版和XML版是不同的实现
		// Annotation版，因为我们在实例化AnnotationConfigApplicationContext时已经创建了beanFactory
		// 所以这里的辑仅仅是获取而已
		// 但如果是XML版，是在这个环节创建beanFactory
		ConfigurableListableBeanFactory beanFactory = obtainFreshBeanFactory();
		// BeanFactory的准备工作
		// 主要是加了两个后置处理器：ApplicationContextAwareProcessor，ApplicationListenerDetector
		// 一些依赖的略，注册一些内置bean等
		// 注意这里是添加后置处理器是add到BeanFactory（AbstractBeanFactory类的beanPostProcessors属性）
		// 而前面我们在实例化BeanFactory时是注册了几个后置处理器，将后置处理器转换为BeanDefinition放入BeanFactory中
		// 这里没必要太纠结
		// 当前只是Spring将一分部后置处理器转换成BeanDefine注册到BeanFactory，一部分是直接add到BeanFactory
		// 后面都会统一add到BeanFactory的beanPostProcessors属性
		prepareBeanFactory(beanFactory);
		try {
			// 这里现在是空方法，实际上是提供给子类的扩展点，子类可以重写该方法。
			postProcessBeanFactory(beanFactory);
			// 重点，执行BeanFactoryProcessor各个实现类的 postProcessBeanFactory方法
			// 执行内置的和我们手动添加的BeanFactoryPostProcessors
			// 手动添加的是需要手动调用annotationConfigApplicationContext.addBeanFactoryPostProcessor来添加
			// 那内置的呢？回想我们前面BeanFactory初始化spring为我们加了很多后置处理器
			// 其中ConfigurationClassPostProcessor就是继承自BeanFactoryPostProcessor
			// ConfigurationClassPostProcessor是重点！
			invokeBeanFactoryPostProcessors(beanFactory);
			// 注册 BeanPostProcessor 的实现类
			// 所有的BeanPostProcessor统一add到BeanFactory的beanPostProcessors属性，准备执行
			registerBeanPostProcessors(beanFactory);
			// 不是重点，忽略。
			initMessageSource();
			// 初始化当前 ApplicationContext 的事件广播器
			initApplicationEventMulticaster();
			// 和上面postProcessBeanFactory类似，也是空方法，提供给子类的扩展点
			onRefresh();
			// 不是重点。注册事件监听器，监听器需要实现 ApplicationListener 接口。
			registerListeners();
			// 重点，初始化所有的 singleton beans
			finishBeanFactoryInitialization(beanFactory);
			// 最后，广播事件，ApplicationContext 初始化完成
			finishRefresh();
		}
		catch (BeansException ex) {
			...
		}
		finally {
			...
		}
	}
}
```
refresh是宏观上的处理流程，我们重点关注这几个方法，其他的先忽略:

* ConfigurableListableBeanFactory beanFactory = obtainFreshBeanFactory();
* invokeBeanFactoryPostProcessors(beanFactory);
* registerBeanPostProcessors
* finishBeanFactoryInitialization(beanFactory);

这里我们划分一下，前两个方法属于Bean的解析过程，后两个方法属于Bean的实例化过程。

#### obtainFreshBeanFactory()
顺着调用链路会执行到AbstractApplicationContext类的refreshBeanFactory方法。
![](https://user-gold-cdn.xitu.io/2019/6/24/16b875f8f5516ad6?w=953&h=406&f=png&s=167822)
refreshBeanFactory是抽象方法，不同的实例执行不同的实现。

回头看上面ApplicationContext的继承结构，
因为我们的AnnotationConfigApplicationContext是GenericApplicationContext的子类，所以应该走GenericApplicationContext类的refreshBeanFactory方法，他的逻辑比较简单，就是获取前面BeanFactory准备工作实例化的DefaultListableBeanFactory实例。

而如果是ClassPathXmlApplicationContext，会执行AbstractRefreshableApplicationContext的refreshBeanFactory方法，它的DefaultListableBeanFactory实例是在这里创建的，这里就不详细阐述了。而这也是Annotation和XML区别之一。
#### invokeBeanFactoryPostProcessors(beanFactory)
只看主线，顺着调用链路会执行到如下方法
```
public static void invokeBeanFactoryPostProcessors(
		ConfigurableListableBeanFactory beanFactory, List<BeanFactoryPostProcessor> beanFactoryPostProcessors) {

	// 一个用于判断的容器而已，凡是已经执行过的后置处理器都放入这里
	Set<String> processedBeans = new HashSet<>();
	// 我们的BeanFactory实际是DefaultListableBeanFactory实例
	// 而DefaultListableBeanFactory实现了BeanDefinitionRgistry，所以是True
	if (beanFactory instanceof BeanDefinitionRegistry) {
		BeanDefinitionRegistry registry = (BeanDefinitionRegistry) beanFactory;
		// 用来存放BeanFactoryPostProcessor
		List<BeanFactoryPostProcessor> regularPostProcessors = new ArrayList<>();
		// 用来存放BeanDefinitionRegistryPostProcessor
		// BeanDefinitionRegistryPostProcessor继承了用来存放BeanFatoryPostProcessor
		// 这两个集合就是做了个区分，后面逻辑需要
		List<BeanDefinitionRegistryPostProcessor> registryProcessors = new ArrayList<>();
		// 为我们手动添加的beanFactoryPostProcessors做区分存入上面两个集合里
		// 除了用注解，我们可以这么添加BeanFactoryPostProcessor 
		// context.addBeanFactoryPostProcessor(newMyBeanDefinitionRegistryPostProcessor());
		// 不过此处一般情况下为空，我觉得可以跳过
		for (BeanFactoryPostProcessor postProcessor : beanFactoryPostProcessors) {
			if (postProcessor instanceof BeanDefinitionRegistryPostProcessor) {
				BeanDefinitionRegistryPostProcessor registryProcessor =
						(BeanDefinitionRegistryPostProcessor) postProcessor;
				registryProcessor.postProcessBeanDefinitionRegistry(registry);
				registryProcessors.add(registryProcessor);
			}
			else {
				regularPostProcessors.add(postProcessor);
			}
		}

		// 就是个临时变量而已，后面的逻辑共用这个变量
		List<BeanDefinitionRegistryPostProcessor> currentRegistryProcessors = new ArrayList<>();
		// 下面方法过长，我们划分一下
		// 从这开始算是第一步，执行实现了PriorityOrdered接口的BeanDefinitionRegistrPostProcessor
		
		// 首先，调用实现PriorityOrdered的BeanDefinitionRegistryPostProcessors
		// PriorityOrdered即优先排序，是个接口，继承自Ordered接口
		// 而ConfigurationClassPostProcessor实现了PriortyOrdered接口
		// Ordered接口就是返回数量而已，用于排序，从而影响后置处理器的执行顺序，其实我们理解为一个标记接口即可
		// 就是从我们的beanFactory的beanDefinitionNames中找到类型是BeanDefinitionRegistryPostProcessor的后置处器
		String[] postProcessorNames =
				beanFactory.getBeanNamesForType(BeanDefinitionRegistryPostProcessor.class, true, false);
		// 当前postProcessorNames中只有ConfigurationClassPostProcessor
		// 因为前面注册的几个后置处理器只有ConfigurationClassPostProcessor是BeanDefinitionRegistryPostProcesor
		for (String ppName : postProcessorNames) {
			if (beanFactory.isTypeMatch(ppName, PriorityOrdered.class)) {
				// 返回一个ConfigurationClassPostProcessor实例放入currentRegistryProcessors中
				// beanFactory.getBean为我们返回实例，是个很重要的方法，这个之后详说
				// 这里看到在真正给所有的Bean实例化前，实际上已经有Spring内置处理器先实例化了
				currentRegistryProcessors.add(
				beanFactory.getBean(ppName,BeanDefinitionRegistryPostProcessor.class));
				processedBeans.add(ppName);
			}
		}
		// 不是重点，可以跳过
		// 就是给currentRegistryProcessors排序当前我们这里只有一个ConfigurationClassPostProcessor实例
		// 其实里面的实现很简单，就是基于order的值排序
		sortPostProcessors(currentRegistryProcessors, beanFactory);
		// BeanDefinitionRegistryPostProcessors的实现都放这里
		registryProcessors.addAll(currentRegistryProcessors);
		// 执行ConfigurationClassPostProcessor的postProcessBeanDefinitionRegistry方法
		invokeBeanDefinitionRegistryPostProcessors(currentRegistryProcessors, registry);
		// 清空
		currentRegistryProcessors.clear();
		// 从这里算是第二步，执行实现了Ordered接口且前面未执行过的BeanDefinitionRegistryPostProcessor
		// Next, invoke the BeanDefinitionRegistryPostProcessors that implement Ordered.
		// 再次从我们的beanFactory的beanDefinitionNames中找到类型是BeanDefinitionRegistryPostProcessor的后置处器
		// 又获取一次，前面其实刚获取到一次，参数一模一样，之所以又取出一遍，因为前面执行过后置处理器，可能有变动
		postProcessorNames = beanFactory.
		getBeanNamesForType(BeanDefinitionRegistryPostProcessor.class, true,false);
		for (String ppName : postProcessorNames) {
			// 获取未处理的且实现Ordered接口的后置处理器
			if (!processedBeans.contains(ppName) && beanFactory.isTypeMatch(ppName, Ordered.class)) {
				currentRegistryProcessors.add(
				beanFactory.getBean(ppName,BeanDefinitionRegistryPostProcessor.class));
				processedBeans.add(ppName);
			}
		}
		sortPostProcessors(currentRegistryProcessors, beanFactory);
		// BeanDefinitionRegistryPostProcessor的实现都放入registryProcessors
		registryProcessors.addAll(currentRegistryProcessors);
		// 执行我们自定义的BeanDefinitionRegistryPostProcessor
		invokeBeanDefinitionRegistryPostProcessors(currentRegistryProcessors, registry);
		// 清空
		currentRegistryProcessors.clear();
		// 这里算第三步，执行剩余的BeanDefinitionRegistryPostProcessor
		// 也就是没有实现Ordered接口的BeanDefinitinRegistryPostProcessor
		// Finally, invoke all other BeanDefinitionRegistryPostProcessors until no further ones appear.
		// 最后，调用所有其他BeanDefinitionRegistryPostProcessors
		boolean reiterate = true;
		while (reiterate) {
			reiterate = false;
			postProcessorNames = 
			beanFactory.getBeanNamesForType(BeanDefinitionRegistryPostProcessor.class,true, false);
			for (String ppName : postProcessorNames) {
				if (!processedBeans.contains(ppName)) {
					currentRegistryProcessors.add(
					beanFactory.getBean(ppName,BeanDefinitionRegistryPostProcessor.class));
					processedBeans.add(ppName);
					reiterate = true;
				}
			}
			sortPostProcessors(currentRegistryProcessors, beanFactory);
			registryProcessors.addAll(currentRegistryProcessors);
			invokeBeanDefinitionRegistryPostProcessors(currentRegistryProcessors, registry);
			currentRegistryProcessors.clear();
		}
		// Now, invoke the postProcessBeanFactory callback of all processors handled so far.
		// 现在，调用到目前为止处理的所有处理器的postProcessBeanFactory回调
		// 就是执行BeanFactoryPostProcessors的回调方法，前面都是执行BeanDefinitionRegistryPostProcessor的回调
		// 到这其实就能明白前面为什么要区分registryProcessors，regularPostProcessors
		// 其实就是做了一个区分而已，然后统一调用postProcessBeanFactory回调
		// regularPostProcessor表示未继承BeanDefinitionRegistryPostProcessor
		// registryProcessors表示继承了BeanDefinitionRegistryPostProcessor，只有我们自定义add的这里才会有
		// 前面几个步骤只执行了BeanDefinitionRegistryPostProcessor的回调
		// 但还没有执行BeanFactoryPostProcessor的回调，此处执行
		invokeBeanFactoryPostProcessors(registryProcessors, beanFactory);
		invokeBeanFactoryPostProcessors(regularPostProcessors, beanFactory);
	}
	else { // 跳过吧，不确定什么情况会走这条线
		// Invoke factory processors registered with the context instance.
		invokeBeanFactoryPostProcessors(beanFactoryPostProcessors, beanFactory);
	}

	// 前面取的是实现BeanDefinitionRegistryPostProcessor的后置处理器，这里获取实现BeanFactoryPostProcessor的后处理器
	String[] postProcessorNames =
			beanFactory.getBeanNamesForType(BeanFactoryPostProcessor.class, true, false);

	// 以下3个List用于区分而已：实现PriorityOrdered的、实现Ordered、未实现的后置处理器
	List<BeanFactoryPostProcessor> priorityOrderedPostProcessors = new ArrayList<>();
	List<String> orderedPostProcessorNames = new ArrayList<>();
	List<String> nonOrderedPostProcessorNames = new ArrayList<>();
	for (String ppName : postProcessorNames) {
		if (processedBeans.contains(ppName)) {
			// skip - already processed in first phase above
			// 前面执行过的跳过
		}
		else if (beanFactory.isTypeMatch(ppName, PriorityOrdered.class)) {
			priorityOrderedPostProcessors.add(beanFactory.getBean(ppName, BeanFactoryPostProcessor.class));
		}
		else if (beanFactory.isTypeMatch(ppName, Ordered.class)) {
			orderedPostProcessorNames.add(ppName);
		}
		else {
			nonOrderedPostProcessorNames.add(ppName);
		}
	}

	// 这里跟前面处理BeanDefinitionRegistryPostProcessor类似也分三步走
	// 第一步执行实现PriorityOrdered的
	sortPostProcessors(priorityOrderedPostProcessors, beanFactory);
	invokeBeanFactoryPostProcessors(priorityOrderedPostProcessors, beanFactory);

	// 第二步执行实现Ordered
	List<BeanFactoryPostProcessor> orderedPostProcessors = new ArrayList<>();
	for (String postProcessorName : orderedPostProcessorNames) {
		orderedPostProcessors.add(beanFactory.getBean(postProcessorName, BeanFactoryPostProcessor.class));
	}
	sortPostProcessors(orderedPostProcessors, beanFactory);
	invokeBeanFactoryPostProcessors(orderedPostProcessors, beanFactory);

	// 第三步执行未排序的
	List<BeanFactoryPostProcessor> nonOrderedPostProcessors = new ArrayList<>();
	for (String postProcessorName : nonOrderedPostProcessorNames) {
		nonOrderedPostProcessors.add(beanFactory.getBean(postProcessorName, BeanFactoryPostProcessor.class));
	}
	invokeBeanFactoryPostProcessors(nonOrderedPostProcessors, beanFactory);
	// Clear cached merged bean definitions since the post-processors might have
	// modified the original metadata, e.g. replacing placeholders in values...
	// 存疑
	beanFactory.clearMetadataCache();
}
```
invokeBeanFactoryPostProcessors这个方法虽然很长，但沉下心来慢慢看，其实很简单，而且执行步骤相似。

总结下该方法做的事情，就是回调BeanFactoryPostProcessors、BeanDefinitionRegistryPostProcessor，也就是我们前面提到的其中两个后置处理器。只不过这些后置处理器的执行顺序有个规则：

1、先执行实现PriorityOrdered接口的BeanDefinitionRegistryPostProcessor的postProcessBeanDefinitionRegistry方法，其实只有ConfigurationClassPostProcessor，这也是非常重要的类，可以说他是开始解析Bean的入口，ConfigurationClassPostProcessor会扫描Bean并解析注册到BeanFactory

2、执行实现Ordered接口的BeanDefinitionRegistryPostProcessor的postProcessBeanDefinitionRegistry方法

3、执行剩余的BeanDefinitionRegistryPostProcessor的postProcessBeanDefinitionRegistry方法

4、执行实现PriorityOrdered接口的BeanDefinitionRegistryPostProcessor、BeanFactoryPostProcessor的postProcessBeanFactory方法

5、执行实现Ordered接口的BeanDefinitionRegistryPostProcessor、BeanFactoryPostProcessor的postProcessBeanFactory方法

6、执行剩余的BeanDefinitionRegistryPostProcessor、BeanFactoryPostProcessor的postProcessBeanFactory方法

后续的解析逻辑实际上就是基于这几个后置处理器是怎么解析的。我们主要分析ConfigurationClassPostProcessor。

#### ConfigurationClassPostProcessor
前面我们分析过BeanDefinitionRegistryPostProcessor的执行顺序，ConfigurationClassPostProcessor实现了BeanDefinitionRegistryPostProcessor，是第一个被执行的后置处理器，他重写了两个方法postProcessBeanDefinitionRegistry和postProcessBeanFactory。

按照我们前面分析的执行过程，ConfigurationClassPostProcessor作为BeanDefinitionRegistryPostProcessor的子类先执行法postProcessBeanDefinitionRegistry方法，当所有的法postProcessBeanDefinitionRegistry方法执行完毕之后，ConfigurationClassPostProcessor作为BeanFactoryPostProcessor的子类执行postProcessBeanFactory方法。

我们先看postProcessBeanDefinitionRegistry方法，顺着主线调用链路找到如下方法processConfigBeanDefinitions
```
public void processConfigBeanDefinitions(BeanDefinitionRegistry registry) {
	List<BeanDefinitionHolder> configCandidates = new ArrayList<>();
	// 获得所有的BeanDefine，也就是前面注册的那几个后置处理器，我们前面把后置处理器转换成BeanDefinition注册到了eanFactory中
	// 加上我们自己注册的配置Bean：AppConfig
	// 前面在refresh之前，我们把AppConfig.class注册到了BeanFactory中
	String[] candidateNames = registry.getBeanDefinitionNames();
	for (String beanName : candidateNames) {
		BeanDefinition beanDef = registry.getBeanDefinition(beanName);
		// 判断beanDef是Full还是Lite
		// 所谓Full 就是用@Configuration注解修饰
		// 所谓Lite 就是用@Component、@ComponentScan、@Import、@ImportResource其中之一修饰
		// 下面是判断“CONFIGURATION_CLASS_ATTRIBUTE”属性值是Full还是Lite
		// 初次执行时CONFIGURATION_CLASS_ATTRIBUE为null，所以不走下面逻辑
		if (ConfigurationClassUtils.isFullConfigurationClass(beanDef) ||
				ConfigurationClassUtils.isLiteConfigurationClass(beanDef)) {
			// 打印debug日志而已
			...
		}
		// 默认会走这里，检查beanDef的注解，将他归类为Full或Lite
		// 设置“CONFIGURATION_CLASS_ATTRIBUTE”属性，标识它是Full还是Lite
		else if (ConfigurationClassUtils.checkConfigurationClassCandidate(beanDef, this.metadataReaderFactory)){
			configCandidates.add(new BeanDefinitionHolder(beanDef, beanName));
		}
	}
	// Return immediately if no @Configuration classes were found
	if (configCandidates.isEmpty()) {
		return;
	}
	// Sort by previously determined @Order value, if applicable
	configCandidates.sort((bd1, bd2) -> {
		int i1 = ConfigurationClassUtils.getOrder(bd1.getBeanDefinition());
		int i2 = ConfigurationClassUtils.getOrder(bd2.getBeanDefinition());
		return Integer.compare(i1, i2);
	});
	// Detect any custom bean name generation strategy supplied through the enclosing application context
	// 检测通过封闭的应用程序上下文提供的任何自定义bean名称生成策略
	SingletonBeanRegistry sbr = null;
	// registry实际上就是DefaultListableBeanFactory对象
	// DefaultListableBeanFactory的继承链路上既有SingletonBeaRegistry，也有BeanDefinitionRegistry
	if (registry instanceof SingletonBeanRegistry) {
		sbr = (SingletonBeanRegistry) registry;
		if (!this.localBeanNameGeneratorSet) {
			// 从命名看来是Bean命名生成器，默认也不会走，忽略吧
			...
		}
	}
	if (this.environment == null) {
		this.environment = new StandardEnvironment();
	}
	// Parse each @Configuration class
	ConfigurationClassParser parser = new ConfigurationClassParser(
			this.metadataReaderFactory, this.problemReporter, this.environment,
			this.resourceLoader, this.componentScanBeanNameGenerator, registry);
	// candidates是所有configBean
	Set<BeanDefinitionHolder> candidates = new LinkedHashSet<>(configCandidates);
	Set<ConfigurationClass> alreadyParsed = new HashSet<>(configCandidates.size());
	do {
		// 解析configBean，解析了config上个各类注解，扫描其他bean并注册到BeanFactory
		// 如果是@Compent注解会直接解析并注册到BeanFactory
		// 如果是@Import  @Bean等注解会先放入parser的ConfigurationClass，后面单独处理
		// 其中如果是@Import的类实现了ImportBeanDefinitionRegistrars
		// 会放到configClass的ImportBeanDefinitionReistrars
		// 这里只是Spring做了个区分，后面处理逻辑会区别对待处理而已
		parser.parse(candidates);
		parser.validate();
		//上面parse里解析@Import、@Bean最后都放入ConfigurationClass，这里取出来
		Set<ConfigurationClass> configClasses = new LinkedHashSet<>(parser.getConfigurationClasses());
		configClasses.removeAll(alreadyParsed);
		// Read the model and create bean definitions based on its content
		if (this.reader == null) {
			this.reader = new ConfigurationClassBeanDefinitionReader(
					registry, this.sourceExtractor, this.resourceLoader, this.environment,
					this.importBeanNameGenerator, parser.getImportRegistry());
		}
		// 这里是将前面只解析但没注册的Bean，都注册
		// 比如@Import、@Bean、@ImportResources
		this.reader.loadBeanDefinitions(configClasses);
		alreadyParsed.addAll(configClasses);
		candidates.clear();
		// 在解析的过程中会发现新的Bean并注册到BeanFactory中
		// 这里用于做循环判断，让新注册的Bean也会解析到
		if (registry.getBeanDefinitionCount() > candidateNames.length) {
			String[] newCandidateNames = registry.getBeanDefinitionNames();
			Set<String> oldCandidateNames = new HashSet<>(Arrays.asList(candidateNames));
			Set<String> alreadyParsedClasses = new HashSet<>();
			for (ConfigurationClass configurationClass : alreadyParsed) {
				alreadyParsedClasses.add(configurationClass.getMetadata().getClassName());
			}
			for (String candidateName : newCandidateNames) {
				if (!oldCandidateNames.contains(candidateName)) {
					BeanDefinition bd = registry.getBeanDefinition(candidateName);
					if (ConfigurationClassUtils.
					checkConfigurationClassCandidate(bd, this.metadataReaderFactory)&&
							!alreadyParsedClasses.contains(bd.getBeanClassName())) {
						candidates.add(new BeanDefinitionHolder(bd, candidateName));
					}
				}
			}
			candidateNames = newCandidateNames;
		}
	}
	while (!candidates.isEmpty());
	// Register the ImportRegistry as a bean in order to support ImportAware @Configuration classes
	// 注册一个ImportStack实例 以支持ImportAware @Configuration类，存疑
	if (sbr != null && !sbr.containsSingleton(IMPORT_REGISTRY_BEAN_NAME)) {
		sbr.registerSingleton(IMPORT_REGISTRY_BEAN_NAME, parser.getImportRegistry());
	}
	...
}
```
这个方法我们主要关注几点：

1、首先找到当前容器的所有BeanDefinition，默认情况下只有我们自己加入的JavaConfig和Spring自己注册的后置处理器。

2、遍历这些BeanDefinition，找到我们配置的Bean：@Configuration、@Component、@ComponentScan、@Import、@ImportResource等修饰的Bean，作为我们配置类，也是后面解析的入口，一般情况下只有我们自己注册的JavaConfig。

这里将配置类分成两种：Full和Lite

所谓Full 就是用@Configuration注解修饰的类

所谓Lite 就是用@Component、@ComponentScan、@Import、@ImportResource其中之一修饰

Full和Lite的区别后面会有解释，这里只是做了个区分。

3、解析Bean
```
parser.parse(candidates);
```
顺着主线调用链路，执行到了doProcessConfigurationClass
```
protected final SourceClass doProcessConfigurationClass(ConfigurationClass configClass, SourceClass sourceClass)
		throws IOException {
	if (configClass.getMetadata().isAnnotated(Component.class.getName())) {
		// Recursively process any member (nested) classes first
		// 首先递归处理（嵌套）类，先忽略吧
		processMemberClasses(configClass, sourceClass);
	}
	// Process any @PropertySource annotations
	// 处理@PropertySource注解，应用上可以把配置文件的值直接注入到bean属性，暂时先忽略
	for (AnnotationAttributes propertySource : AnnotationConfigUtils.attributesForRepeatable(
			sourceClass.getMetadata(), PropertySources.class,
			org.springframework.context.annotation.PropertySource.class)) {
		...
	}
	// Process any @ComponentScan annotations
	// 处理@ComponentScan注解
	Set<AnnotationAttributes> componentScans = AnnotationConfigUtils.attributesForRepeatable(
			sourceClass.getMetadata(), ComponentScans.class, ComponentScan.class);
	// 如果没有@ComponentScan，或者@Condition条件跳过，就不再进入这个if
	if (!componentScans.isEmpty() &&
			!this.conditionEvaluator.shouldSkip(sourceClass.getMetadata(), ConfigurationPhase.REGISTER_BEAN)) {
		for (AnnotationAttributes componentScan : componentScans) {
			// config类使用@ComponentScan注释 - >立即执行扫描 返回扫描到的BeanDefinitionHolder
			// 返回的就是我们加@Compent等注解的BeanDefinitionHolder
			// BeanDefinitionHolder就是BeanDefinition+nam的封装
			// parse下面的方法暂时先不分析了，大致看了下就是基于注解转换成BeanDefinition
			// 并注册到BeanFactory，最后为我们返回解析到的Bean集合
			Set<BeanDefinitionHolder> scannedBeanDefinitions =
					this.componentScanParser.parse(componentScan, sourceClass.getMetadata().getClassName());
			// Check the set of scanned definitions for any further config classes and parse recursively ifneeded
			//  检查任何进一步配置类的扫描定义集，并在需要时递归解析
			for (BeanDefinitionHolder holder : scannedBeanDefinitions) {
				BeanDefinition bdCand = holder.getBeanDefinition().getOriginatingBeanDefinition();
				if (bdCand == null) {
					bdCand = holder.getBeanDefinition();
				}
				// checkConfigurationClassCandidate方法前面遇到过
				// 检查bdCand的注解，将他归类为Full或Lite，并设置“CONFIGURATION_CLASS_ATTRIBUTE”属性
				// 用来标识它是Full还是Lite
				// 也就是判断扫描到的类中是否还有配置类(比如配置多数据源)，如果有递归解析，这个我们也忽略吧
				if (ConfigurationClassUtils.checkConfigurationClassCandidate(bdCand,this.metadataReaderFactory)) {
					parse(bdCand.getBeanClassName(), holder.getBeanName());
				}
			}
		}
	}
	// Process any @Import annotations
	// @Import注解是spring中很重要的一个注解，Springboot大量应用这个注解
	// @Import三种类，普通类，ImportSelector，ImportBeanDefinitionRegistrar
	// 存疑
	processImports(configClass, sourceClass, getImports(sourceClass), true);
	// Process any @ImportResource annotations
	// 先忽略，@ImportResource处理xml配置
	AnnotationAttributes importResource =
			AnnotationConfigUtils.attributesFor(sourceClass.getMetadata(), ImportResource.class);
	if (importResource != null) {
		...
	}
	// Process individual @Bean methods
	// 找到@Bean修饰的方法，add到configClass
	Set<MethodMetadata> beanMethods = retrieveBeanMethodMetadata(sourceClass);
	for (MethodMetadata methodMetadata : beanMethods) {
		configClass.addBeanMethod(new BeanMethod(methodMetadata, configClass));
	}
	// Process default methods on interfaces
	// 先忽略，默认没走 存疑
	processInterfaces(configClass, sourceClass);
	// Process superclass, if any
	// // 先忽略，默认没走 存疑
	if (sourceClass.getMetadata().hasSuperClass()) {
		...
	}
	// No superclass -> processing is complete
	return null;
}
```
这里宏观上的解析逻辑，按不同注解分别解析了@ComponentScan、@Import、@Bean等注解

* 先解析处理@PropertySource注解，就是我们常用的读取配置文件
* 处理@ComponentScan注解
* 处理@Import注解

Import里面又分三种情况，普通Import、ImportSelector、ImportBeanDefinitionRegistrar
* 处理@ImportResource
* 处理@Bean修饰的方法

具体的解析逻辑还需要下钻逐步分析...，这里我就不扯了，也扯不明白...，到processConfigBeanDefinitions执行结束为止，大体意思应该就是扫描到的Bean转换成BeanDefinition并注册到BeanFactory中。

按照前面的执行规则，之后会执行ConfigurationClassPostProcessor作为BeanFactoryPostProcessor的子类的postProcessBeanFactory方法，我们主要关注如下部分
```
// config cglic动态代理
enhanceConfigurationClasses(beanFactory);
// 新增一个后置处理器ImportAwareBeanPostProcessor
beanFactory.addBeanPostProcessor(new ImportAwareBeanPostProcessor(beanFactory));
```
前面我们的JavaConfig区分了Full和Lite，这里就是针对Full做了处理。如果Bean是Full类型，会使用cglib动态代理重新生成JavaConfig的代理类的对象，代码就不分析了，也分析不明白。。。，我们直接看结果

![](https://user-gold-cdn.xitu.io/2019/6/26/16b92aea1d9697d1?w=1505&h=948&f=png&s=276669)

![](https://user-gold-cdn.xitu.io/2019/6/26/16b92b2f7925d520?w=1505&h=978&f=png&s=209504)
在执行cglib动态代理之前，beanDefinition的beanClass属性还是属于AppConfig类型
在cglib动态代理之后，beanClass就变为代理类

![](https://user-gold-cdn.xitu.io/2019/6/26/16b92b616024db45?w=1505&h=944&f=png&s=215360)

至于这里为什么要针对Full类型的Bean也就是@Configuration修饰的JavaConfig要使用cglib动态代理，我了解到的造成的影响之一是：cglib动态代理可以防止JavaConfig中的Bean重复被实例化。

我们的第二步：Bean的解析，到这里算是结束。总结下，核心类是ConfigurationClassPostProcessor，这个后置处理器为我们解析了Bean，实际的解析逻辑我也是似懂非懂，即便如此也能大致了解Bean的解析过程以及Full类型的bean是使用cglib动态代理等实现。到此为止，我们定义的Bean还没有实例化，只是被解析成BeanDefinition存储在BeanFactory中。

### Bean的实例化
前面的流程已经将Bean解析，转换成BeanDefine并注册到了BeanFactory

回到refresh方法，实例化过程主要依赖这两个方法
```
// 注册 BeanPostProcessor 的实现类
// 所有的BeanPostProcessor统一add到BeanFactory的beanPostProcessors属性，准备执行
registerBeanPostProcessors(beanFactory);
// 重点，初始化所有的 singleton beans
finishBeanFactoryInitialization(beanFactory);
```
#### registerBeanPostProcessors

```
public static void registerBeanPostProcessors(
		ConfigurableListableBeanFactory beanFactory, AbstractApplicationContext applicationContext) {
	// 找到所有已经注册到BeanFactory的BeanPostProcessor
	// 除了我们自己加的，还有Spring前面内置的两个
	// AutowiredAnnotationProcessor
	// CommonAnnotationProcessor
	String[] postProcessorNames = beanFactory.getBeanNamesForType(BeanPostProcessor.class, true, false);
	// 添加BeanPostProcessorChecker后置处理器
	// 后置处理器总数就是 前面add的后置处理器 + 1 + 注册的BeanPostProcessor
	int beanProcessorTargetCount = beanFactory.getBeanPostProcessorCount() + 1 + postProcessorNames.length;
	beanFactory.addBeanPostProcessor(new BeanPostProcessorChecker(beanFactory, beanProcessorTargetCount));
	// 如果熟悉前面BeanFactoryPostProcessor的逻辑，这里一看就懂，跟前面很相似
	List<BeanPostProcessor> priorityOrderedPostProcessors = new ArrayList<>();
	List<BeanPostProcessor> internalPostProcessors = new ArrayList<>();
	List<String> orderedPostProcessorNames = new ArrayList<>();
	List<String> nonOrderedPostProcessorNames = new ArrayList<>();
	for (String ppName : postProcessorNames) {
		if (beanFactory.isTypeMatch(ppName, PriorityOrdered.class)) {
			BeanPostProcessor pp = beanFactory.getBean(ppName, BeanPostProcessor.class);
			priorityOrderedPostProcessors.add(pp);
			if (pp instanceof MergedBeanDefinitionPostProcessor) {
				internalPostProcessors.add(pp);
			}
		}
		else if (beanFactory.isTypeMatch(ppName, Ordered.class)) {
			orderedPostProcessorNames.add(ppName);
		}
		else {
			nonOrderedPostProcessorNames.add(ppName);
		}
	}
	sortPostProcessors(priorityOrderedPostProcessors, beanFactory);
	// 说一下BeanPostProcessor的注册，前面的某些环节会加一些BeanPostProcessors
	// 有的是注册在BeanFactory
	// 有的是直接add到AbstractBeanFactory的beanPostProcessors属性
	// 而这里就是将注册在BeanFactory里的add到AbstractBeanFactory的beanPostProcessors
	registerBeanPostProcessors(beanFactory, priorityOrderedPostProcessors);
	// Next, register the BeanPostProcessors that implement Ordered.
	List<BeanPostProcessor> orderedPostProcessors = new ArrayList<>();
	for (String ppName : orderedPostProcessorNames) {
		BeanPostProcessor pp = beanFactory.getBean(ppName, BeanPostProcessor.class);
		orderedPostProcessors.add(pp);
		if (pp instanceof MergedBeanDefinitionPostProcessor) {
			internalPostProcessors.add(pp);
		}
	}
	sortPostProcessors(orderedPostProcessors, beanFactory);
	registerBeanPostProcessors(beanFactory, orderedPostProcessors);
	// Now, register all regular BeanPostProcessors.
	List<BeanPostProcessor> nonOrderedPostProcessors = new ArrayList<>();
	for (String ppName : nonOrderedPostProcessorNames) {
		BeanPostProcessor pp = beanFactory.getBean(ppName, BeanPostProcessor.class);
		nonOrderedPostProcessors.add(pp);
		if (pp instanceof MergedBeanDefinitionPostProcessor) {
			internalPostProcessors.add(pp);
		}
	}
	registerBeanPostProcessors(beanFactory, nonOrderedPostProcessors);
	// Finally, re-register all internal BeanPostProcessors.
	sortPostProcessors(internalPostProcessors, beanFactory);
	registerBeanPostProcessors(beanFactory, internalPostProcessors);
	// 又加了个后置处理器ApplicationListenerDetector
	beanFactory.addBeanPostProcessor(new ApplicationListenerDetector(applicationContext));
}
```
这个方法很简单，前面我们分析过BeanFactoryPostProcessor的执行顺序，这俩类似，只不过这里仅仅是先注册而已。

#### finishBeanFactoryInitialization
```
protected void finishBeanFactoryInitialization(ConfigurableListableBeanFactory beanFactory) {
	// 字母意思是如果有这个conversionService，就set值，存疑，默认没走先忽略吧
	if (beanFactory.containsBean(CONVERSION_SERVICE_BEAN_NAME) &&
			beanFactory.isTypeMatch(CONVERSION_SERVICE_BEAN_NAME, ConversionService.class)) {
		beanFactory.setConversionService(
				beanFactory.getBean(CONVERSION_SERVICE_BEAN_NAME, ConversionService.class));
	}
	// 忽略
	if (!beanFactory.hasEmbeddedValueResolver()) {
		beanFactory.addEmbeddedValueResolver(strVal -> getEnvironment().resolvePlaceholders(strVal));
	}
	// 如果有LoadTimeWeaverAware，先实例化，忽略
	String[] weaverAwareNames = beanFactory.getBeanNamesForType(LoadTimeWeaverAware.class, false, false);
	for (String weaverAwareName : weaverAwareNames) {
		getBean(weaverAwareName);
	}
	// Stop using the temporary ClassLoader for type matching.
	beanFactory.setTempClassLoader(null);
	// Allow for caching all bean definition metadata, not expecting further changes.
	beanFactory.freezeConfiguration();
	// Instantiate all remaining (non-lazy-init) singletons.
	// 实例化所有非延迟加载的单例，重点
	beanFactory.preInstantiateSingletons();
}
```
后面的调用链路如下

preInstantiateSingletons->getBean->doGetBean

重点关注doGetBean

doGetBean里首先获得beanDefinition的class对象，取到其构造方法，然后根据构造方法实例化对象。

然后执行
```
...
populateBean(beanName, mbd, instanceWrapper);// Bean属性值的注入 @Autowired
exposedObject = initializeBean(beanName, exposedObject, mbd);// 执行初始化方法
...
```

#### initializeBean
就是执行初始化方法
```
protected Object initializeBean(final String beanName, final Object bean, @Nullable RootBeanDefinition mbd) {
	if (System.getSecurityManager() != null) {
		AccessController.doPrivileged((PrivilegedAction<Object>) () -> {
			invokeAwareMethods(beanName, bean);
			return null;
		}, getAccessControlContext());
	}
	else {
		// 执行一些aware回调
		// 如果Bean实现了BeanNameAware、BeanClassLoaderAware、BeanFactoryAware这些接口则执行
		invokeAwareMethods(beanName, bean);
	}
	Object wrappedBean = bean;
	// 先执行beanPostProcessor的postProcessBeforeInitialization
	if (mbd == null || !mbd.isSynthetic()) {
		wrappedBean = applyBeanPostProcessorsBeforeInitialization(wrappedBean, beanName);
	}
	try {// 执行初始化方法 InitializingBean 和 init-method
		invokeInitMethods(beanName, wrappedBean, mbd);
	}
	catch (Throwable ex) {
		throw new BeanCreationException(
				(mbd != null ? mbd.getResourceDescription() : null),
				beanName, "Invocation of init method failed", ex);
	}
	// 初始化方法执行完毕后,执行beanPostProcessor的postProcessAfterInitialization
	if (mbd == null || !mbd.isSynthetic()) {
		wrappedBean = applyBeanPostProcessorsAfterInitialization(wrappedBean, beanName);
	}
	return wrappedBean;
}
```
initializeBean就决定了我们Bean的几个初始化方法的执行顺序

BeanPostProcessor -> InitializingBean -> init-method

至此bean创建完毕。这只是最常见且最简单的一条线路。

这里面还有很多逻辑没有解释，比如属性的注入、循环引用等等，等有机会再补充吧

```!
文章比较长，不定时修整、更新一下~
```
