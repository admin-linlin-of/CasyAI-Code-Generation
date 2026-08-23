package com.casy.casyaicodemother.langgraph4j.node;

import cn.hutool.core.util.StrUtil;
import com.casy.casyaicodemother.core.AiCodeGeneratorFacade;
import com.casy.casyaicodemother.exception.ErrorCode;
import com.casy.casyaicodemother.exception.ThrowUtils;
import com.casy.casyaicodemother.langgraph4j.model.QualityResult;
import com.casy.casyaicodemother.langgraph4j.state.WorkflowContext;
import com.casy.casyaicodemother.util.SpringContextUtil;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import reactor.core.publisher.Flux;

import java.io.File;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
 * 代码修复节点：使用独立 CodeRepairService，只改已有文件，不走代码生成提示词。
 */
@Slf4j
public class CodeRepairNode {

    private static final Pattern VERSION_SUFFIX = Pattern.compile("_(v\\d+)$");

    public static AsyncNodeAction<MessagesState<String>> create() {
        return node_async(state -> {
            WorkflowContext context = WorkflowContext.getContext(state);
            int nextCount = (context.getRepairCount() == null ? 0 : context.getRepairCount()) + 1;
            context.setRepairCount(nextCount);
            log.info("执行节点: 代码修复，第 {}/{} 次", nextCount, WorkflowContext.MAX_REPAIR_COUNT);

            ThrowUtils.throwIf(context.getAppId() == null || context.getAppId() <= 0,
                    ErrorCode.PARAMS_ERROR, "工作流缺少真实 appId");
            ThrowUtils.throwIf(context.getModelTypeEnum() == null,
                    ErrorCode.PARAMS_ERROR, "工作流缺少模型类型");

            String versionDir = extractVersionDir(context.getGeneratedCodeDir());
            AiCodeGeneratorFacade facade = SpringContextUtil.getBean(AiCodeGeneratorFacade.class);
            Flux<String> repairStream = facade.repairCodeStream(
                    buildRepairMessage(context, nextCount),
                    context.getGenerationType(),
                    context.getModelTypeEnum(),
                    context.getAppId(),
                    context.getUserMessageId(),
                    versionDir);
            repairStream.blockLast(Duration.ofMinutes(10));

            context.setCurrentStep("代码修复");
            return WorkflowContext.saveContext(context);
        });
    }

    /**
     * 把质检/打包错误组装成修复服务的用户消息。
     */
    private static String buildRepairMessage(WorkflowContext context, int repairCount) {
        StringBuilder sb = new StringBuilder();
        sb.append("这是第 ").append(repairCount).append("/").append(WorkflowContext.MAX_REPAIR_COUNT)
                .append(" 次修复。\n");
        sb.append("项目目录: ").append(StrUtil.blankToDefault(context.getGeneratedCodeDir(), "未知")).append("\n");
        if (StrUtil.isNotBlank(context.getOriginalPrompt())) {
            sb.append("原始需求: ").append(context.getOriginalPrompt()).append("\n");
        }
        QualityResult qualityResult = context.getQualityResult();
        if (qualityResult != null) {
            if (qualityResult.getErrors() != null) {
                sb.append("\n## 错误\n");
                qualityResult.getErrors().forEach(error -> sb.append("- ").append(error).append("\n"));
            }
            if (qualityResult.getSuggestions() != null) {
                sb.append("\n## 建议\n");
                qualityResult.getSuggestions().forEach(s -> sb.append("- ").append(s).append("\n"));
            }
        }
        sb.append("\n请用工具只修复上述问题，不要重写整个项目。");
        return sb.toString();
    }

    private static String extractVersionDir(String generatedCodeDir) {
        if (StrUtil.isBlank(generatedCodeDir)) {
            return null;
        }
        Matcher matcher = VERSION_SUFFIX.matcher(new File(generatedCodeDir).getName());
        return matcher.find() ? matcher.group(1) : null;
    }
}
