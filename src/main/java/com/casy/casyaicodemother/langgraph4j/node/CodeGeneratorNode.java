package com.casy.casyaicodemother.langgraph4j.node;

import cn.hutool.core.util.StrUtil;
import com.casy.casyaicodemother.constant.AppConstant;
import com.casy.casyaicodemother.core.AiCodeGeneratorFacade;
import com.casy.casyaicodemother.core.vue.VueProjectVersionManager;
import com.casy.casyaicodemother.exception.ErrorCode;
import com.casy.casyaicodemother.exception.ThrowUtils;
import com.casy.casyaicodemother.langgraph4j.state.WorkflowContext;
import com.casy.casyaicodemother.model.enums.CodeGenTypeEnum;
import com.casy.casyaicodemother.model.enums.ModelTypeEnum;
import com.casy.casyaicodemother.service.AppVersionService;
import com.casy.casyaicodemother.util.SpringContextUtil;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import reactor.core.publisher.Flux;

import java.time.Duration;

import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
 * 代码生成节点
 */
@Slf4j
public class CodeGeneratorNode {

    public static AsyncNodeAction<MessagesState<String>> create() {
        return node_async(state -> {
            WorkflowContext context = WorkflowContext.getContext(state);
            log.info("执行节点: 代码生成");

            // 构造用户消息
            String userMessage = context.getEnhancedPrompt();

            CodeGenTypeEnum generationType = context.getGenerationType();
            ModelTypeEnum generationModel = context.getModelTypeEnum();
            Long appId = context.getAppId();
            // 必须使用真实 appId：Redis 记忆、createCodeVersion 查 t_app、落盘目录都绑定它。
            // appId=0 会导致脏记忆、空响应，以及「应用不存在」后文件根本不会写出。
            ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "工作流缺少真实 appId");
            Long userMessageId = context.getUserMessageId();

            AiCodeGeneratorFacade codeGeneratorFacade = SpringContextUtil.getBean(AiCodeGeneratorFacade.class);
            log.info("开始生成代码，类型: {} ({})，appId: {}", generationType.getValue(), generationType.getText(), appId);

            // versionDir 传 null：HTML/MULTI_FILE 由 processCodeStream 内 createCodeVersion 分配；
            // VUE_PROJECT 由 CodeGenContextHolder 在首次写文件时 createCodeVersion，不能写死 v1。
            Flux<String> codeStream = codeGeneratorFacade.generateAndSaveCodeStream(
                    userMessage, generationType, generationModel, appId, userMessageId, null);
            // 保存失败会从 Flux 抛出，不再像 doOnComplete 吞异常那样假装成功
            codeStream.blockLast(Duration.ofMinutes(10));

            // 真正的版本号以 createCodeVersion 写入的记录为准，不能用节点里拼的 v1
            AppVersionService appVersionService = SpringContextUtil.getBean(AppVersionService.class);
            String versionDir = appVersionService.getLatestCodeDir(appId);
            String generatedCodeDir = resolveGeneratedCodeDir(generationType, appId, versionDir);
            log.info("AI 代码生成完成，生成目录: {}", generatedCodeDir);

            context.setCurrentStep("代码生成");
            context.setGeneratedCodeDir(generatedCodeDir);
            return WorkflowContext.saveContext(context);
        });
    }

    /**
     * 按真实版本目录拼落盘路径；尚未写出文件时返回空，交给质检判定失败。
     */
    private static String resolveGeneratedCodeDir(CodeGenTypeEnum generationType, Long appId, String versionDir) {
        if (StrUtil.isBlank(versionDir)) {
            log.warn("代码版本未创建，文件可能未写出，appId={}", appId);
            return "";
        }
        if (generationType == CodeGenTypeEnum.VUE_PROJECT) {
            VueProjectVersionManager versionManager = SpringContextUtil.getBean(VueProjectVersionManager.class);
            return versionManager.getVersionDir(appId, versionDir).getAbsolutePath();
        }
        return String.format("%s/%s_%s_%s",
                AppConstant.CODE_OUTPUT_ROOT_DIR, generationType.getValue(), appId, versionDir);
    }

}
