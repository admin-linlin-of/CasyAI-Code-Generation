package com.casy.casyaicodemother.ai;

import com.casy.casyaicodemother.ai.model.HtmlCodeResult;
import com.casy.casyaicodemother.ai.model.MultiFileCodeResult;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;
import reactor.core.publisher.Flux;

public interface AiCodeGeneratorService {
    /**
     * 生成 HTML 代码
     *
     * @param userMessage 用户消息
     * @return 生成的代码结果
     */
    @SystemMessage(fromResource = "prompt/codegen-html-system-prompt.txt")
    HtmlCodeResult generateHtmlCode(String userMessage); // 结构化输出

    /**
     * 响应式生成 HTML 代码
     *
     * @param userMessage 用户消息
     * @return 生成的代码结果
     */
    @SystemMessage(fromResource = "prompt/codegen-html-system-prompt.txt")
    Flux<String> generateHtmlCodeStream(String userMessage); // 流式输出（旧入口）

    /**
     * HTML 流式生成（TokenStream）。
     * <p>
     * 必须用 TokenStream 而不能用 {@link #generateHtmlCodeStream}：只有前者提供
     * {@code onPartialThinking}，才能把模型 reasoning 推到聊天区。
     * Flux 旧入口仍保留给 {@code AiCodeGeneratorFacadeOld}。
     *
     * @param userMessage 用户消息
     * @return 含思考 token 与正文的流
     */
    @SystemMessage(fromResource = "prompt/codegen-html-system-prompt.txt")
    TokenStream generateHtmlCodeTokenStream(String userMessage);

    /**
     * 生成多文件代码
     *
     * @param userMessage 用户消息
     * @return 生成的代码结果
     */
    @SystemMessage(fromResource = "prompt/codegen-multi-file-system-prompt.txt")
    MultiFileCodeResult generateMultiFileCode(String userMessage);


    /**
     * 响应式生成多文件代码
     *
     * @param userMessage 用户消息
     * @return 生成的代码结果
     */
    @SystemMessage(fromResource = "prompt/codegen-multi-file-system-prompt.txt")
    Flux<String> generateMultiFileCodeStream(String userMessage);

    /**
     * 多文件流式生成（TokenStream）。
     * <p>
     * 与 {@link #generateHtmlCodeTokenStream} 相同：用 TokenStream 才能下发思考过程。
     *
     * @param userMessage 用户消息
     * @return 含思考 token 与正文的流
     */
    @SystemMessage(fromResource = "prompt/codegen-multi-file-system-prompt.txt")
    TokenStream generateMultiFileCodeTokenStream(String userMessage);

    /**
     * 生成 Vue 项目代码（流式）
     *
     * @param userMessage 用户消息
     * @return 生成过程的流式响应
     */
    @SystemMessage(fromResource = "prompt/codegen-vue-project-system-prompt.txt")
    TokenStream generateVueProjectCodeStream(@MemoryId long appId, @UserMessage String userMessage);
}
