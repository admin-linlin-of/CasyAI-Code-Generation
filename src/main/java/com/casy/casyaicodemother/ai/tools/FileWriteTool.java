package com.casy.casyaicodemother.ai.tools;

import cn.hutool.json.JSONObject;
import com.casy.casyaicodemother.constant.AppConstant;
import com.casy.casyaicodemother.core.CodeGenContextHolder;
import com.casy.casyaicodemother.service.AppVersionService;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolMemoryId;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

@Slf4j
@Component
public class FileWriteTool extends BaseTool {

    @Resource
    @Lazy
    private AppVersionService appVersionService;

    @Tool("Write file to the specified relative path")
    public String writeFile(
            @P("Relative file path") String relativeFilePath,
            @P("File content to write") String content,
            @P(value = "Whether to append content. Use false for first chunk and true for following chunks", required = false) boolean append,
            @ToolMemoryId Long appId
    ) {
        try {
            Path path = Paths.get(relativeFilePath);
            if (!path.isAbsolute()) {
                String versionDir = CodeGenContextHolder.getOrCreateVersionDir(appId, appVersionService);
                String projectDirName = versionDir != null
                        ? CodeGenContextHolder.buildProjectDirName(appId, versionDir)
                        : "vue_project_" + appId;
                Path projectRoot = Paths.get(AppConstant.CODE_OUTPUT_ROOT_DIR, projectDirName);
                path = projectRoot.resolve(relativeFilePath);
            }
            Path parentDir = path.getParent();
            if (parentDir != null) {
                Files.createDirectories(parentDir);
            }
            if (append) {
                Files.write(path, content.getBytes(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } else {
                Files.write(path, content.getBytes(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            }
            log.info("Successfully wrote file: {}", path.toAbsolutePath());
            return "File written successfully: " + relativeFilePath;
        } catch (IOException e) {
            String errorMessage = "Failed to write file: " + relativeFilePath + ", error: " + e.getMessage();
            log.error(errorMessage, e);
            return errorMessage;
        }
    }

    @Override
    public String getToolName() {
        return "writeFile";
    }

    @Override
    public String getDisplayName() {
        return "write file";
    }

    @Override
    public String generateToolExecutedResult(JSONObject arguments) {
        String relativeFilePath = arguments.getStr("relativeFilePath");
        boolean append = Boolean.TRUE.equals(arguments.getBool("append"));
        String mode = append ? "append" : "overwrite";
        return String.format("[Tool] %s `%s` %s", getDisplayName(), relativeFilePath, mode);
    }
}
