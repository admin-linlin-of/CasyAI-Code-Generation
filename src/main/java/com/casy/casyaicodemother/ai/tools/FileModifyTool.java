package com.casy.casyaicodemother.ai.tools;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import com.casy.casyaicodemother.constant.AppConstant;
import com.casy.casyaicodemother.core.CodeGenContextHolder;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolMemoryId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

@Slf4j
@Component
public class FileModifyTool extends BaseTool {

    @Tool("Modify file content by replacing old content with new content")
    public String modifyFile(
            @P("Relative file path") String relativeFilePath,
            @P("Old content to replace") String oldContent,
            @P("New replacement content") String newContent,
            @ToolMemoryId Long appId
    ) {
        // 与 FileWriteTool 同理：先校验必填参数，避免 Paths.get(null)/contains(null) 抛无信息 NPE
        requireRelativeFilePath(relativeFilePath);
        if (StrUtil.isBlank(oldContent)) {
            throw new IllegalArgumentException(
                    getToolName() + " 工具缺少必填参数 oldContent（要替换的原文内容），本次未修改任何文件。请补上该参数后重试。");
        }
        if (newContent == null) {
            throw new IllegalArgumentException(
                    getToolName() + " 工具缺少必填参数 newContent（替换后的新内容），本次未修改任何文件。请补上该参数后重试。");
        }
        try {
            Path path = Paths.get(relativeFilePath);
            if (!path.isAbsolute()) {
                String projectDirName = CodeGenContextHolder.getProjectDirName(appId);
                Path projectRoot = Paths.get(AppConstant.CODE_OUTPUT_ROOT_DIR, projectDirName);
                path = projectRoot.resolve(relativeFilePath);
            }
            if (!Files.exists(path) || !Files.isRegularFile(path)) {
                return "Error: file does not exist or is not a regular file - " + relativeFilePath;
            }
            String originalContent = Files.readString(path);
            if (!originalContent.contains(oldContent)) {
                return "Warning: old content was not found, file unchanged - " + relativeFilePath;
            }
            String modifiedContent = originalContent.replace(oldContent, newContent);
            if (originalContent.equals(modifiedContent)) {
                return "Info: file content did not change after replacement - " + relativeFilePath;
            }
            Files.writeString(path, modifiedContent, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            log.info("Successfully modified file: {}", path.toAbsolutePath());
            return "File modified successfully: " + relativeFilePath;
        } catch (IOException e) {
            String errorMessage = "Failed to modify file: " + relativeFilePath + ", error: " + e.getMessage();
            log.error(errorMessage, e);
            throw new IllegalStateException(errorMessage, e);
        }
    }

    @Override
    public String getToolName() {
        return "modifyFile";
    }

    @Override
    public String getDisplayName() {
        return "modify file";
    }

    @Override
    public String generateToolExecutedResult(JSONObject arguments) {
        String relativeFilePath = arguments.getStr("relativeFilePath");
        return String.format("[Tool] %s `%s`", getDisplayName(),
                StrUtil.blankToDefault(relativeFilePath, "(未提供 relativeFilePath)"));
    }
}
