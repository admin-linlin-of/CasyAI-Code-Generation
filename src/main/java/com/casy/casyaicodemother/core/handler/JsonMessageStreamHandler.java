package com.casy.casyaicodemother.core.handler;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.casy.casyaicodemother.ai.model.AiResponseMessage;
import com.casy.casyaicodemother.ai.model.AiThinkingMessage;
import com.casy.casyaicodemother.ai.model.StreamMessage;
import com.casy.casyaicodemother.ai.model.ToolExecutedMessage;
import com.casy.casyaicodemother.ai.model.ToolRequestMessage;
import com.casy.casyaicodemother.exception.BusinessException;
import com.casy.casyaicodemother.exception.ErrorCode;
import com.casy.casyaicodemother.model.entity.User;
import com.casy.casyaicodemother.model.enums.StreamMessageTypeEnum;
import com.casy.casyaicodemother.service.ChatHistoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * JSON 消息流处理器
 * 处理 VUE_PROJECT 类型的复杂流式响应，包含工具调用信息
 */
@Slf4j
@Component
public class JsonMessageStreamHandler {

    /**
     * 处理 TokenStream（VUE_PROJECT）
     * 解析 JSON 消息并重组为完整的响应格式
     *
     * @param originFlux            原始流
     * @param chatHistoryService    聊天历史服务
     * @param appId                 应用ID
     * @param userMessageId         父消息ID
     * @param loginUser             登录用户
     * @return 处理后的流
     */
    public Flux<String> handle(Flux<String> originFlux,
                               ChatHistoryService chatHistoryService,
                               long appId,
                               long userMessageId,
                               User loginUser) {
        // 收集数据用于生成后端记忆格式
        StringBuilder chatHistoryStringBuilder = new StringBuilder();
        // 用于跟踪已经见过的工具ID，判断是否是第一次调用
        Set<String> seenToolIds = new HashSet<>();
        return originFlux
                .map(chunk -> {
                    // 解析每个 JSON 消息块
                    return handleJsonMessageChunk(chunk, chatHistoryStringBuilder, seenToolIds);
                })
                .filter(StrUtil::isNotEmpty) // 过滤空串
                // 流结束后统一收尾：空响应写入错误并推送到 SSE，避免 doOnComplete 抛异常导致前端收不到错误
                .concatWith(Mono.defer(() -> {
                    String aiResponse = chatHistoryStringBuilder.toString();
                    if (StrUtil.isBlank(aiResponse)) {
                        String detail = "模型未返回任何内容";
                        chatHistoryService.saveErrorMessage(appId, userMessageId, detail, loginUser);
                        // 作为普通文本 chunk 推送，前端 onmessage 可实时展示
                        return Mono.just("生成失败：" + detail);
                    }
                    chatHistoryService.saveAiMessage(appId, userMessageId, aiResponse, loginUser);
                    return Mono.empty();
                }))
                // 流中途异常时持久化错误，Controller 层 onErrorResume 负责推送给前端
                .doOnError(error -> chatHistoryService.saveErrorMessage(appId, userMessageId, error.getMessage(), loginUser));
    }

    /**
     * 解析并收集 TokenStream 数据
     *
     * @param chunk 消息块
     * @param chatHistoryStringBuilder 对话记收集器
     * @param seenToolIds 已经出现过的工具
     * @return ai消息
     */
    private String handleJsonMessageChunk(String chunk, StringBuilder chatHistoryStringBuilder, Set<String> seenToolIds) {
        // 解析 JSON
        StreamMessage streamMessage = JSONUtil.toBean(chunk, StreamMessage.class);
        StreamMessageTypeEnum typeEnum = StreamMessageTypeEnum.getEnumByValue(streamMessage.getType());
        if (typeEnum == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "消息类型异常！");
        }
        switch (typeEnum) {
            case AI_RESPONSE -> {
                AiResponseMessage aiResponseMessage = JSONUtil.toBean(chunk, AiResponseMessage.class);
                String data = aiResponseMessage.getData();
                // 直接拼接响应
                chatHistoryStringBuilder.append(data);
                return data;
            }
            case AI_THINKING -> {
                AiThinkingMessage aiThinkingMessage = JSONUtil.toBean(chunk, AiThinkingMessage.class);
                String data = aiThinkingMessage.getData();
                // 深度思考仅实时推前端展示，不写入对话历史
                return JSONUtil.toJsonStr(Map.of("c", data, "t", "thinking"));
            }
            case TOOL_REQUEST -> {
                ToolRequestMessage toolRequestMessage = JSONUtil.toBean(chunk, ToolRequestMessage.class);
                String toolId = toolRequestMessage.getId();
                // 检查是否是第一次看到这个工具 ID
                if (toolId != null && !seenToolIds.contains(toolId)) {
                    // 第一次调用这个工具，记录 ID 并完整返回工具信息
                    seenToolIds.add(toolId);
                    return "\n\n[选择工具] 写入文件\n\n";
                } else {
                    // 不是第一次调用这个工具，直接返回空？
                    // TODO 感觉不太对呢？
                    return "";
                }
            }
            case TOOL_EXECUTED -> {
                ToolExecutedMessage toolExecutedMessage = JSONUtil.toBean(chunk, ToolExecutedMessage.class);
                JSONObject jsonObject = JSONUtil.parseObj(toolExecutedMessage.getArguments());
                String relativeFilePath = jsonObject.getStr("relativeFilePath");
                boolean failed = Boolean.TRUE.equals(toolExecutedMessage.getFailed());
                // 聊天区只推送路径摘要，不嵌入完整文件内容，避免单次 SSE 块过大导致前端无法打字机展示
                String status = failed ? "失败" : "成功";
                String output = String.format("\n\n[工具调用] 写入文件 `%s` %s\n\n", relativeFilePath, status);
                chatHistoryStringBuilder.append(output);
                return output;
            }
            default -> {
                log.error("不支持的消息类型：{}", typeEnum);
                return "";
            }
        }
    }

}
