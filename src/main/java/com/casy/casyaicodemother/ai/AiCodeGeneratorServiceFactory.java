package com.casy.casyaicodemother.ai;

import com.casy.casyaicodemother.ai.tools.FileWriteTool;
import com.casy.casyaicodemother.exception.BusinessException;
import com.casy.casyaicodemother.exception.ErrorCode;
import com.casy.casyaicodemother.model.enums.CodeGenTypeEnum;
import com.casy.casyaicodemother.model.enums.ModelTypeEnum;
import com.casy.casyaicodemother.service.ChatHistoryService;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dev.langchain4j.community.store.memory.chat.redis.RedisChatMemoryStore;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.service.AiServices;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * AI 代码生成服务工厂（策略模式的上下文 Context）。
 * <p>
 * 职责：
 * <ul>
 *   <li>启动时通过 Spring 自动注入所有 {@link ModelProvider}，构建 {@code Map<ModelTypeEnum, ModelProvider>} 策略注册表</li>
 *   <li>对外提供统一入口 {@link #getService(ModelTypeEnum, CodeGenTypeEnum, Long)}，按模型类型选择策略</li>
 *   <li>为每个 appId 创建带 Redis 记忆的 {@link AiCodeGeneratorService}，并用 Caffeine 缓存实例</li>
 * </ul>
 * <p>
 * 调用链：业务层传入 modelType → 从 providerMap 取策略 → 用策略中的 ChatModel 构建 AiServices 代理。
 *
 * <h3>策略程示意图：</h3>
 * <img src="../../../../../javadoc/doc-files/多模型策略类.png" alt="多模型策略类" width="700"  height="500"/>
 */
@Component
@Slf4j
public class AiCodeGeneratorServiceFactory {

    private final Cache<String, AiCodeGeneratorService> serviceCache = Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(Duration.ofMinutes(30))
            .expireAfterAccess(Duration.ofMinutes(10))
            .removalListener((key, value, cause) -> {
                log.info("AI 服务实例被移除，appId: {}, 原因: {}", key, cause);
            })
            .build();
    @Resource
    private RedisChatMemoryStore redisChatMemoryStore;
    @Resource
    private ChatHistoryService chatHistoryService;
    /** 必须注入 Spring Bean，不能用 new FileWriteTool()，否则 @Resource AppVersionService 不会生效 */
    @Resource
    private FileWriteTool fileWriteTool;
    /**
     * Spring 自动收集容器中所有 ModelProvider Bean（各 *ModelConfig 注册）。
     * 新增模型时此字段无需修改，符合开闭原则。
     */
    @Resource
    private List<ModelProvider> modelProviders;
    /**
     * 策略注册表：ModelTypeEnum → ModelProvider，在 {@link #init()} 中初始化。
     */
    private Map<ModelTypeEnum, ModelProvider> providerMap;

    /**
     * 将所有 ModelProvider 策略注册到 Map，key 为模型类型。
     */
    @PostConstruct
    public void init() {
        providerMap = modelProviders.stream()
                .collect(Collectors.toMap(ModelProvider::getType, Function.identity()));
    }

    /**
     * 根据模型类型和应用 ID 获取 AI 服务（带缓存）。
     * 记忆 ID 仅使用 appId，切换模型不会丢失对话记忆。
     */
    public AiCodeGeneratorService getService(ModelTypeEnum modelType, CodeGenTypeEnum codeGenType, Long appId) {
        if (modelType == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "模型类型为空");
        }
        if (codeGenType == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "生成类型为空");
        }
        if (appId == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "应用ID不能为空");
        }
        return serviceCache.get(getCacheKey(modelType, codeGenType, appId), key -> createService(modelType, codeGenType, appId));
    }

    private AiCodeGeneratorService createService(ModelTypeEnum modelType, CodeGenTypeEnum codeGenType, Long appId) {
        ModelProvider provider = providerMap.get(modelType);
        if (provider == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "不支持的模型类型：" + modelType.getModelName());
        }
        MessageWindowChatMemory chatMemory = MessageWindowChatMemory
                .builder()
                .id(appId)
                .chatMemoryStore(redisChatMemoryStore)
                .maxMessages(20)
                .build();
        chatHistoryService.loadChatHistoryToMemory(appId, chatMemory, 20);
        // 根据代码生成类型选择不同的模型配置，vue工程项目需要用到工具
        return switch (codeGenType) {
            // Vue 项目生成使用pro模型
            case VUE_PROJECT -> AiServices.builder(AiCodeGeneratorService.class)
                    .streamingChatModel(provider.getStreamingChatModel())
                    .chatMemoryProvider(memoryId -> chatMemory)
                    .tools(fileWriteTool)
                    // hallucinatedToolNameStrategy（幻觉工具名称策略）配置了找不到工具时的处理策略，可以让框架帮我们处理 AI 出现幻觉的情况，比如告诉 AI “找不到工具”
                    // TODO 注意‍‍！这里最好做一些调整，防止 AI 一直无限循环调用工具，包括：
                    // TODO 调大对话记忆的容量，否则 AI 会中途断片儿，忘记已经生成了哪些文件
                    // TODO 尝试换其他的 AI 大模型、优化提示词
                    .hallucinatedToolNameStrategy(toolExecutionRequest -> ToolExecutionResultMessage.from(
                            toolExecutionRequest, "Error: there is no tool called " + toolExecutionRequest.name()
                    ))
                    .build();
            // HTML 和多文件生成使用默认模型
            case HTML, MULTI_FILE -> AiServices.builder(AiCodeGeneratorService.class)
                    .chatModel(provider.getChatModel())
                    .streamingChatModel(provider.getStreamingChatModel())
                    .chatMemory(chatMemory)
                    .build();
            default -> throw new BusinessException(ErrorCode.SYSTEM_ERROR,
                    "不支持的代码生成类型: " + codeGenType.getValue());
        };
    }

    private String getCacheKey(ModelTypeEnum modelTypeEnum, CodeGenTypeEnum codeGenType, Long appId) {
        return String.format("%s_%s_%s", modelTypeEnum.getModelName(), codeGenType.getValue(), appId);
    }

    /**
     * 默认 Bean，兼容旧代码直接注入 AiCodeGeneratorService 的场景。
     * 默认模型为 DeepSeek V4 Flash。
     */
    @Bean
    public AiCodeGeneratorService aiCodeGeneratorService() {
        return getService(ModelTypeEnum.DEEPSEEKFLASH, CodeGenTypeEnum.MULTI_FILE, 0L);
    }
}
