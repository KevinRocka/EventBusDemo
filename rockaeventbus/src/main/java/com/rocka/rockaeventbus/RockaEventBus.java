package com.rocka.rockaeventbus;

import android.os.Handler;
import android.os.Looper;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @author: Rocka
 * @version: 1.0
 */
public class RockaEventBus {

    static volatile RockaEventBus defaultInstance;

    private ExecutorService executorService;

    private Handler handler;

    private Map<Object, List<SubscribleMethod>> cacheMap;

    private static final int BRIDGE = 0x40;
    private static final int SYNTHETIC = 0x1000;

    private static final int MODIFIERS_IGNORE = Modifier.ABSTRACT | Modifier.STATIC | BRIDGE | SYNTHETIC;

    /**
     * DLC 单例模式
     *
     * @return
     */
    public static RockaEventBus getDefault() {
        if (defaultInstance == null) {
            synchronized (RockaEventBus.class) {
                if (defaultInstance == null) {
                    defaultInstance = new RockaEventBus();
                }
            }
        }
        return defaultInstance;
    }

    private RockaEventBus() {
        cacheMap = new HashMap<>();
        executorService = Executors.newCachedThreadPool();
        handler = new Handler(Looper.getMainLooper());
    }

    /**
     * 注册
     *
     * @param object
     */
    public void register(Object object) {
        List<SubscribleMethod> list = cacheMap.get(object);

        if (list == null) {
            list = getSubscribleMethods(object);
            cacheMap.put(object, list);
        }
    }

    /**
     * 获取该object下符合条件的回调订阅消息方法
     *
     * @param object
     * @return
     */
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

    /**
     * 注销
     *
     * @param object
     */
    public void unregister(Object object) {
        if (cacheMap != null) {
            cacheMap.remove(object);
        }
    }

    /**
     * 发消息
     *
     * @param event
     */
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

    void invokeSubscriber(SubscribleMethod subscribleMethod, Object ac, Object event) {
        try {
            Method method = subscribleMethod.getMethod();

            method.invoke(ac, event);
        } catch (Exception e) {
            throw new IllegalStateException("Unexpected exception", e);
        }
    }

}
