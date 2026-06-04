package com.casy.casyaicodemother.ai;

import com.casy.casyaicodemother.model.enums.ModelTypeEnum;
import com.casy.casyaicodemother.service.ChatHistoryService;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dev.langchain4j.community.store.memory.chat.redis.RedisChatMemoryStore;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.service.AiServices;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@Slf4j
public class AiCodeGeneratorServiceFactory {

    @Resource
    private RedisChatMemoryStore redisChatMemoryStore;

    @Resource
    private ChatHistoryService chatHistoryService;

    @Resource
    @Qualifier("openAiChatModel")
    ChatModel openAiChatModel;

    @Resource
    @Qualifier("openAiStreamingChatModel")
    StreamingChatModel openAiStreamingChatModel;

    @Resource
    @Qualifier("gptChatModel")
    ChatModel gptChatModel;

    @Resource
    @Qualifier("gptStreamingChatModel")
    StreamingChatModel gptStreamingChatModel;

    /**
     * 利用 Caffeine 缓存来存储，之后相同 appId 就能直接获取到 AI 服务实例，避免重复构造
     * 缓存策略：
     * - 最大缓存 1000 个实例
     * - 写入后 30 分钟过期
     * - 访问后 10 分钟过期
     */
    private final Cache<String, AiCodeGeneratorService> serviceCache = Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(Duration.ofMinutes(30))
            .expireAfterAccess(Duration.ofMinutes(10))
            .removalListener((key, value, cause) -> {
                log.info("AI 服务实例被移除，appId: {}, 原因: {}", key, cause);
            })
            .build();

    /**
     * 根据 modeType + appId 获取DeepSeek服务（带缓存）
     * 记忆 ID 只用 appId，不要带模型名，这样切换模型就不会丢失记忆
     * 本地缓存会存在不同模型的service
     */
    public AiCodeGeneratorService getDeepSeekCodeGeneratorService(Long appId) {
        return serviceCache.get(getCacheKey(ModelTypeEnum.DEEPSEEK, appId), this::createDeepSeekCodeGeneratorService);
    }

    public AiCodeGeneratorService createDeepSeekCodeGeneratorService(String cacheKey) {
        // 根据 appId 构建独立的对话记忆
        MessageWindowChatMemory chatMemory = MessageWindowChatMemory
                .builder()
                .id(getAppId(cacheKey))
                .chatMemoryStore(redisChatMemoryStore)
                .maxMessages(20)
                .build();
        // 从数据库加载历史对话到记忆中
        chatHistoryService.loadChatHistoryToMemory(getAppId(cacheKey), chatMemory, 20);
        return AiServices.builder(AiCodeGeneratorService.class)
                .chatModel(openAiChatModel)
                .streamingChatModel(openAiStreamingChatModel)
                .chatMemory(chatMemory)
                .build();
    }

    /**
     * 根据 appId 获取GPT服务（带缓存）
     */
    public AiCodeGeneratorService getGptCodeGeneratorService(Long appId) {
        return serviceCache.get(getCacheKey(ModelTypeEnum.GPT, appId), this::createGptCodeGeneratorService);
    }

    public AiCodeGeneratorService createGptCodeGeneratorService(String cacheKey) {
        // 根据 appId 构建独立的对话记忆
        MessageWindowChatMemory chatMemory = MessageWindowChatMemory
                .builder()
                .id(getAppId(cacheKey))
                .chatMemoryStore(redisChatMemoryStore)
                .maxMessages(20)
                .build();
        chatHistoryService.loadChatHistoryToMemory(getAppId(cacheKey), chatMemory, 20);
        return AiServices.builder(AiCodeGeneratorService.class)
                .chatModel(gptChatModel)
                .streamingChatModel(gptStreamingChatModel)
                .chatMemory(chatMemory)
                .build();
    }

    private String getCacheKey(ModelTypeEnum modelTypeEnum, Long appId) {
        return String.format("%s_%s", modelTypeEnum.getModelName(), appId);
    }

    private Long getAppId(String cacheKey) {
        return Long.valueOf(cacheKey.split("_")[1]);
    }

    /**
     * 默认提供一个 Bean，兼容旧代码
     */
    @Bean
    public AiCodeGeneratorService aiCodeGeneratorService() {
        return getDeepSeekCodeGeneratorService(0L);
    }
}
