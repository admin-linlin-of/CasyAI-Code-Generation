package com.casy.casyaicodemother.core.handler;

import com.casy.casyaicodemother.model.entity.User;
import com.casy.casyaicodemother.model.enums.CodeGenTypeEnum;
import com.casy.casyaicodemother.service.ChatHistoryService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

/**
 * 流处理器执行器
 * 根据代码生成类型创建合适的流处理器：
 * 1. HTML / MULTI_FILE：纯文本 Flux -> SimpleTextStreamHandler（请求侧已关思考）
 * 2. VUE_PROJECT：工具调用 JSON 流 -> JsonMessageStreamHandler
 */
@Slf4j
@Component
public class StreamHandlerExecutor {

    @Resource
    private JsonMessageStreamHandler jsonMessageStreamHandler;

    public Flux<String> doExecute(Flux<String> originFlux,
                                  ChatHistoryService chatHistoryService,
                                  long appId,
                                  long userMessageId,
                                  User loginUser,
                                  CodeGenTypeEnum codeGenTypeEnum) {
        return switch (codeGenTypeEnum) {
            case VUE_PROJECT -> // 使用注入的组件实例
                    jsonMessageStreamHandler.handle(originFlux, chatHistoryService, appId, userMessageId, loginUser);
            // HTML / 多文件：纯文本正文；SimpleTextStreamHandler 无状态，按次 new 即可
            case HTML, MULTI_FILE ->
                    new SimpleTextStreamHandler().handle(originFlux, chatHistoryService, appId, userMessageId, loginUser);
        };
    }
}
