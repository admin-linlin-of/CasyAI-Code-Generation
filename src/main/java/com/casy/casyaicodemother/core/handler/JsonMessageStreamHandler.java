package com.casy.casyaicodemother.core.handler;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.casy.casyaicodemother.ai.model.*;
import com.casy.casyaicodemother.ai.tools.BaseTool;
import com.casy.casyaicodemother.ai.tools.ToolManager;
import com.casy.casyaicodemother.exception.BusinessException;
import com.casy.casyaicodemother.exception.ErrorCode;
import com.casy.casyaicodemother.model.entity.User;
import com.casy.casyaicodemother.model.enums.StreamMessageTypeEnum;
import com.casy.casyaicodemother.service.ChatHistoryService;
import com.casy.casyaicodemother.util.ChatThinkingCodec;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.*;

/**
 * JSON 消息流处理器
 * 处理 VUE_PROJECT 类型的复杂流式响应，包含工具调用信息
 */
@Slf4j
@Component
public class JsonMessageStreamHandler {

    @Resource
    private ToolManager toolManager;

    /**
     * 处理 TokenStream（VUE_PROJECT）
     * 解析 JSON 消息并重组为完整的响应格式
     *
     * @param originFlux         原始流
     * @param chatHistoryService 聊天历史服务
     * @param appId              应用ID
     * @param userMessageId      父消息ID
     * @param loginUser          登录用户
     * @return 处理后的流
     */
    public Flux<String> handle(Flux<String> originFlux,
                               ChatHistoryService chatHistoryService,
                               long appId,
                               long userMessageId,
                               User loginUser) {
        // 收集数据用于生成后端记忆格式
        StringBuilder chatHistoryStringBuilder = new StringBuilder();
        StringBuilder thinkingHistoryBuilder = new StringBuilder();
        // 用于跟踪已经见过的工具ID，判断是否是第一次调用
        Set<String> seenToolIds = new HashSet<>();
        return originFlux
                // 1 个 chunk → List<String>（1~2 条）→ 每条各发 1 次 SSE
                .concatMap(chunk -> Flux.fromIterable(
                        handleJsonMessageChunks(chunk, chatHistoryStringBuilder, thinkingHistoryBuilder, seenToolIds)))
                .filter(StrUtil::isNotEmpty)// 过滤空串
                // 流结束后统一收尾：空响应写入错误并推送到 SSE，避免 doOnComplete 抛异常导致前端收不到错误
                .concatWith(Mono.defer(() -> {
                    String aiResponse = chatHistoryStringBuilder.toString();
                    if (StrUtil.isBlank(aiResponse)) {
                        String detail = "模型未返回任何内容";
                        chatHistoryService.saveErrorMessage(appId, userMessageId, detail, loginUser);
                        // 作为普通文本 chunk 推送，前端 onmessage 可实时展示
                        return Mono.just("生成失败：" + detail);
                    }
                    chatHistoryService.saveAiMessage(appId, userMessageId,
                            ChatThinkingCodec.composeForSave(thinkingHistoryBuilder.toString(), aiResponse),
                            loginUser);
                    return Mono.empty();
                }))
                // 流中途异常时持久化错误，Controller 层 onErrorResume 负责推送给前端
                .doOnError(error -> {
                    // 异常中断（如达到工具调用上限、服务重启、网络异常）时，已产生的深度思考/文本/工具摘要
                    // 此前只缓存在内存 StringBuilder 中，若不落库，用户回首页再进对话将看不到任何生成记录。
                    // 因此先把已生成的部分内容存为一条 ai 消息（思考用 <aiThinking> 包裹、工具用标签包裹，
                    // 与正常收尾格式一致，前端按同一规则还原），再存错误行。
                    String aiResponse = chatHistoryStringBuilder.toString();
                    String thinking = thinkingHistoryBuilder.toString();
                    if (StrUtil.isNotBlank(aiResponse) || StrUtil.isNotBlank(thinking)) {
                        try {
                            chatHistoryService.saveAiMessage(appId, userMessageId,
                                    ChatThinkingCodec.composeForSave(thinking, aiResponse), loginUser);
                        } catch (Exception saveException) {
                            log.error("保存异常中断前的部分生成内容失败，appId: {}", appId, saveException);
                        }
                    }
                    chatHistoryService.saveErrorMessage(appId, userMessageId, error.getMessage(), loginUser);
                });
    }

    /**
     * 解析 TokenStream 消息块，可能产生多条 SSE 输出（如聊天摘要 + 代码面板 file 事件）。
     *
     * @param chunk                    消息块
     * @param chatHistoryStringBuilder 对话记收集器
     * @param seenToolIds              已经出现过的工具
     * @return ai消息
     */
    private List<String> handleJsonMessageChunks(String chunk,
                                               StringBuilder chatHistoryStringBuilder,
                                               StringBuilder thinkingHistoryBuilder,
                                               Set<String> seenToolIds) {
        // 解析 JSON
        StreamMessage streamMessage = JSONUtil.toBean(chunk, StreamMessage.class);
        StreamMessageTypeEnum typeEnum = StreamMessageTypeEnum.getEnumByValue(streamMessage.getType());
        if (typeEnum == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "消息类型异常！");
        }
        return switch (typeEnum) {
            case AI_RESPONSE -> {
                AiResponseMessage aiResponseMessage = JSONUtil.toBean(chunk, AiResponseMessage.class);
                String data = aiResponseMessage.getData();
                // 直接拼接响应
                chatHistoryStringBuilder.append(data);
                yield List.of(data);
            }
            case AI_THINKING -> {
                AiThinkingMessage aiThinkingMessage = JSONUtil.toBean(chunk, AiThinkingMessage.class);
                String data = aiThinkingMessage.getData();
                thinkingHistoryBuilder.append(data);
                yield List.of(JSONUtil.toJsonStr(Map.of("c", data, "t", "thinking")));
            }
            case TOOL_REQUEST -> {
                ToolRequestMessage toolRequestMessage = JSONUtil.toBean(chunk, ToolRequestMessage.class);
                String toolId = toolRequestMessage.getId();
                String toolName = toolRequestMessage.getName();
                // 检查是否是第一次看到这个工具 ID
                if (toolId != null && !seenToolIds.contains(toolId)) {
                    // 第一次调用这个工具，记录 ID 并返回工具信息
                    seenToolIds.add(toolId);
                    // 根据工具名称获取工具实例
                    BaseTool tool = toolManager.getTool(toolName);
                    // 返回格式化的工具调用信息
                    String toolRequestResponse = tool == null
                            ? String.format("\n\n[Tool] %s\n\n", StrUtil.blankToDefault(toolName, "unknown"))
                            : tool.generateToolRequestResponse();
                    yield List.of(toolRequestResponse);
                }
                yield List.of();
            }
            case TOOL_EXECUTED -> buildToolExecutedOutputs(chunk, chatHistoryStringBuilder);
            default -> {
                log.error("不支持的消息类型：{}", typeEnum);
                yield List.of();
            }
        };
    }

    /**
     * writeFile 工具执行完成后的双路输出处理。
     * <p>
     * LangChain4j TokenStream 推送一条 {@code TOOL_EXECUTED} JSON，本方法将其拆成两条 SSE 字符串：
     * <ol>
     *   <li><b>聊天区</b>：Markdown 摘要（仅路径 + 成功/失败），写入 {@code chatHistoryStringBuilder} 持久化</li>
     *   <li><b>代码预览区</b>：{@code {"t":"file",...}} JSON，前端 {@code useProjectFileStore.ingestFileEvent} 消费</li>
     * </ol>
     * 两条输出由上层 {@link #handle} 的 {@code concatMap} 依次推送给前端，Controller 对含 {@code "t"} 的 JSON 原样透传。
     *
     * @param chunk                    TokenStream 原始 JSON 块（type=TOOL_EXECUTED）
     * @param chatHistoryStringBuilder 对话历史收集器，仅追加聊天摘要，不含文件正文
     * @return 1~2 条 SSE 字符串：必有聊天摘要；成功且有路径时追加 file 事件
     */
    private List<String> buildToolExecutedOutputs(String chunk, StringBuilder chatHistoryStringBuilder) {
        // ── 步骤 1：反序列化工具执行结果 ──
        // ToolExecutedMessage 含 failed 标志；arguments 为 writeFile 入参 JSON 字符串
        ToolExecutedMessage toolExecutedMessage = JSONUtil.toBean(chunk, ToolExecutedMessage.class);
        String toolName = toolExecutedMessage.getName();
        JSONObject jsonObject = parseToolArguments(toolExecutedMessage.getArguments());
        BaseTool tool = toolManager.getTool(toolName);

        // ── 步骤 2：提取 writeFile 参数 ──
        // relativeFilePath：相对项目根的路径，如 src/App.vue
        // content：本次写入的文本（大文件可能分块，见 append）
        // append：true 表示追加到已有文件，false 表示覆盖
        String relativeFilePath = jsonObject.getStr("relativeFilePath");
        boolean failed = Boolean.TRUE.equals(toolExecutedMessage.getFailed());

        // ── 步骤 3：构造聊天区摘要（不含 content，避免 SSE 块过大） ──
        String status = failed ? "失败" : "成功";
        String chatOutput = String.format("\n\n[工具调用] 写入文件 `%s` %s\n\n", relativeFilePath, status);
        // 写入历史：刷新页面后聊天区仍能看到工具调用记录
        String result = buildToolExecutedResult(tool, toolName, jsonObject, toolExecutedMessage, failed);
        String tagName = getToolExecutedTagName(toolName);
        chatOutput = String.format("\n\n<%s>%s</%s>\n\n", tagName, result, tagName);
        chatHistoryStringBuilder.append(chatOutput);

        // ── 步骤 4：组装 SSE 输出列表（至少 1 条聊天摘要） ──
        List<String> outputs = new ArrayList<>();
        outputs.add(chatOutput);

        // ── 步骤 5：成功时追加代码预览专用 file 事件 ──
        // 失败或无路径时不推 file 事件，前端代码面板不做无效更新
        if (!failed && "writeFile".equals(toolName) && StrUtil.isNotBlank(relativeFilePath)) {
            // LinkedHashMap 保证 JSON 字段顺序稳定，便于调试
            String content = jsonObject.getStr("content", "");
            boolean append = Boolean.TRUE.equals(jsonObject.getBool("append"));
            Map<String, Object> fileEvent = new LinkedHashMap<>();
            fileEvent.put("t", "file");           // 事件类型，AppController 识别后原样透传
            fileEvent.put("path", relativeFilePath);
            fileEvent.put("content", content);    // 完整写入内容，供 Monaco 实时展示
            fileEvent.put("append", append);      // 前端按 append 决定拼接还是覆盖
            fileEvent.put("done", true);          // 工具单次执行结束；分块写入时每次 TOOL_EXECUTED 均为 true
            outputs.add(JSONUtil.toJsonStr(fileEvent)); // 「双路」= 同一条 SSE 连接、两次 onmessage，两种用途。用 List 只是 Java 里把两条字符串一起返回，后面 concatMap 会拆成两次推送。
        }
        return outputs;
    }

    private JSONObject parseToolArguments(String arguments) {
        if (StrUtil.isBlank(arguments)) {
            return new JSONObject();
        }
        try {
            return JSONUtil.parseObj(arguments);
        } catch (Exception e) {
            log.warn("工具参数解析失败，arguments={}", arguments, e);
            return new JSONObject();
        }
    }

    private String buildToolExecutedResult(BaseTool tool,
                                           String toolName,
                                           JSONObject arguments,
                                           ToolExecutedMessage toolExecutedMessage,
                                           boolean failed) {
        String result;
        if (tool == null) {
            result = String.format("[工具调用] %s", StrUtil.blankToDefault(toolName, "unknown"));
        } else {
            result = tool.generateToolExecutedResult(arguments);
        }
        if (failed) {
            String errorMessage = StrUtil.blankToDefault(toolExecutedMessage.getResult(), "工具执行失败");
            return result + "\n" + errorMessage;
        }
        return result;
    }

    private String getToolExecutedTagName(String toolName) {
        return switch (StrUtil.blankToDefault(toolName, "")) {
            case "writeFile" -> "fileWrite";
            case "modifyFile" -> "fileModify";
            case "readFile" -> "fileRead";
            case "deleteFile" -> "fileDelete";
            case "readDir" -> "dirRead";
            default -> "toolCall";
        };
    }
}
