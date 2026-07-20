package com.casy.casyaicodemother.ai.tools;

import cn.hutool.json.JSONObject;
import com.casy.casyaicodemother.constant.AppConstant;
import com.casy.casyaicodemother.core.CodeGenContextHolder;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolMemoryId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
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
        try {
            Path path = Paths.get(relativeFilePath);
            String versionDir = CodeGenContextHolder.getVersionDir(appId);
            if (!path.isAbsolute()) {
                String projectDirName = "vue_project_" + appId + File.separator + versionDir;
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
            return errorMessage;
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
        return String.format("[Tool] %s `%s`", getDisplayName(), relativeFilePath);
    }
}
