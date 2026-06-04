package com.casy.casyaicodemother.core;

import com.casy.casyaicodemother.ai.model.HtmlCodeResult;
import com.casy.casyaicodemother.ai.model.MultiFileCodeResult;
import com.casy.casyaicodemother.core.chatModel.ChatModelExecutor;
import com.casy.casyaicodemother.core.parser.CodeParserExecutor;
import com.casy.casyaicodemother.core.save.CodeFileSaverExecutor;
import com.casy.casyaicodemother.exception.BusinessException;
import com.casy.casyaicodemother.exception.ErrorCode;
import com.casy.casyaicodemother.model.enums.CodeGenTypeEnum;
import com.casy.casyaicodemother.model.enums.ModelTypeEnum;
import com.casy.casyaicodemother.service.AppVersionService;
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
                HtmlCodeResult htmlCodeResult = ChatModelExecutor.executeParser(modelTypeEnum, appId).generateHtmlCode(userMessage);
                yield CodeFileSaverExecutor.executeSaver(htmlCodeResult, CodeGenTypeEnum.HTML, appId);
            }
            case MULTI_FILE -> {
                MultiFileCodeResult multiFileCodeResult = ChatModelExecutor.executeParser(modelTypeEnum, appId).generateMultiFileCode(userMessage);
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
    public Flux<String> generateAndSaveCodeStream(String userMessage, CodeGenTypeEnum codeGenTypeEnum, ModelTypeEnum modelTypeEnum, Long appId, Long userMessageId) {
        if (codeGenTypeEnum == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "生成类型为空");
        }
        return switch (codeGenTypeEnum) {
            case HTML -> {
                Flux<String> stringFlux = ChatModelExecutor.executeParser(modelTypeEnum, appId).generateHtmlCodeStream(userMessage);
                yield processCodeStream(stringFlux, CodeGenTypeEnum.HTML, modelTypeEnum, appId, userMessageId);
            }
            case MULTI_FILE -> {
                Flux<String> stringFlux = ChatModelExecutor.executeParser(modelTypeEnum, appId).generateMultiFileCodeStream(userMessage);
                yield processCodeStream(stringFlux, CodeGenTypeEnum.MULTI_FILE, modelTypeEnum, appId, userMessageId);
            }
            default -> {
                String errorMessage = "不支持的生成类型：" + codeGenTypeEnum.getValue();
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, errorMessage);
            }
        };
    }


    /**
     * 通用流式代码处理方法，实时收集流中响应信息
     *
     * @param codeStream  代码流
     * @param codeGenType 代码生成类型
     * @return 流式响应
     */
    private Flux<String> processCodeStream(Flux<String> codeStream, CodeGenTypeEnum codeGenType, ModelTypeEnum modelTypeEnum, Long appId, Long userMessageId) {
        StringBuilder codeBuilder = new StringBuilder();
        // 实时收集代码片段
        return codeStream.doOnNext(codeBuilder::append).doOnComplete(() -> {
            // 流式返回完成后保存代码
            try {
                String completeCode = codeBuilder.toString();
                log.info("AI最终的响应：{}", completeCode);
                // 使用执行器解析代码
                Object parsedResult = CodeParserExecutor.executeParser(codeGenType, completeCode);
                // 添加新增版本
                String versionDir = appVersionService.createCodeVersion(appId, modelTypeEnum, userMessageId);
                // 使用执行器保存代码
                File savedDir = CodeFileSaverExecutor.executeSaver(parsedResult, codeGenType, appId, versionDir);
                log.info("保存成功，路径为：{}", savedDir.getAbsolutePath());
            } catch (Exception e) {
                log.error("代码解析或保存失败: {}", e.getMessage());
            }
        });
    }

}
