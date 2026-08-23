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
import com.casy.casyaicodemother.langgraph4j.workflow.WorkflowChatEmitter;
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

            // 多次对话可指定已有版本目录；为空则 HTML/MULTI_FILE 在 processCodeStream 内 createCodeVersion，
            // VUE_PROJECT 由 CodeGenContextHolder 首次写文件时创建，不能写死 v1。
            String specifiedVersionDir = StrUtil.isBlank(context.getVersionDir()) ? null : context.getVersionDir();
            Flux<String> codeStream = codeGeneratorFacade.generateAndSaveCodeStream(
                    userMessage, generationType, generationModel, appId, userMessageId, specifiedVersionDir);
            // 代码流本身不进对话（避免把 HTML 源码刷到左侧）。先提示「正在生成」，
            // 再每 1.6s 推一个点，避免长节点期间 SSE 完全静默。
            WorkflowChatEmitter.emitChunked("\n代码生成中，模型正在输出…\n");
            java.util.concurrent.atomic.AtomicLong lastBeat = new java.util.concurrent.atomic.AtomicLong(System.currentTimeMillis());
            codeStream
                    .doOnNext(chunk -> {
                        long now = System.currentTimeMillis();
                        if (now - lastBeat.get() >= 1600) {
                            lastBeat.set(now);
                            WorkflowChatEmitter.emitPing();
                        }
                    })
                    .blockLast(Duration.ofMinutes(10));

            AppVersionService appVersionService = SpringContextUtil.getBean(AppVersionService.class);
            String versionDir = appVersionService.getLatestCodeDir(appId);
            String generatedCodeDir = resolveGeneratedCodeDir(generationType, appId, versionDir);
            log.info("AI 代码生成完成，生成目录: {}", generatedCodeDir);

            context.setCurrentStep("代码生成");
            context.setGeneratedCodeDir(generatedCodeDir);
            // HTML / 多文件写盘后静态预览已可访问，立刻后台截封面，与后续质检并行。
            // Vue 要等 npm build 出 dist，改在 ProjectBuilderNode 成功后再 submit。
            if (generationType != CodeGenTypeEnum.VUE_PROJECT) {
                SitePreviewNode.submit(appId, generatedCodeDir, generationType);
            }
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
