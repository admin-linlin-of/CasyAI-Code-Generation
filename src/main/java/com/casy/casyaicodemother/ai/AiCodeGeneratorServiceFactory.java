package com.casy.casyaicodemother.ai;

import com.casy.casyaicodemother.ai.tools.RepairingToolExecutor;
import com.casy.casyaicodemother.ai.tools.ToolManager;
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
import dev.langchain4j.service.tool.AiServiceTool;
import dev.langchain4j.service.tool.ToolErrorHandlerResult;
import dev.langchain4j.service.tool.ToolService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

    private final Cache<String, AiCodeGeneratorService> serviceCache = Caffeine.newBuilder().maximumSize(1000).expireAfterWrite(Duration.ofMinutes(30)).expireAfterAccess(Duration.ofMinutes(10)).removalListener((key, value, cause) -> {
        log.info("AI 服务实例被移除，appId: {}, 原因: {}", key, cause);
    }).build();
    @Resource
    private RedisChatMemoryStore redisChatMemoryStore;
    @Resource
    private ChatHistoryService chatHistoryService;

    @Resource
    private ToolManager toolManager;

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
        providerMap = modelProviders.stream().collect(Collectors.toMap(ModelProvider::getType, Function.identity()));
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
        MessageWindowChatMemory chatMemory = MessageWindowChatMemory.builder().id(appId).chatMemoryStore(redisChatMemoryStore).maxMessages(20).build();
        chatHistoryService.loadChatHistoryToMemory(appId, chatMemory, 20);
        // 根据代码生成类型选择不同的模型配置，vue工程项目需要用到工具
        return switch (codeGenType) {
            // Vue 项目生成使用pro模型
            case VUE_PROJECT ->
                    AiServices.builder(AiCodeGeneratorService.class)
                            .streamingChatModel(provider.getStreamingChatModel())
                            .chatMemoryProvider(memoryId -> chatMemory)
                            // 用 RepairingToolExecutor 包装默认执行器，在 Jackson 解析前先尝试修复 LLM 返回的非法 tool arguments JSON
                            .tools(wrapToolsWithRepair(toolManager.getAllTools()))
                            // 修复仍失败时，将错误文本回传 LLM 让其自行纠正（而非直接中断流式生成）
                            .toolArgumentsErrorHandler((error, context) -> ToolErrorHandlerResult.text("工具参数 JSON 解析失败：" + error.getMessage() + "。请确保 content 中双引号转义为 \\\"，换行用 \\n；单块不超过1500字符，大文件分块 append=false/true 写入。")) // TODO 这个提示在多个工具时就不合适了
                            // hallucinatedToolNameStrategy（幻觉工具名称策略）配置了找不到工具时的处理策略，可以让框架帮我们处理 AI 出现幻觉的情况，比如告诉 AI “找不到工具”
                            // TODO 注意‍‍！这里最好做一些调整，防止 AI 一直无限循环调用工具，包括：
                            // TODO 调大对话记忆的容量，否则 AI 会中途断片儿，忘记已经生成了哪些文件
                            // TODO 尝试换其他的 AI 大模型、优化提示词
                            .hallucinatedToolNameStrategy(toolExecutionRequest -> ToolExecutionResultMessage.from(toolExecutionRequest, "Error: there is no tool called " + toolExecutionRequest.name())).build();
            // HTML 和多文件生成使用默认模型
            case HTML, MULTI_FILE ->
                    AiServices.builder(AiCodeGeneratorService.class).chatModel(provider.getChatModel()).streamingChatModel(provider.getStreamingChatModel()).chatMemory(chatMemory).build();
            default ->
                    throw new BusinessException(ErrorCode.SYSTEM_ERROR, "不支持的代码生成类型: " + codeGenType.getValue());
        };
    }

    private String getCacheKey(ModelTypeEnum modelTypeEnum, CodeGenTypeEnum codeGenType, Long appId) {
        return String.format("%s_%s_%s", modelTypeEnum.getModelName(), codeGenType.getValue(), appId);
    }

    /**
     * 将 @Tool Bean 注册为 AiServiceTool，并用 {@link RepairingToolExecutor} 包装原始执行器。
     * <p>
     * 背景：LLM 调用 writeFile 时，content 内未转义的双引号会导致 arguments JSON 解析失败；
     * 包装后在真正执行工具前先做 JSON 容错修复。
     */
    private List<AiServiceTool> wrapToolsWithRepair(Object tools) {
        Stream<?> toolBeanStream;
        if (tools == null) {
            toolBeanStream = Stream.empty();
        } else if (tools instanceof Object[] toolArray) {
            toolBeanStream = Arrays.stream(toolArray);
        } else if (tools instanceof Collection<?> toolCollection) {
            toolBeanStream = toolCollection.stream();
        } else {
            toolBeanStream = Stream.of(tools);
        }

        return toolBeanStream
                .filter(Objects::nonNull)
                .flatMap(toolBean -> ToolService.findTools(toolBean).stream())
                .map(tool -> tool.toBuilder()
                        .toolExecutor(new RepairingToolExecutor(tool.toolExecutor()))
                        .build())
                .toList();
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
