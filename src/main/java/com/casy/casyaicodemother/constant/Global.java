package com.casy.casyaicodemother.constant;

import com.casy.casyaicodemother.config.GlobalSpringContextInitializer;
import com.casy.casyaicodemother.exception.IllegalStateExceptionSupplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.Optional;

/**
 * 全局静态数据类。
 * 本类只应该放“绝对全局”的的内容
 *
 */
@Slf4j
public class Global {


    /**
     * Spring的Context对象。 服务在SpringBoot启动时对本变量赋值(需等待赋值完成后才能使用)
     * 将本变量将 Spring的Context对象 存放的目的：在一些静态static代码（例如工具类）需要获得Spring Bean对象进行处理。
     *   但在static静态代码中 无法通过autowired注入方式获得Bean对象。所以需要本变量，实现通过springContext.getBean()方式获得Bean对象
     */
    private static ConfigurableApplicationContext springContext;

    /**
     * 设置springContext. 各个服务在启动时自动通过{@link GlobalSpringContextInitializer}赋值
     * @param context spring context
     */
    public static void setSpringContext(ConfigurableApplicationContext context) {
        if (context == null) {
            log.warn("传入的springContext新对象为null，忽略!");
            return;
        }
        if(null != springContext) { // 若不为空，输出原对象内容只日志
            log.info("原全局springContext对象为:{}", springContext);
        }
        springContext = context;
        log.info("已将全局springContext对象设置为: {}", springContext);
    }


    /**
     * 获取Spring的ApplicationContext对象。
     * @return ApplicationContext
     * @throws IllegalStateException 未设置ApplicationContext对象
     */
    public static ConfigurableApplicationContext getSpringContext() {
        return Optional.ofNullable(springContext)
                .orElseThrow(IllegalStateExceptionSupplier.of("全局springContext对象为空! 应当在SpringBoot启动时完成赋值后，才能使用该对象"));
    }
}
