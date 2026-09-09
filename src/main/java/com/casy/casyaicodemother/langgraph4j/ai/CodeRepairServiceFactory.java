package com.casy.casyaicodemother.langgraph4j.ai;

import com.casy.casyaicodemother.ai.ModelProvider;
import com.casy.casyaicodemother.ai.tools.RepairingToolExecutor;
import com.casy.casyaicodemother.ai.tools.ToolManager;
import com.casy.casyaicodemother.exception.BusinessException;
import com.casy.casyaicodemother.exception.ErrorCode;
import com.casy.casyaicodemother.model.enums.ModelTypeEnum;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.tool.AiServiceTool;
import dev.langchain4j.service.tool.ToolErrorHandlerResult;
import dev.langchain4j.service.tool.ToolService;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
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
 * 代码修复 AI 服务工厂：独立提示词、独立记忆（appId:repair），工具仍用真实 appId 写文件。
 */
@Slf4j
@Component
public class CodeRepairServiceFactory {

    private final Cache<ModelTypeEnum, CodeRepairService> serviceCache = Caffeine.newBuilder()
            .maximumSize(20)
            .expireAfterAccess(Duration.ofMinutes(30))
            .build();

    @Resource
    private ChatMemoryStore redisChatMemoryStore;

    @Resource
    private ToolManager toolManager;

    @Resource
    private List<ModelProvider> modelProviders;

    private Map<ModelTypeEnum, ModelProvider> providerMap;

    @PostConstruct
    public void init() {
        providerMap = modelProviders.stream().collect(Collectors.toMap(ModelProvider::getType, Function.identity()));
    }

    public CodeRepairService getService(ModelTypeEnum modelType) {
        if (modelType == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "模型类型为空");
        }
        return serviceCache.get(modelType, this::createService);
    }

    /**
     * 失效修复服务缓存（同 {@link com.casy.casyaicodemother.ai.AiCodeGeneratorServiceFactory#evictService}，
     * 修复流同样会因中途异常留下“孤儿 tool_calls”记忆）。
     */
    public void evictService(ModelTypeEnum modelType) {
        if (modelType == null) {
            return;
        }
        serviceCache.invalidate(modelType);
        log.info("修复流异常/取消，已失效修复服务缓存: {}", modelType);
    }

    private CodeRepairService createService(ModelTypeEnum modelType) {
        ModelProvider provider = providerMap.get(modelType);
        if (provider == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "不支持的模型类型：" + modelType.getModelName());
        }
        return AiServices.builder(CodeRepairService.class)
                .streamingChatModel(provider.getStreamingChatModel())
                .chatMemoryProvider(memoryId -> MessageWindowChatMemory.builder()
                        .id(memoryId + ":repair")
                        .chatMemoryStore(redisChatMemoryStore)
                        // 修复同样多轮读写文件，窗口需容纳 assistant(tool_calls)+tool 配对
                        .maxMessages(60)
                        .build())
                .tools(wrapToolsWithRepair(toolManager.getAllTools()))
                .toolArgumentsErrorHandler((error, context) -> ToolErrorHandlerResult.text(
                        "工具参数 JSON 解析失败：" + error.getMessage()))
                .hallucinatedToolNameStrategy(request ->
                        ToolExecutionResultMessage.from(request, "Error: there is no tool called " + request.name()))
                // 修复可能涉及多轮文件写入/校验；上限给足余量（与 AiCodeGeneratorServiceFactory 一致），
                // 避免合法修复因 langchain4j 顺序工具调用计数超限而被误判为失败。
                .maxSequentialToolsInvocations(100)
                .build();
    }

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
}
