package com.casy.casyaicodemother.core;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.casy.casyaicodemother.ai.AiCodeGeneratorServiceFactory;
import com.casy.casyaicodemother.ai.model.*;
import com.casy.casyaicodemother.core.parser.CodeParserExecutor;
import com.casy.casyaicodemother.core.save.CodeFileSaverExecutor;
import com.casy.casyaicodemother.exception.BusinessException;
import com.casy.casyaicodemother.exception.ErrorCode;
import com.casy.casyaicodemother.langgraph4j.ai.CodeRepairServiceFactory;
import com.casy.casyaicodemother.model.enums.CodeGenTypeEnum;
import com.casy.casyaicodemother.model.enums.ModelTypeEnum;
import com.casy.casyaicodemother.service.AppVersionService;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.model.chat.response.PartialThinking;
import dev.langchain4j.service.TokenStream;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.io.File;

@Service
@Slf4j
public class AiCodeGeneratorFacade {

    @Resource
    @Lazy
    private AppVersionService appVersionService;

    @Resource
    private AiCodeGeneratorServiceFactory aiCodeGeneratorServiceFactory;

    @Resource
    private CodeRepairServiceFactory codeRepairServiceFactory;

    /**
     * 统一入口：根据类型生成并保存代码
     *
     * @param userMessage     用户提示词
     * @param codeGenTypeEnum 生成类型
     * @return 保存的目录
     */
    public File generateAndSaveCode(String userMessage, CodeGenTypeEnum codeGenTypeEnum, ModelTypeEnum modelTypeEnum, Long appId) {
        if (codeGenTypeEnum == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "生成类型为空");
        }
        return switch (codeGenTypeEnum) {
            case HTML -> {
                HtmlCodeResult htmlCodeResult = aiCodeGeneratorServiceFactory.getService(modelTypeEnum, codeGenTypeEnum, appId).generateHtmlCode(userMessage);
                yield CodeFileSaverExecutor.executeSaver(htmlCodeResult, CodeGenTypeEnum.HTML, appId);
            }
            case MULTI_FILE -> {
                MultiFileCodeResult multiFileCodeResult = aiCodeGeneratorServiceFactory.getService(modelTypeEnum, codeGenTypeEnum, appId).generateMultiFileCode(userMessage);
                yield CodeFileSaverExecutor.executeSaver(multiFileCodeResult, CodeGenTypeEnum.MULTI_FILE, appId);
            }
            default -> {
                String errorMessage = "不支持的生成类型：" + codeGenTypeEnum.getValue();
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, errorMessage);
            }
        };
    }

    /**
     * 统一入口：根据类型生成并保存代码 (流式)
     *
     * @param userMessage     用户提示词
     * @param codeGenTypeEnum 生成类型
     * @return 保存的目录
     */
    public Flux<String> generateAndSaveCodeStream(String userMessage, CodeGenTypeEnum codeGenTypeEnum, ModelTypeEnum modelTypeEnum, Long appId, Long userMessageId, String versionDir) {
        if (codeGenTypeEnum == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "生成类型为空");
        }
        return switch (codeGenTypeEnum) {
            case HTML -> {
                Flux<String> stringFlux = aiCodeGeneratorServiceFactory.getService(modelTypeEnum, codeGenTypeEnum, appId).generateHtmlCodeStream(userMessage);
                yield processCodeStream(stringFlux, CodeGenTypeEnum.HTML, modelTypeEnum, appId, userMessageId);
            }
            case MULTI_FILE -> {
                Flux<String> stringFlux = aiCodeGeneratorServiceFactory.getService(modelTypeEnum, codeGenTypeEnum, appId).generateMultiFileCodeStream(userMessage);
                yield processCodeStream(stringFlux, CodeGenTypeEnum.MULTI_FILE, modelTypeEnum, appId, userMessageId);
            }
            case VUE_PROJECT -> {
                // 流开始前：把 modelType、userMessageId 放进 CodeGenContextHolder，
                // 供 FileWriteTool 首次写文件时 createCodeVersion 使用（工具本身只能拿到 appId）
                CodeGenContextHolder.set(appId, modelTypeEnum, userMessageId, versionDir);
                TokenStream tokenStream = aiCodeGeneratorServiceFactory.getService(modelTypeEnum, codeGenTypeEnum, appId).generateVueProjectCodeStream(appId, userMessage);
                // 流结束（成功/失败/取消）后清理上下文，避免内存泄漏
                yield processTokenStream(tokenStream).doFinally(signal -> CodeGenContextHolder.remove(appId));
            }
            default -> {
                String errorMessage = "不支持的生成类型：" + codeGenTypeEnum.getValue();
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, errorMessage);
            }
        };
    }

    /**
     * 代码修复流：写入已有 versionDir，不新建版本；使用独立 CodeRepairService。
     */
    public Flux<String> repairCodeStream(String repairMessage, CodeGenTypeEnum codeGenType,
                                         ModelTypeEnum modelType, Long appId, Long userMessageId, String versionDir) {
        CodeGenContextHolder.set(appId, modelType, userMessageId, versionDir, codeGenType);
        TokenStream tokenStream = codeRepairServiceFactory.getService(modelType).repairCode(appId, repairMessage);
        return processTokenStream(tokenStream).doFinally(signal -> CodeGenContextHolder.remove(appId));
    }


    /**
     * 流式收集模型输出，流结束后解析并落盘。
     * <p>
     * 版本目录仍由 {@link AppVersionService#createCodeVersion} 生成，不在此处改写 versionDir 规则。
     * 保存失败必须向下游抛出，避免调用方把「流结束」误当成「文件已生成」。
     */
    private Flux<String> processCodeStream(Flux<String> codeStream, CodeGenTypeEnum codeGenType, ModelTypeEnum modelTypeEnum, Long appId, Long userMessageId) {
        StringBuilder codeBuilder = new StringBuilder();
        return codeStream.doOnNext(codeBuilder::append).concatWith(Flux.defer(() -> {
            String completeCode = codeBuilder.toString();
            log.info("AI最终的响应：{}", completeCode);
            if (StrUtil.isBlank(completeCode)) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "模型未返回代码内容，无法保存文件");
            }
            // 使用执行器解析代码
            Object parsedResult = CodeParserExecutor.executeParser(codeGenType, completeCode);
            // 添加新增版本
            String versionDir;
            if (appId == 741582369L) {
                versionDir = "v1";
            } else {
                versionDir = appVersionService.createCodeVersion(appId, modelTypeEnum, userMessageId);
            }

            // 使用执行器保存代码
            File savedDir = CodeFileSaverExecutor.executeSaver(parsedResult, codeGenType, appId, versionDir);
            log.info("保存成功，路径为：{}", savedDir.getAbsolutePath());
            return Flux.empty();
        }));
    }

    /**
     * 将 TokenStream 转换为 Flux&lt;String&gt;，实时推送 AI 文本响应与工具调用各阶段事件。
     * <p>
     * 工具调用完整生命周期（参考 LangChain4j Tools 文档）：
     * <pre>
     * 1. onPartialThinking      → AI 深度思考/reasoning token（需模型开启 returnThinking）
     * 2. onPartialResponse      → AI 生成普通文本 token（非工具调用阶段）
     * 3. onPartialToolCall      → AI 流式输出工具调用请求（工具名 + 参数 JSON 片段）
     * 4. onToolExecuted         → AI Service 执行完工具后回调（含完整请求 + 执行结果）
     * 5. onCompleteResponse     → 本轮 AI 响应全部结束（可能含多轮工具调用）
     * 6. onError                → 流式过程中发生异常
     * </pre>
     * 注意：TokenStream（AI Service 高层 API）不提供 onCompleteToolCall，
     * 该回调仅存在于底层 StreamingChatModel 的 StreamingChatResponseHandler 中。
     * 完整工具请求信息通过 onToolExecuted 的 request() 获取。
     *
     * <h3>工具调用消息流程图：</h3>
     * <img src="../../../../../javadoc/doc-files/VUE项目生成流程.png" alt="登录验证流程" width="700"  height="500"/>
     *
     * @param tokenStream TokenStream 对象
     * @return Flux&lt;String&gt; JSON 格式的流式响应
     */
    private Flux<String> processTokenStream(TokenStream tokenStream) {
        return Flux.create(sink -> {
            tokenStream
                    // 阶段1：AI 普通文本流式输出
                    // 当 LLM 生成文本内容（非工具调用）时，每产生一个 token 触发一次
                    // 前端 type=ai_response，可实时拼接展示 AI 回复
                    // 阶段0：AI 深度思考流式输出（DeepSeek reasoning 等模型）
                    // 每产生一个 thinking token 触发一次，前端 type=ai_thinking 单独展示
                    .onPartialThinking((PartialThinking partialThinking) -> {
                        sink.next(JSONUtil.toJsonStr(new AiThinkingMessage(partialThinking.text())));
                    })
                    // 阶段1：AI 普通文本流式输出
                    .onPartialResponse(partialResponse -> {
                        sink.next(JSONUtil.toJsonStr(new AiResponseMessage(partialResponse)));
                    })
                    // 阶段2：工具调用请求流式输出（仅部分 LLM 支持，如 OpenAI）
                    // LLM 决定调用工具后，以流式方式输出工具名和参数 JSON 片段
                    // 同一工具调用会多次触发，index 标识第几个工具，partialArguments 为参数片段
                    // 所有片段拼接后应形成完整 JSON，如 {"city":"London"}
                    // 部分提供商（Bedrock/Google/Mistral/Ollama）不支持流式工具调用，此回调不会触发
                    // 前端 type=tool_request, partial=true，可展示"正在调用 xxx 工具..."
                    .onPartialToolCall(partialToolCall -> {
                        ToolRequestMessage message = new ToolRequestMessage(
                                partialToolCall.index(),
                                partialToolCall.id(),
                                partialToolCall.name(),
                                partialToolCall.partialArguments(),
                                true
                        );
                        sink.next(JSONUtil.toJsonStr(message));
                    })
                    // 阶段3：工具执行完成
                    // AI Service 在收到完整工具调用请求后自动执行对应 @Tool 方法，执行完毕后触发
                    // request() 含完整工具请求（id/name/arguments），result() 含执行返回值
                    // 若启用 executeToolsConcurrently()，多个工具会并发执行，各自独立触发此回调
                    // 前端 type=tool_executed，可展示工具执行结果（如文件写入成功）
                    .onToolExecuted(toolExecution -> {
                        ToolExecutionRequest request = toolExecution.request();
                        ToolExecutedMessage message = new ToolExecutedMessage(
                                request.id(),
                                request.name(),
                                request.arguments(),
                                toolExecution.result(),
                                toolExecution.hasFailed()
                        );
                        sink.next(JSONUtil.toJsonStr(message));
                    })
                    // 阶段4：本轮响应全部完成
                    // 所有文本输出和工具调用（含多轮 tool→LLM→tool 循环）结束后触发
                    // response.aiMessage() 包含最终 AI 消息，可获取所有工具调用记录
                    .onCompleteResponse(response -> {
                        log.info("Vue 项目生成完成，最终响应: {}", response.aiMessage().text());
                        sink.complete();
                    })
                    // 阶段5：异常处理
                    // 网络错误、模型错误、工具执行未捕获异常等导致流中断时触发
                    .onError(error -> {
                        log.error("TokenStream 流式生成异常", error);
                        sink.error(error);
                    })
                    .start();
        });
    }


}
