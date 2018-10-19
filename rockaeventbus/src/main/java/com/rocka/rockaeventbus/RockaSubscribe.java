package com.rocka.rockaeventbus;


import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
public @interface RockaSubscribe {
    ThreadMode threadMode() default ThreadMode.POSTING;
}
