package com.casy.casyaicodemother.core.handler;


import com.casy.casyaicodemother.model.entity.User;
import com.casy.casyaicodemother.service.ChatHistoryService;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;


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
                // 收集AI响应
                .doOnNext(aiResponseBuilder::append)
                // 6. 添加AI消息到对话历史
                .doOnComplete(() -> chatHistoryService.saveAiMessage(appId, userMessageId, aiResponseBuilder.toString(), loginUser))
                // 7. 添加AI异常消息到对话历史
                .doOnError(error -> chatHistoryService.saveErrorMessage(appId, userMessageId, error.getMessage(), loginUser));
    }

}
