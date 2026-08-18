package com.casy.casyaicodemother.langgraph4j.node;

import com.casy.casyaicodemother.core.builder.VueProjectBuilder;
import com.casy.casyaicodemother.exception.BusinessException;
import com.casy.casyaicodemother.exception.ErrorCode;
import com.casy.casyaicodemother.langgraph4j.state.WorkflowContext;
import com.casy.casyaicodemother.model.enums.CodeGenTypeEnum;
import com.casy.casyaicodemother.util.SpringContextUtil;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.prebuilt.MessagesState;

import java.io.File;

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
            CodeGenTypeEnum generationType = context.getGenerationType();
            String buildResultDir;
            // Vue 项目类型：使用 VueProjectBuilder 进行构建
            if (generationType == CodeGenTypeEnum.VUE_PROJECT) {
                try {
                    VueProjectBuilder vueBuilder = SpringContextUtil.getBean(VueProjectBuilder.class);
                    // 执行 Vue 项目构建（npm install + npm run build）
                    boolean buildSuccess = vueBuilder.buildProject(generatedCodeDir);
                    if (buildSuccess) {
                        // 构建成功，返回 dist 目录路径
                        buildResultDir = generatedCodeDir + File.separator + "dist";
                        log.info("Vue 项目构建成功，dist 目录: {}", buildResultDir);
                    } else {
                        throw new BusinessException(ErrorCode.SYSTEM_ERROR, "Vue 项目构建失败");
                    }
                } catch (Exception e) {
                    log.error("Vue 项目构建异常: {}", e.getMessage(), e);
                    buildResultDir = generatedCodeDir; // 异常时返回原路径
                }
            } else {
                // HTML 和 MULTI_FILE 代码生成时已经保存了，直接使用生成的代码目录
                buildResultDir = generatedCodeDir;
            }

            // 更新状态
            context.setCurrentStep("项目构建");
            context.setBuildResultDir(buildResultDir);
            log.info("项目构建节点完成，最终目录: {}", buildResultDir);
            return WorkflowContext.saveContext(context);
        });
    }
}

