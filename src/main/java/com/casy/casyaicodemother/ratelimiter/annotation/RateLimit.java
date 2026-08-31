package com.casy.casyaicodemother.ratelimiter.annotation;

import com.casy.casyaicodemother.ratelimiter.enums.RateLimitType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

//规定这个自定义注解能写在哪些地方
//ElementType.METHOD → 只能加在方法上，不能写在类、字段、参数上面。
@Target({ElementType.METHOD})
/*
作用：规定注解的生命周期，什么时候保留这个注解信息，一共 3 种策略：
1. RetentionPolicy.SOURCE
    源码阶段有效，编译后直接丢弃，class 文件不存在。
2. RetentionPolicy.CLASS（默认）
    编译后放到 class 字节码文件，运行时 JVM 加载类的时候会丢弃，反射拿不到注解。
3. RetentionPolicy.RUNTIME
    运行时依旧保留在字节码，JVM 加载类后，可以通过反射读取注解信息。
*/
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {
    
    /**
     * 限流key前缀
     */
    String key() default "";
    
    /**
     * 每个时间窗口允许的请求数
     */
    int rate() default 10;
    
    /**
     * 时间窗口（秒）
     */
    int rateInterval() default 1;
    
    /**
     * 限流类型
     */
    RateLimitType limitType() default RateLimitType.USER;
    
    /**
     * 限流提示信息
     */
    String message() default "请求过于频繁，请稍后再试";
}
