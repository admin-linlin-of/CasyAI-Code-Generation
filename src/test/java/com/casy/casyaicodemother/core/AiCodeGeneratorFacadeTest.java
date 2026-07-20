package com.casy.casyaicodemother.core;

import com.casy.casyaicodemother.constant.Global;
import com.casy.casyaicodemother.model.enums.CodeGenTypeEnum;
import com.casy.casyaicodemother.model.enums.ModelTypeEnum;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;
import reactor.core.publisher.Flux;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
class AiCodeGeneratorFacadeTest {

    AiCodeGeneratorFacadeTest(@Autowired ConfigurableApplicationContext context) {
        Global.setSpringContext(context);
    }

    @Resource
    AiCodeGeneratorFacade aiCodeGeneratorFacade;

    @Test
    void generateAndSaveCodeStream() {

        Flux<String> stringFlux = aiCodeGeneratorFacade.generateAndSaveCodeStream("帮我实现一个简单的登录页，代码不要超过100行", CodeGenTypeEnum.HTML, ModelTypeEnum.GPT, 1L, 1L, "");
        List<String> result = stringFlux.collectList().block();
        assertNotNull(result);
    }

    @Test
    void generateAndSaveCode() {
        File file = aiCodeGeneratorFacade.generateAndSaveCode("帮我实现一个简单的登录页，代码不要超过100行", CodeGenTypeEnum.MULTI_FILE, ModelTypeEnum.GPT, 1L);
        assertNotNull(file);
    }

    @Test
    void generateVueProjectCodeStream() {
        Flux<String> codeStream = aiCodeGeneratorFacade.generateAndSaveCodeStream(
                "简单的任务记录网站，总代码量不超过 200 行",
                CodeGenTypeEnum.VUE_PROJECT, ModelTypeEnum.GPT,2L, 2L, "");
        // 阻塞等待所有数据收集完成
        List<String> result = codeStream.collectList().block();
        // 验证结果
        Assertions.assertNotNull(result);
        String completeContent = String.join("", result);
        Assertions.assertNotNull(completeContent);
    }

}