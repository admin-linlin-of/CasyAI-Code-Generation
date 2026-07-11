package com.casy.casyaicodemother.ai.tools;

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

/**
 * 文件写入工具（VUE_PROJECT 模式下 AI 通过 @Tool 调用）。
 * <p>
 * AI 传入相对路径（如 src/pages/Home.vue），本工具负责：
 * 1. 首次写入：createCodeVersion → 写库；v2+ 时 {@link com.casy.casyaicodemother.core.vue.VueProjectVersionManager} 已从上一版复制完整源码
 * 2. 写入带版本号目录：tmp/code_output/vue_project_{appId}_v{n}/...
 * 3. node_modules 不在此复制，构建时链接到 vue_project_{appId}_shared/node_modules
 * 4. 返回相对路径给 AI，不暴露服务器绝对路径
 */
@Slf4j
@Component
public class FileWriteTool {

    @Resource
    @Lazy
    private AppVersionService appVersionService;

    @Tool("写入文件到指定路径")
    public String writeFile(
            @P("文件的相对路径") String relativeFilePath,
            @P("要写入的文件内容") String content,
            // append=false 覆盖写入（首块）；append=true 追加写入（大文件分块续写，降低单次 tool arguments JSON 体积）
            @P(value = "是否追加写入；同一文件首块传 false，后续分块传 true", required = false) boolean append,
            @ToolMemoryId Long appId
    ) {
        try {
            Path path = Paths.get(relativeFilePath);
            if (!path.isAbsolute()) {
                // 第一次 writeFile：createCodeVersion（v2+ 会先复制上一版完整源码，再写入/覆盖本文件）
                String versionDir = CodeGenContextHolder.getOrCreateVersionDir(appId, appVersionService);
                // 有版本 → vue_project_123_v1；无上下文（异常情况）→ 降级为 vue_project_123
                String projectDirName = versionDir != null
                        ? CodeGenContextHolder.buildProjectDirName(appId, versionDir)
                        : "vue_project_" + appId;
                // 完整根目录：{user.dir}/tmp/code_output/vue_project_{appId}_v{n}
                Path projectRoot = Paths.get(AppConstant.CODE_OUTPUT_ROOT_DIR, projectDirName);
                // AI 给的 relativeFilePath 拼到版本目录下，如 .../vue_project_123_v1/src/App.vue
                path = projectRoot.resolve(relativeFilePath);
            }
            // 创建父目录（如果不存在）
            Path parentDir = path.getParent();
            if (parentDir != null) {
                Files.createDirectories(parentDir);
            }
            // 写入文件内容
            // 后两个是 OpenOption，控制文件不存在/已存在时怎么打开
            // StandardOpenOption.CREATE：文件不存在就创建
            // StandardOpenOption.TRUNCATE_EXISTING：文件已存在就清空再写（覆盖）
            if (append) {
                // 分块续写：保留已有内容，在文件末尾追加本块
                Files.write(path, content.getBytes(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } else {
                // 首块或整文件写入：清空后覆盖
                Files.write(path, content.getBytes(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            }
            log.info("成功写入文件: {}", path.toAbsolutePath());
            // 注意要返回相对路径，不能让 AI 把文件绝对路径返回给用户
            return "文件写入成功: " + relativeFilePath;
        } catch (IOException e) {
            String errorMessage = "文件写入失败: " + relativeFilePath + ", 错误: " + e.getMessage();
            log.error(errorMessage, e);
            return errorMessage;
        }
    }
}
