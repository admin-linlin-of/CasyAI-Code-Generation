package com.casy.casyaicodemother.langgraph4j.ai;

import com.casy.casyaicodemother.langgraph4j.tools.ImageSearchTool;
import com.casy.casyaicodemother.langgraph4j.tools.LogoGeneratorTool;
import com.casy.casyaicodemother.langgraph4j.tools.MermaidDiagramTool;
import com.casy.casyaicodemother.langgraph4j.tools.UndrawIllustrationTool;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.service.AiServices;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

@Slf4j
@Configuration
public class ImageCollectionServiceFactory {

    @Resource
    private ImageSearchTool imageSearchTool;

    @Resource
    private UndrawIllustrationTool undrawIllustrationTool;

    @Resource
    private MermaidDiagramTool mermaidDiagramTool;

    @Resource
    private LogoGeneratorTool logoGeneratorTool;

    @Bean
    @Scope("prototype")
    public ImageCollectionService createImageCollectionService(
            @Qualifier("deepSeekV4FlashChatModel") ChatModel chatModel) {
        return AiServices.builder(ImageCollectionService.class)
                .chatModel(chatModel)
                .chatRequestTransformer(request -> request.toBuilder()
                        .parameters(request.parameters().overrideWith(
                                ChatRequestParameters.builder()
                                        .responseFormat(ResponseFormat.JSON)
                                        .build()))
                        .build())
                .tools(
                        imageSearchTool,
                        undrawIllustrationTool,
                        mermaidDiagramTool,
                        logoGeneratorTool
                )
                .build();
    }
}
