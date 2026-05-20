package com.casy.casyaicodemother.config;


import com.casy.casyaicodemother.constant.Global;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.lang.NonNull;

/**
 * 通过本类实现对全局{@link Global}中变量的springContext的设置
 * <p>
 *   在SpringApplication执行流程中，initialize(包括实现该方法的子类)会先于 {@linkplain ConfigurableApplicationContext#refresh()} 执行
 *   因为在代码中，例如{@link Constant}类的静态static代码，需要通过springContext获取属性等操作，
 *   所以需要本类对{@link Global}中变量的springContext的设置，这样静态static代码中才能使用
 * </p>
 */
@ToString
@Slf4j
public class GlobalSpringContextInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
    @Override
    public void initialize(@NonNull ConfigurableApplicationContext applicationContext) {
        log.info("GlobalSpringContextInitializer initialize: {}", applicationContext.getId());
        Global.setSpringContext(applicationContext);
    }
}
