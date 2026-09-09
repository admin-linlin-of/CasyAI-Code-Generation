package com.casy.casyaicodemother.core.handler;


import com.casy.casyaicodemother.model.entity.User;
import com.casy.casyaicodemother.service.ChatHistoryService;
import com.casy.casyaicodemother.util.ChatThinkingCodec;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;


@Slf4j
public class SimpleTextStreamHandler {

    /**
     * 处理传统流（HTML / MULTI_FILE）。
     * <p>
     * 思考与正文分流收集：思考只写入 {@code t_chat_history} 的 {@code <aiThinking>}，
     * 供前端回放；加载 LangChain 记忆时会 {@link ChatThinkingCodec#stripThinking}，
     * 不会把 reasoning 再喂给模型。控制事件（ping/file/code）不进对话正文。
     *
     * @param originFlux         原始流（可含 thinking JSON 与纯文本正文）
     * @param chatHistoryService 聊天历史服务
     * @param appId              应用 ID
     * @param userMessageId      用户消息 id
     * @param loginUser          登录用户
     * @return 透传后的流；空响应时追加一条「生成失败」文本给前端
     */
    public Flux<String> handle(Flux<String> originFlux,
                               ChatHistoryService chatHistoryService,
                               long appId,
                               long userMessageId,
                               User loginUser) {
        StringBuilder aiResponseBuilder = new StringBuilder();
        StringBuilder thinkingBuilder = new StringBuilder();
        return originFlux
                .doOnNext(chunk -> {
                    // ping / file / code 是控制事件，不进对话历史正文
                    if (chunk != null && chunk.startsWith("{")
                            && (chunk.contains("\"t\":\"ping\"")
                            || chunk.contains("\"t\":\"file\"")
                            || chunk.contains("\"t\":\"code\""))) {
                        return;
                    }
                    // 思考 JSON 仍向下游透传（doOnNext 不 filter），仅从落库正文中剔除
                    if (chunk != null && chunk.startsWith("{") && chunk.contains("\"t\":\"thinking\"")) {
                        try {
                            JSONObject obj = JSONUtil.parseObj(chunk);
                            thinkingBuilder.append(StrUtil.nullToEmpty(obj.getStr("c")));
                        } catch (Exception ignored) {
                            // 解析失败则忽略该思考片段，不影响正文
                        }
                        return;
                    }
                    aiResponseBuilder.append(chunk);
                })
                .concatWith(Mono.defer(() -> {
                    String aiResponse = aiResponseBuilder.toString();
                    if (StrUtil.isBlank(aiResponse)) {
                        String detail = "模型未返回任何内容";
                        chatHistoryService.saveErrorMessage(appId, userMessageId, detail, loginUser);
                        return Mono.just("生成失败：" + detail);
                    }
                    // composeForSave 只服务 UI 回放；记忆加载路径会剥掉 <aiThinking>
                    chatHistoryService.saveAiMessage(appId, userMessageId,
                            ChatThinkingCodec.composeForSave(thinkingBuilder.toString(), aiResponse),
                            loginUser);
                    return Mono.empty();
                }))
                .doOnError(error -> chatHistoryService.saveErrorMessage(appId, userMessageId, error.getMessage(), loginUser));
    }

}
