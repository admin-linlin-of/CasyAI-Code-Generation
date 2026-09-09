package com.casy.casyaicodemother.ai;

import com.casy.casyaicodemother.ai.guardrail.PromptSafetyInputGuardrail;
import com.casy.casyaicodemother.ai.tools.RepairingToolExecutor;
import com.casy.casyaicodemother.ai.tools.ToolManager;
import com.casy.casyaicodemother.exception.BusinessException;
import com.casy.casyaicodemother.exception.ErrorCode;
import com.casy.casyaicodemother.model.enums.CodeGenTypeEnum;
import com.casy.casyaicodemother.model.enums.ModelTypeEnum;
import com.casy.casyaicodemother.service.ChatHistoryService;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.guardrail.config.OutputGuardrailsConfig;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.tool.AiServiceTool;
import dev.langchain4j.service.tool.ToolErrorHandlerResult;
import dev.langchain4j.service.tool.ToolService;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.*;
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
    private ChatMemoryStore redisChatMemoryStore;
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

    // 通过护轨配置类来设置最大重试次数
    OutputGuardrailsConfig outputGuardrailsConfig = OutputGuardrailsConfig.builder()
            .maxRetries(3)
            .build();

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
        // 记忆窗口要足够大：Vue 工具生成一轮可能连续 20~30+ 条 assistant(tool_calls)+tool 消息，
        // 窗口太小会把首条 user 消息和历史裁掉，导致 gpt-5.5 等 OpenAI 系拒绝
        // （“缺少用户消息开头 / 消息序列被切坏”类 upstream invalid request）。
        MessageWindowChatMemory chatMemory = MessageWindowChatMemory.builder().id(appId).chatMemoryStore(redisChatMemoryStore).maxMessages(60).build();
        chatHistoryService.loadChatHistoryToMemory(appId, chatMemory, 60);
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
                            .toolArgumentsErrorHandler((error, context) -> ToolErrorHandlerResult.text("工具参数 JSON 解析失败：" + error.getMessage()
                                    + "。请检查后重试：① 每次工具调用都必须带全必填参数（writeFile 必须含 relativeFilePath 与 content；modifyFile 必须含 relativeFilePath、oldContent、newContent）；"
                                    + "② content 中双引号转义为 \\\"，换行用 \\n；③ 单块不超过1500字符，大文件分块写入时 append=false 开头、append=true 续写，且每个分块都要带相同的 relativeFilePath。"))
                            // hallucinatedToolNameStrategy（幻觉工具名称策略）配置了找不到工具时的处理策略，可以让框架帮我们处理 AI 出现幻觉的情况，比如告诉 AI “找不到工具”
                            // 防止 AI 一直无限循环调用工具，包括：
                            // 注意：该上限按「本轮生成内所有包含工具调用的模型回复轮数」累计，文本段落不会重置计数。
                            // Vue 工程合理生成往往需要 20+ 轮文件写入（本次日志实测 26 轮仍被判超限导致误报“生成失败”），
                            // 因此给足余量：既能容纳完整项目生成，又保留对病态无限循环的兜底。
                            .maxSequentialToolsInvocations(100)  // 最多连续 100 轮工具调用
                            // 调大对话记忆的容量，否则 AI 会中途断片儿，忘记已经生成了哪些文件 (调大了最大的token数）
                            // 尝试换其他的 AI 大模型、优化提示词（已优化）
                            .inputGuardrails(new PromptSafetyInputGuardrail())  // 添加输入护轨
                            /*
                                如果用了输出护轨，可能会导致流式输出的响应不及时，
                                等到 AI 输出结束才一起返回，所以如果为了追求流式输出效果，建议不要通过护轨的方式进行重试
                             */
//                            .outputGuardrails(new RetryOutputGuardrail())  // 添加输出护轨
                            .outputGuardrailsConfig(outputGuardrailsConfig)
                            .hallucinatedToolNameStrategy(toolExecutionRequest -> ToolExecutionResultMessage.from(toolExecutionRequest, "Error: there is no tool called " + toolExecutionRequest.name())).build();
            // HTML / 多文件：关闭思考，把额度留给页面代码。聊天区靠设计说明 + 文件进度。
            case HTML, MULTI_FILE ->
                    AiServices.builder(AiCodeGeneratorService.class)
                            .chatModel(ThinkingDisabledChatModels.wrap(provider.getChatModel()))
                            .streamingChatModel(ThinkingDisabledChatModels.wrap(provider.getStreamingChatModel()))
                            .inputGuardrails(new PromptSafetyInputGuardrail())
//                            .outputGuardrails(new RetryOutputGuardrail())
                            .outputGuardrailsConfig(outputGuardrailsConfig)
                            .chatMemory(chatMemory)
                            .build();
            default ->
                    throw new BusinessException(ErrorCode.SYSTEM_ERROR, "不支持的代码生成类型: " + codeGenType.getValue());
        };
    }

    private String getCacheKey(ModelTypeEnum modelTypeEnum, CodeGenTypeEnum codeGenType, Long appId) {
        return String.format("%s_%s_%s", modelTypeEnum.getModelName(), codeGenType.getValue(), appId);
    }

    /**
     * 失效指定 app 的 AI 服务缓存（下次请求会从 t_chat_history 重建干净记忆）。
     * <p>
     * 背景：流式工具调用中途异常/取消时，langchain4j 可能已在对话记忆里写入
     * “带 tool_calls 的 assistant 消息”，却来不及补对应 tool 结果（孤儿 tool_calls）。
     * 该记忆会随下一次请求发往模型，DeepSeek/OpenAI 会拒绝：
     * “An assistant message with 'tool_calls' must be followed by tool messages responding to each 'tool_call_id'”。
     * 因此在流异常/取消后失效缓存，避免复用损坏的记忆。
     */
    public void evictService(ModelTypeEnum modelType, CodeGenTypeEnum codeGenType, Long appId) {
        if (modelType == null || codeGenType == null || appId == null) {
            return;
        }
        String key = getCacheKey(modelType, codeGenType, appId);
        serviceCache.invalidate(key);
        log.info("流式生成异常/取消，已失效 AI 服务缓存: {}", key);
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
