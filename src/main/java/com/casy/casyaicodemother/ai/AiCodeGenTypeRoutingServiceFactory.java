package com.casy.casyaicodemother.ai;

import com.casy.casyaicodemother.service.AiModelCatalogService;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AI代码生成类型路由服务工厂
 *
 * @author casy
 */
@Slf4j
@Configuration
public class AiCodeGenTypeRoutingServiceFactory {

    /** 使用 routing-chat-model（便宜模型），避免类型/模型路由占用代码生成大模型额度 */
    @Resource
    @Qualifier("routingChatModel")
    private ChatModel chatModel;

    /**
     * 创建AI代码生成类型路由服务实例
     */
    @Bean
    public AiCodeGenTypeRoutingService aiCodeGenTypeRoutingService() {
        return AiServices.builder(AiCodeGenTypeRoutingService.class)
                .chatModel(chatModel)
                .build();
    }

    /**
     * 模型路由：每次请求用当前启用模型生成 system prompt，避免静态 prompt 仍推荐已停用模型。
     */
    @Bean
    public AiCodeModelTypeRoutingService aiCodeModelTypeRoutingService(AiModelCatalogService aiModelCatalogService) {
        return AiServices.builder(AiCodeModelTypeRoutingService.class)
                .chatModel(chatModel)
                .systemMessageProvider(ignored -> aiModelCatalogService.buildRoutingPrompt()) // 用返回值当 system prompt，相当于动态的 @SystemMessage
                .build();
    }
}
