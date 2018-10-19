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


## EventBus3.0源码解析

> EventBus的源码写的非常不错，不同的线程策略，反射代码，以及Apt(注解)编译生成代码)和缓存的大量使用，可以重复阅读。

1. 订阅注册 
	
	> Event.getDefault().register(object);
		
	采用了**DLC双重检测单例模式**
	
	```java
	/** Convenience singleton for apps using a process-wide EventBus instance. */
	public static EventBus getDefault() {
	     if (defaultInstance == null) {
	         synchronized (EventBus.class) {
	             if (defaultInstance == null) {
	                 defaultInstance = new EventBus();
	             }
	         }
	     }
	     return defaultInstance;
	}
	```
	再来看看构造方法，**很明显通过一个构造者模式构造一个EventBusBuilder来对EventBus进行配置。**

	```java
	public EventBus() {
		this(DEFAULT_BUILDER);
	}
	
	EventBus(EventBusBuilder builder) {
		subscriptionsByEventType = new HashMap<>();
		typesBySubscriber = new HashMap<>();
		stickyEvents = new ConcurrentHashMap<>();
		mainThreadPoster = new HandlerPoster(this, Looper.getMainLooper(), 10);
		backgroundPoster = new BackgroundPoster(this);
		asyncPoster = new AsyncPoster(this);
		indexCount = builder.subscriberInfoIndexes != null ? builder.subscriberInfoIndexes.size() : 0;
		subscriberMethodFinder = new SubscriberMethodFinder(builder.subscriberInfoIndexes,
		builder.strictMethodVerification, builder.ignoreGeneratedIndex);
		logSubscriberExceptions = builder.logSubscriberExceptions;
		logNoSubscriberMessages = builder.logNoSubscriberMessages;
		sendSubscriberExceptionEvent = builder.sendSubscriberExceptionEvent;
		sendNoSubscriberEvent = builder.sendNoSubscriberEvent;
		throwSubscriberException = builder.throwSubscriberException;
		eventInheritance = builder.eventInheritance;
		executorService = builder.executorService;
	}
	```

	register主要做了两件事，一是**查找订阅者的订阅方法**findSubscriberMethods，二是**subscribe注册过程**

	```java
	public void register(Object subscriber) {
		Class<?> subscriberClass = subscriber.getClass();
		//查找订阅方法
		List<SubscriberMethod> subscriberMethods = subscriberMethodFinder.findSubscriberMethods(subscriberClass);
		synchronized (this) {
			for (SubscriberMethod subscriberMethod : subscriberMethods) {
				//订阅者的注册过程
				subscribe(subscriber, subscriberMethod);
			}
		}
	}
	```

	SubscriberMethod是用来保存订阅方法的Method对象，事件类型，优先级，是否黏性事件等
	
	```java
	public class SubscriberMethod {
		final Method method;
		final ThreadMode threadMode;
		final Class<?> eventType;
		final int priority;
		final boolean sticky;
		/** Used for efficient comparison */
		String methodString;
	}
	```

	查找订阅方法的核心方法入口

	```java
	List<SubscriberMethod> findSubscriberMethods(Class<?> subscriberClass) {
		//从缓存中查找是否有订阅方法集合，找到就立马返回
		List<SubscriberMethod> subscriberMethods = METHOD_CACHE.get(subscriberClass);
		if (subscriberMethods != null) {
			return subscriberMethods;
		}
		//根据ignoreGeneratedIndex属性来选择怎么查找订阅方法的集合，通过单利模式获取EventBus，ignoreGeneratedIndex就为false
		if (ignoreGeneratedIndex) {
			subscriberMethods = findUsingReflection(subscriberClass);
		} else {
			subscriberMethods = findUsingInfo(subscriberClass);
		}
		
		if (subscriberMethods.isEmpty()) {
			throw new EventBusException("Subscriber " + subscriberClass
                    + " and its super classes have no public methods with the @Subscribe annotation");
		} else {
			//将找到的订阅方法集合SubscriberMethod放入缓存中，方便下次继续查找
			METHOD_CACHE.put(subscriberClass, subscriberMethods);
			return subscriberMethods;
		}
	}	
	```

	```java
	private List<SubscriberMethod> findUsingInfo(Class<?> subscriberClass) {
        FindState findState = prepareFindState();
        findState.initForSubscriber(subscriberClass);
        while (findState.clazz != null) {
            findState.subscriberInfo = getSubscriberInfo(findState);
            if (findState.subscriberInfo != null) {
                SubscriberMethod[] array = findState.subscriberInfo.getSubscriberMethods();
                for (SubscriberMethod subscriberMethod : array) {
                    if (findState.checkAdd(subscriberMethod.method, subscriberMethod.eventType)) {
                        findState.subscriberMethods.add(subscriberMethod);
                    }
                }
            } else {
            	//将订阅方法保存到findState当中
                findUsingReflectionInSingleClass(findState);
            }
            findState.moveToSuperclass();
        }
        //最后通过该方法先做回收处理然后返回订阅方法的List集合
        return getMethodsAndRelease(findState);
  }
	```

	**通过反射获取订阅者中的方法并保存在findstate中**

	```java
	private void findUsingReflectionInSingleClass(FindState findState) {
        Method[] methods;
        try {
            // This is faster than getMethods, especially when subscribers are fat classes like Activities
            //通过反射来获取订阅者中的所有方法，根据方法的类型、参数、注解必须符合要求，找到订阅的方法。最后在保存到findstate中，否则抛出异常
            methods = findState.clazz.getDeclaredMethods();
        } catch (Throwable th) {
            // Workaround for java.lang.NoClassDefFoundError, see https://github.com/greenrobot/EventBus/issues/149
            methods = findState.clazz.getMethods();
            findState.skipSuperClasses = true;
        }
        for (Method method : methods) {
            int modifiers = method.getModifiers();
            // 通过访问符只获取public
            if ((modifiers & Modifier.PUBLIC) != 0 && (modifiers & MODIFIERS_IGNORE) == 0) {
                Class<?>[] parameterTypes = method.getParameterTypes();
                // 方法的参数（事件类型）长度只能为1
                if (parameterTypes.length == 1) {
                    Subscribe subscribeAnnotation = method.getAnnotation(Subscribe.class);
                    if (subscribeAnnotation != null) {
                        Class<?> eventType = parameterTypes[0];
                         // 获取到注解annotation中的内容，进行subscriberMethod的添加
                        if (findState.checkAdd(method, eventType)) {
                            ThreadMode threadMode = subscribeAnnotation.threadMode();
                            findState.subscriberMethods.add(new SubscriberMethod(method, eventType, threadMode,
                                    subscribeAnnotation.priority(), subscribeAnnotation.sticky()));
                        }
                    }
                } else if (strictMethodVerification && method.isAnnotationPresent(Subscribe.class)) {
                //抛出参数只能有一个的异常
                    String methodName = method.getDeclaringClass().getName() + "." + method.getName();
                    throw new EventBusException("@Subscribe method " + methodName +
                            "must have exactly 1 parameter but has " + parameterTypes.length);
                }
            } else if (strictMethodVerification && method.isAnnotationPresent(Subscribe.class)) {
            	//抛出只能为public,non-static,non-abs异常
                String methodName = method.getDeclaringClass().getName() + "." + method.getName();
                throw new EventBusException(methodName +
                        " is a illegal @Subscribe method: must be public, non-static, and non-abstract");
            }
        }
    }
		
	```

	订阅者的注册过程

	```java
    private void subscribe(Object subscriber, SubscriberMethod subscriberMethod) {
        Class<?> eventType = subscriberMethod.eventType;
        //根据subscriber(订阅者)和subscriberMethod(订阅方法)创建一个新的订阅对象newSubscription
        Subscription newSubscription = new Subscription(subscriber, subscriberMethod);
        //根据事件类型获取订阅对象集合
        CopyOnWriteArrayList<Subscription> subscriptions = subscriptionsByEventType.get(eventType);
        if (subscriptions == null) {
            subscriptions = new CopyOnWriteArrayList<>();
            subscriptionsByEventType.put(eventType, subscriptions);
        } else {
        	//如果订阅者已经被注册，则抛出异常
            if (subscriptions.contains(newSubscription)) {
                throw new EventBusException("Subscriber " + subscriber.getClass() + " already registered to event "
                        + eventType);
            }
        }
		//按照订阅方法的优先级将订阅方法插入订阅方法集合中，完成订阅方法注册
        int size = subscriptions.size();
        for (int i = 0; i <= size; i++) {
            if (i == size || subscriberMethod.priority > subscriptions.get(i).subscriberMethod.priority) {
                subscriptions.add(i, newSubscription);
                break;
            }
        }
		//获取事件类型集合
        List<Class<?>> subscribedEvents = typesBySubscriber.get(subscriber);
        if (subscribedEvents == null) {
            subscribedEvents = new ArrayList<>();
            typesBySubscriber.put(subscriber, subscribedEvents);
        }
        subscribedEvents.add(eventType);

        if (subscriberMethod.sticky) {
        	//黏性事件的处理
            if (eventInheritance) {
                // Existing sticky events of all subclasses of eventType have to be considered.
                // Note: Iterating over all events may be inefficient with lots of sticky events,
                // thus data structure should be changed to allow a more efficient lookup
                // (e.g. an additional map storing sub classes of super classes: Class -> List<Class>).
                Set<Map.Entry<Class<?>, Object>> entries = stickyEvents.entrySet();
                for (Map.Entry<Class<?>, Object> entry : entries) {
                    Class<?> candidateEventType = entry.getKey();
                    if (eventType.isAssignableFrom(candidateEventType)) {
                        Object stickyEvent = entry.getValue();
                        checkPostStickyEventToSubscription(newSubscription, stickyEvent);
                    }
                }
            } else {
                Object stickyEvent = stickyEvents.get(eventType);
                checkPostStickyEventToSubscription(newSubscription, stickyEvent);
            }
        }
    }	
	```

2. 发送
	> EventBus.getDefault().post(Object);
	
	从PostingThreadState取出事件队列，将当前的事件插入事件队列。最后将队列中的事件一次交postSingleEvent方法，并且移除事件。
	
	```java
    public void post(Object event) {
    	//PostingThreadState保存着队列和线程状态信息，是一个ThreadLocal的变量，更快的get/set获取多线程变量信息
        PostingThreadState postingState = currentPostingThreadState.get();
        //获取事件的队列，将当前事件插入事件队列
        List<Object> eventQueue = postingState.eventQueue;
        eventQueue.add(event);

        if (!postingState.isPosting) {
            postingState.isMainThread = Looper.getMainLooper() == Looper.myLooper();
            postingState.isPosting = true;
            if (postingState.canceled) {
                throw new EventBusException("Internal error. Abort state was not reset");
            }
            //处理队列的所有事件
            try {
                while (!eventQueue.isEmpty()) {
                    postSingleEvent(eventQueue.remove(0), postingState);
                }
            } finally {
                postingState.isPosting = false;
                postingState.isMainThread = false;
            }
        }
    }
	```

	```java
	private void postSingleEvent(Object event, PostingThreadState postingState) throws Error {
        Class<?> eventClass = event.getClass();
        boolean subscriptionFound = false;
        //处理向上查找事件的父类
        if (eventInheritance) {
            List<Class<?>> eventTypes = lookupAllEventTypes(eventClass);
            int countTypes = eventTypes.size();
            for (int h = 0; h < countTypes; h++) {
                Class<?> clazz = eventTypes.get(h);
                subscriptionFound |= postSingleEventForEventType(event, postingState, clazz);
            }
        } else {
            subscriptionFound = postSingleEventForEventType(event, postingState, eventClass);
        }
        if (!subscriptionFound) {
            if (logNoSubscriberMessages) {
                Log.d(TAG, "No subscribers registered for event " + eventClass);
            }
            if (sendNoSubscriberEvent && eventClass != NoSubscriberEvent.class &&
                    eventClass != SubscriberExceptionEvent.class) {
                post(new NoSubscriberEvent(this, event));
            }
        }
    }

	private boolean postSingleEventForEventType(Object event, PostingThreadState postingState, Class<?> eventClass) {
        CopyOnWriteArrayList<Subscription> subscriptions;
        //同步取出该事件对应的subscriptions订阅对象集合。
        synchronized (this) {
            subscriptions = subscriptionsByEventType.get(eventClass);
        }
        if (subscriptions != null && !subscriptions.isEmpty()) {
        	//遍历取出事件event和对应的Subscription交给postingState，然后调用postToSubscription方法来处理
            for (Subscription subscription : subscriptions) {
                postingState.event = event;
                postingState.subscription = subscription;
                boolean aborted = false;
                try {
                    postToSubscription(subscription, event, postingState.isMainThread);
                    aborted = postingState.canceled;
                } finally {
                    postingState.event = null;
                    postingState.subscription = null;
                    postingState.canceled = false;
                }
                if (aborted) {
                    break;
                }
            }
            return true;
        }
        return false;
    }
	```

	**如果threadMode是MAIN。若提交的事件的线程是主线程，则直接通过反射运行订阅方法；若不是主线程，则通过mainThreadPoster将我们订阅的事件添加到主线程队列去。mainThreadposter是HandlerPoster，继承Handler，通过Handler将事件切换到主线程。当调用它的enqueue方法的时候，它会发送一个事件并在它自身的handleMessage方法中从队列中取值并进行处理，从而达到在主线程中分发事件的目的。这里的backgroundPoster实现了Runnable接口，它会在调用enqueue方法的时候，拿到EventBus的ExecutorService实例，并使用它来执行自己。在它的run方法中会从队列中不断取值来进行执行。**

	```java
   private void postToSubscription(Subscription subscription, Object event, boolean isMainThread) {
        switch (subscription.subscriberMethod.threadMode) {
            case POSTING:
                invokeSubscriber(subscription, event);
                break;
            case MAIN:
                if (isMainThread) {
                    invokeSubscriber(subscription, event);
                } else {
                    mainThreadPoster.enqueue(subscription, event);
                }
                break;
            case BACKGROUND:
                if (isMainThread) {
                    backgroundPoster.enqueue(subscription, event);
                } else {
                    invokeSubscriber(subscription, event);
                }
                break;
            // asyncPoster是一个Runnable，没有等待，直接子线程执行
            case ASYNC:
                asyncPoster.enqueue(subscription, event);
                break;
            default:
                throw new IllegalStateException("Unknown thread mode: " + subscription.subscriberMethod.threadMode);
        }
    }	
	```

3.	取消注册
> EventBus.getDefault().unregister(object);
	
	取消注册调用的是unregister

	```java
	public synchronized void unregister(Object subscriber) {
		//订阅的时候将subscriber放入了typesBySubscriber中，取消注册时找到事件类型集合
        List<Class<?>> subscribedTypes = typesBySubscriber.get(subscriber);
        if (subscribedTypes != null) {
        	//遍历subscribedTypes，最后调用unsubscribeByEventType
            for (Class<?> eventType : subscribedTypes) {
                unsubscribeByEventType(subscriber, eventType);
            }
            //从typesBySubscriber移除subscriber
            typesBySubscriber.remove(subscriber);
        } else {
            Log.w(TAG, "Subscriber to unregister was not registered before: " + subscriber.getClass());
        }
    }

    //获取订阅对象集合subscriptions，并且移除订阅对象集合的订阅者
    /** Only updates subscriptionsByEventType, not typesBySubscriber! Caller must update typesBySubscriber. */
    private void unsubscribeByEventType(Object subscriber, Class<?> eventType) {
        List<Subscription> subscriptions = subscriptionsByEventType.get(eventType);
        if (subscriptions != null) {
            int size = subscriptions.size();
            for (int i = 0; i < size; i++) {
                Subscription subscription = subscriptions.get(i);
                if (subscription.subscriber == subscriber) {
                    subscription.active = false;
                    subscriptions.remove(i);
                    i--;
                    size--;
                }
            }
        }
    }
	```

## 简易的EventBus框架

> 为了熟悉EventBus工作原理，改了一个简易的EventBus框架(RockaEventBus)，主要就是注解和反射的使用

* 获取该object下符合条件的回调订阅消息方法合集

	```java
    private List<SubscribleMethod> getSubscribleMethods(Object object) {
        List<SubscribleMethod> list = new ArrayList<>();

        Class clz = object.getClass();

        while (clz != null) {
            Method[] methods = clz.getDeclaredMethods();
            for (Method method : methods) {
                RockaSubscribe subscribe = method.getAnnotation(RockaSubscribe.class);

                if (subscribe == null) {
                    continue;
                }

                Class[] paratems = method.getParameterTypes();
                int modifiers = method.getModifiers();
                if (paratems.length != 1) {
                    throw new RuntimeException("Rocka EventBus can only have one parameter");
                } else if (!(((modifiers & Modifier.PUBLIC) != 0 && (modifiers & MODIFIERS_IGNORE) == 0))) {
                    throw new RuntimeException("@Subscribe method: must be public");
                }

                ThreadMode threadMode = subscribe.threadMode();

                //消息订阅回调方法，线程类型，消息订阅回调方法参数
                SubscribleMethod subscribleMethod = new SubscribleMethod(method, threadMode, paratems[0]);
                list.add(subscribleMethod);
            }

            clz = clz.getSuperclass();
        }
        return list;
    }
	```
* 处理发消息逻辑

	```java
    public void post(final Object event) {
        Set<Object> set = cacheMap.keySet();
        Iterator iterator = set.iterator();
        while (iterator.hasNext()) {
            final Object ob = iterator.next();
            List<SubscribleMethod> list = cacheMap.get(ob);
            for (final SubscribleMethod subscribleMethod : list) {
                //是同一个类或接口
                if (subscribleMethod.getEventType().isAssignableFrom(event.getClass())) {
                    switch (subscribleMethod.getThreadMode()) {
                        case ASYNC:
                            break;
                        case POSTING:
                            break;
                        case BACKGROUND:
                            break;
                        case MAIN:
                            if (Looper.myLooper() == Looper.getMainLooper()) {
                                invokeSubscriber(subscribleMethod, ob, event);
                            }else {
                                handler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        invokeSubscriber(subscribleMethod, ob, event);
                                    }
                                });
                            }
                            break;
                        default:
                            break;
                    }
                }
            }
        }
    }
	```
	
## 大白话总结

EventBus核心技术：**发布者发布事件，订阅者通过反射的方式根据发布事件的class类型查找SubscriberMethod，然后通过这个类来反射订阅类中处理对应事件的方法**

* 源码分析参考来源，感谢：
	1. [Android、Boy](https://www.cnblogs.com/all88/archive/2016/03/30/5338412.html)，
	2. [Android进阶之光](http://liuwangshu.cn/)
	3. [alighters](https://www.jianshu.com/p/7f982f294fc2)
* [项目地址](https://github.com/KevinRocka/EventBusDemo)
