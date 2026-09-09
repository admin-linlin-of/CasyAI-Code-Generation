package com.casy.casyaicodemother.core.handler;


import com.casy.casyaicodemother.model.entity.User;
import com.casy.casyaicodemother.service.ChatHistoryService;
import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;


@Slf4j
public class SimpleTextStreamHandler {

    /**
     * 处理传统流（HTML, MULTI_FILE）
     * 直接收集完整的文本响应
     *
     * @param originFlux         原始流
     * @param chatHistoryService 聊天历史服务
     * @param appId              应用ID
     * @param userMessageId      用户消息id
     * @param loginUser          登录用户
     *
     * @return 处理后的流
     */
    public Flux<String> handle(Flux<String> originFlux,
                               ChatHistoryService chatHistoryService,
                               long appId,
                               long userMessageId,
                               User loginUser) {
        StringBuilder aiResponseBuilder = new StringBuilder();
        return originFlux
                .doOnNext(chunk -> {
                    // ping / file / code 是控制事件，不进对话历史正文
                    if (chunk != null && chunk.startsWith("{")
                            && (chunk.contains("\"t\":\"ping\"")
                            || chunk.contains("\"t\":\"file\"")
                            || chunk.contains("\"t\":\"code\""))) {
                        return;
                    }
                    aiResponseBuilder.append(chunk);
                })
                // 流结束后统一收尾：空响应写入错误并推送到 SSE，避免 doOnComplete 抛异常导致前端收不到错误
                .concatWith(Mono.defer(() -> {
                    String aiResponse = aiResponseBuilder.toString();
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

}
