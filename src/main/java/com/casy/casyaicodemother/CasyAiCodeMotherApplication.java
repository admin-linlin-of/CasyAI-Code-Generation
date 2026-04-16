package com.casy.casyaicodemother;


import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@SpringBootApplication
@EnableAspectJAutoProxy(exposeProxy = true) //可以在方法执行时通过 AopContext.currentProxy() 获取当前的代理对象。
public class CasyAiCodeMotherApplication {

    public static void main(String[] args) {
        SpringApplication.run(CasyAiCodeMotherApplication.class, args);
    }

}
