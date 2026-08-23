package com.casy.casyaicodemother.langgraph4j.node;

import com.casy.casyaicodemother.core.builder.VueProjectBuilder;
import com.casy.casyaicodemother.exception.BusinessException;
import com.casy.casyaicodemother.exception.ErrorCode;
import com.casy.casyaicodemother.langgraph4j.model.QualityResult;
import com.casy.casyaicodemother.langgraph4j.state.WorkflowContext;
import com.casy.casyaicodemother.model.enums.CodeGenTypeEnum;
import com.casy.casyaicodemother.util.SpringContextUtil;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.prebuilt.MessagesState;

import java.io.File;
import java.util.List;

import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
 * 如果是 Vue ‍工程项目类型，调用项目已有的 VueProjectBuilder 打包构建；
 * 如果是其他类型，直接忽略（因为上一步文件已经通过 AI 工具调用保存）。
 */
@Slf4j
public class ProjectBuilderNode {

    public static AsyncNodeAction<MessagesState<String>> create() {
        return node_async(state -> {
            WorkflowContext context = WorkflowContext.getContext(state);
            log.info("执行节点: 项目构建");

            // 获取必要的参数
            String generatedCodeDir = context.getGeneratedCodeDir();
            String buildResultDir;

            // Vue 项目类型：使用 VueProjectBuilder 进行构建
            try {
                VueProjectBuilder vueBuilder = SpringContextUtil.getBean(VueProjectBuilder.class);
                // 执行 Vue 项目构建（npm install + npm run build）
                boolean buildSuccess = vueBuilder.buildProject(generatedCodeDir);
                if (buildSuccess) {
                    // 构建成功，返回 dist 目录路径
                    buildResultDir = generatedCodeDir + File.separator + "dist";
                    log.info("Vue 项目构建成功，dist 目录: {}", buildResultDir);
                    // dist 已就绪，后台截预览图，不在本节点里等 Chrome
                    SitePreviewNode.submit(context.getAppId(), generatedCodeDir, CodeGenTypeEnum.VUE_PROJECT);
                } else {
                    // 打包失败回写质检结果，工作流走代码修复节点
                    markBuildFailed(context, "Vue 项目 npm run build 失败");
                    throw new BusinessException(ErrorCode.SYSTEM_ERROR, "Vue 项目构建失败");
                }
            } catch (Exception e) {
                log.error("Vue 项目构建异常: {}", e.getMessage(), e);
                markBuildFailed(context, e.getMessage());
                buildResultDir = generatedCodeDir; // 异常时返回原路径
            }

            // 更新状态
            context.setCurrentStep("项目构建");
            context.setBuildResultDir(buildResultDir);
            log.info("项目构建节点完成，最终目录: {}", buildResultDir);
            return WorkflowContext.saveContext(context);
        });
    }

    /**
     * 将 npm 构建失败写入质检结果，供后续条件边进入代码修复节点。
     */
    private static void markBuildFailed(WorkflowContext context, String error) {
        context.setQualityResult(QualityResult.builder()
                .isValid(false)
                .errors(List.of(error == null ? "Vue 项目构建失败" : error))
                .suggestions(List.of(
                        "请检查 .vue 文件标签是否闭合，尤其是 </template> 与根节点 </div>",
                        "用文件修改工具补全缺失标签后保证 npm run build 能通过"
                ))
                .build());
    }
}

