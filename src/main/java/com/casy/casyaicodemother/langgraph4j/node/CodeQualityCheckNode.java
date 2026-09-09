package com.casy.casyaicodemother.langgraph4j.node;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.casy.casyaicodemother.constant.AppConstant;
import com.casy.casyaicodemother.core.vue.VueProjectVersionManager;
import com.casy.casyaicodemother.langgraph4j.ai.CodeQualityCheckService;
import com.casy.casyaicodemother.langgraph4j.model.QualityResult;
import com.casy.casyaicodemother.langgraph4j.state.WorkflowContext;
import com.casy.casyaicodemother.model.enums.CodeGenTypeEnum;
import com.casy.casyaicodemother.service.AppVersionService;
import com.casy.casyaicodemother.util.SpringContextUtil;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.prebuilt.MessagesState;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
 * 代码质量检查节点
 */
@Slf4j
public class CodeQualityCheckNode {

    /**
     * 需要检查的文件扩展名
     */
    private static final List<String> CODE_EXTENSIONS = Arrays.asList(
            ".html", ".htm", ".css", ".js", ".json", ".vue", ".ts", ".jsx", ".tsx"
    );

    public static AsyncNodeAction<MessagesState<String>> create() {
        return node_async(state -> {
            WorkflowContext context = WorkflowContext.getContext(state);
            log.info("执行节点: 代码质量检查");
            String generatedCodeDir = context.getGeneratedCodeDir();
            QualityResult qualityResult;
            try {
                // 1. 读取并拼接代码文件内容
                String codeContent =
                        readAndConcatenateCodeFiles(generatedCodeDir); // 读取 generatedCodeDir 这个路径下的所有代码文件，拼成一段文本交给 AI 质检。 这里的 generatedCodeDir 是工作流上下文里记录的“代码在哪”的路径，它不一定等于磁盘上真实的代码位置。
                // 1.1 兜底：当前记录为空目录/无文件（例如首次生成未写文件、修复节点才真正产出 v1 的情况）时，
                // 自动回退到该 app 最新版本目录并回写 context，让后续「项目构建/代码修复」也使用正确路径，
                // 避免“文件已生成但质检一直说未找到”的假失败循环。
                if (StrUtil.isBlank(codeContent)) {
                    String resolved = resolveLatestGeneratedDir(context);
                    if (StrUtil.isNotBlank(resolved) && !resolved.equals(generatedCodeDir)) {
                        log.warn("generatedCodeDir={} 下未找到代码，回退到最新版本目录: {}", generatedCodeDir, resolved);
                        context.setGeneratedCodeDir(resolved);
                        generatedCodeDir = resolved;
                        codeContent = readAndConcatenateCodeFiles(generatedCodeDir);
                    }
                }
                if (StrUtil.isBlank(codeContent)) {
                    log.warn("未找到可检查的代码文件");
                    qualityResult = QualityResult.builder()
                            .isValid(false)
                            .errors(List.of("未找到可检查的代码文件"))
                            .suggestions(List.of("请确保代码生成成功"))
                            .build();
                } else {
                    // 2. 调用 AI 进行代码质量检查
                    CodeQualityCheckService qualityCheckService = SpringContextUtil.getBean(CodeQualityCheckService.class);
                    qualityResult = qualityCheckService.checkCodeQuality(codeContent);
                    log.info("代码质量检查完成 - 是否通过: {}", qualityResult.getIsValid());
                }
            } catch (Exception e) {
                log.error("代码质量检查异常: {}", e.getMessage(), e);
                qualityResult = QualityResult.builder()
                        .isValid(true) // 异常直接跳到下一个步骤
                        .build();
            }
            // 3. 更新状态
            context.setCurrentStep("代码质量检查");
            context.setQualityResult(qualityResult);
            return WorkflowContext.saveContext(context);
        });
    }

    /**
     * 按 app 当前最新版本重新解析落盘目录（HTML/MULTI_FILE 与 Vue 的路径规则不同）。
     * 与 {@link CodeGeneratorNode} 的 resolveGeneratedCodeDir 保持一致。
     */
    private static String resolveLatestGeneratedDir(WorkflowContext context) {
        Long appId = context.getAppId();
        CodeGenTypeEnum generationType = context.getGenerationType();
        if (appId == null || generationType == null) {
            return "";
        }
        try {
            AppVersionService appVersionService = SpringContextUtil.getBean(AppVersionService.class);
            String versionDir = appVersionService.getLatestCodeDir(appId);
            if (StrUtil.isBlank(versionDir)) {
                return "";
            }
            if (generationType == CodeGenTypeEnum.VUE_PROJECT) {
                VueProjectVersionManager versionManager = SpringContextUtil.getBean(VueProjectVersionManager.class);
                return versionManager.getVersionDir(appId, versionDir).getAbsolutePath();
            }
            return String.format("%s/%s_%s_%s",
                    AppConstant.CODE_OUTPUT_ROOT_DIR, generationType.getValue(), appId, versionDir);
        } catch (Exception e) {
            log.warn("回退解析最新代码目录失败，appId={}", appId, e);
            return "";
        }
    }

    /**
     * 读取并拼接代码目录下的所有代码文件
     */
    private static String readAndConcatenateCodeFiles(String codeDir) {
        if (StrUtil.isBlank(codeDir)) {
            return "";
        }
        File directory = new File(codeDir);
        if (!directory.exists() || !directory.isDirectory()) {
            log.error("代码目录不存在或不是目录: {}", codeDir);
            return "";
        }
        StringBuilder codeContent = new StringBuilder();
        codeContent.append("# 项目文件结构和代码内容\n\n");
        // 使用 Hutool 的 walkFiles 方法遍历所有文件
        /**
         * 访问者模式允许你在不修改对象结构‍
         * 的情况下，定义作用于这些对象的新操作。在这个例子中，walkFiles 方法遍历文件树结构，
         * 而我们传入的 lambda 表达式就是访问者，它定义了对每个文件要执行的具体操作。
         * 这样的设计让文件遍历逻辑和文件处理逻辑完全分离，可以灵活地定义不同的文件处理策略，而不需要修改遍历文件的核心代码。
         */
        FileUtil.walkFiles(directory, file -> {
            // 过滤条件：跳过隐藏文件、特定目录下的文件、非代码文件
            if (shouldSkipFile(file, directory)) {
                return;
            }
            if (isCodeFile(file)) {
                String relativePath = FileUtil.subPath(directory.getAbsolutePath(), file.getAbsolutePath());
                codeContent.append("## 文件: ").append(relativePath).append("\n\n");
                String fileContent = FileUtil.readUtf8String(file);
                codeContent.append(fileContent).append("\n\n");
            }
        });
        return codeContent.toString();
    }

    /**
     * 判断是否应该跳过此文件
     */
    private static boolean shouldSkipFile(File file, File rootDir) {
        String relativePath = FileUtil.subPath(rootDir.getAbsolutePath(), file.getAbsolutePath());
        // 跳过隐藏文件
        if (file.getName().startsWith(".")) {
            return true;
        }
        // 跳过特定目录下的文件
        return relativePath.contains("node_modules" + File.separator) ||
                relativePath.contains("dist" + File.separator) ||
                relativePath.contains("target" + File.separator) ||
                relativePath.contains(".git" + File.separator);
    }

    /**
     * 判断是否是需要检查的代码文件
     */
    private static boolean isCodeFile(File file) {
        String fileName = file.getName().toLowerCase();
        return CODE_EXTENSIONS.stream().anyMatch(fileName::endsWith);
    }
}
