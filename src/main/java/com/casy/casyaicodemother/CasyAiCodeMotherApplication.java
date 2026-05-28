package com.casy.casyaicodemother;


import cn.dev33.satoken.SaManager;
import dev.langchain4j.community.store.embedding.redis.spring.RedisEmbeddingStoreAutoConfiguration;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@SpringBootApplication(exclude = {RedisEmbeddingStoreAutoConfiguration.class}) // 排除 embedding 的自动装配
@EnableAspectJAutoProxy(exposeProxy = true) //可以在方法执行时通过 AopContext.currentProxy() 获取当前的代理对象。
@MapperScan("com.casy.casyaicodemother.mapper")
public class CasyAiCodeMotherApplication {

    public static void main(String[] args) {
        SpringApplication.run(CasyAiCodeMotherApplication.class, args);
        System.out.println("启动成功，Sa-Token 配置如下：" + SaManager.getConfig());
    }

}
