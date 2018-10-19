> EventBus用了很久了，用法简单，主要再来回顾下源码。事件(发布-订阅)总线带来优势非常明显，能够帮助组件之间相互高效的通信，同时能够解决解决组件高耦合。

<!-- more -->

## EventBus3.0基础用法

#### EventBus 三角色
* Event:  事件，任意对象;
* Subscriber: 事件订阅者，在3.0以前使用消息处理回调方法只限定于使用固定方法名onEventMainThread(Event event),onEventBackgroundThread(Event event)和
onEventAsync(Event event)，在3.0以后都使用注解，来回调方法随便取名;
* Publisher: 事件发布者，任意线程，任意位置发送事件Event；

#### EventBus 四模型
* POSTING(默认) 在哪个线程发布消息，就在哪个线程处理事件消息。在线程处理消息回调函数中避免执行耗时操作，否则会阻塞事件传递，引起ANR
* MAIN 事件的处理会在UI线程。事件处理时间不能过长，否则会导致ANR。
* BACKGROUND 如果事件是在UI线程中发布，处理消息回调函数会在新线程中运行；如果事件的发布本来就在子线程，处理消息回调函数就在该线程执行; 此事件禁止进行UI更新。
* ASYNC 无论在哪个线程发布，都会在子线程中执行处理事件，此事件禁止进行UI更新.

#### EventBus 五步骤
1. 新建事件类型

	```java
	public class EventMessage(){
		//property
		//...
	}
	```
	
2. 在需要订阅的地方注册

	```java
	EventBus.getDefault().register(this);
	```
	
3. 发布事件

	```java
	EventBus.getDefault().post(eventMessage);
	```
	
4. 处理事件

	```java
	//方法名随意，注解标记回调方法以及线程模型
	@Subscribe(threadMode = ThreadMode.MAIN)
	public void funcXX(EventMessage){
		//do something
		//...
	}
	```
	
5. 注销事件

	```java
	EventBus.getDefault().unregister(this);
	```
	
#### 黏性事件

**就是在发送黏性事件后，即使没有使用EventBus.getDefault().register(this)注册，等到注册订阅成功后，也能马上收到该事件。**

#### 混淆规则(官方混淆)

```java
-keepattributes *Annotation*
-keepclassmembers class ** {
    @org.greenrobot.eventbus.Subscribe <methods>;
}
-keep enum org.greenrobot.eventbus.ThreadMode { *; }

# Only required if you use AsyncExecutor
-keepclassmembers class * extends org.greenrobot.eventbus.util.ThrowableFailureEvent {
    <init>(java.lang.Throwable);
}
--------------------- 
```

## 大白话总结

EventBus核心技术：**发布者发布事件，订阅者通过反射的方式根据发布事件的class类型查找SubscriberMethod，然后通过这个类来反射订阅类中处理对应事件的方法**

* 源码分析参考来源，感谢：
	1. [Android、Boy](https://www.cnblogs.com/all88/archive/2016/03/30/5338412.html)，
	2. [Android进阶之光](http://liuwangshu.cn/)
	3. [alighters](https://www.jianshu.com/p/7f982f294fc2)
* [项目地址](https://github.com/KevinRocka/EventBusDemo)
