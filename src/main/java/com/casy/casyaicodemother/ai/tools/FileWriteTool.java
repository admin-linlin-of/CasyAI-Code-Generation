package com.casy.casyaicodemother.ai.tools;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
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
            @P(value = "Whether to append content. Use false for first chunk and true for following chunks", required = false) Boolean append,
            @ToolMemoryId Long appId
    ) {
        // append 使用包装类型 Boolean：模型经常漏传这个可选参数，若声明为原始 boolean，
        // langchain4j 会用 null 去填原始类型，反射拆箱时报
        // “ValueConversions.primitiveConversion(Wrapper,Object,boolean) is null”的底层异常。
        // 包装类型缺失时为 null，这里统一按 false（覆盖写）处理。
        boolean appendFlag = append != null && append;
        // 关键：在触碰 Paths.get / 文件系统之前先校验必填参数。
        // 否则 relativeFilePath=null 会抛 NullPointerException，模型只收到“java.lang.NullPointerException”，
        // 无法定位缺哪个参数，导致同一条错误调用被反复重试（见日志中大量 tool 内容为 NPE 的记录）。
        requireRelativeFilePath(relativeFilePath);
        if (content == null) {
            throw new IllegalArgumentException(
                    getToolName() + " 工具缺少必填参数 content（要写入的文件内容），本次未写入任何文件。请补上该参数后重试。");
        }
        // .vue 是「成对结构标签」文件：append 续写极易造成重复 </template>/游离 </div>，导致 npm build 的 Invalid end tag。
        // 直接拒绝并要求整文件重写（或 readFile 后 modifyFile 局部修改），从源头避免这类结构性损坏。
        if (appendFlag && relativeFilePath.toLowerCase().endsWith(".vue")) {
            throw new IllegalArgumentException(
                    getToolName() + " 禁止对 .vue 文件使用 append 续写：会破坏 <template>/<script> 的成对闭合"
                            + "（典型后果：重复 </template>、游离 </div>，npm run build 报 Invalid end tag）。"
                            + "请改为：一次 writeFile(append=false) 写入完整的 .vue 文件；"
                            + "或先 readFile 该文件再用 modifyFile 精确替换需要改动的片段。本次未写入任何内容。");
        }
        try {
            Path path = CodeGenContextHolder.resolveProjectPathForWrite(appId, relativeFilePath, appVersionService);
            Path parentDir = path.getParent();
            if (parentDir != null) {
                Files.createDirectories(parentDir);
            }
            if (appendFlag) {
                Files.write(path, content.getBytes(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } else {
                Files.write(path, content.getBytes(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            }
            log.info("Successfully wrote file: {}", path.toAbsolutePath());
            return "File written successfully: " + relativeFilePath;
        } catch (IOException e) {
            String errorMessage = "写入文件失败: " + relativeFilePath + ", 错误: " + e.getMessage();
            log.error(errorMessage, e);
            throw new IllegalStateException(errorMessage, e);
        } catch (RuntimeException e) {
            // 例如路径含非法字符（InvalidPathException）等；以“工具执行失败”回传模型，
            // 而不是让 langchain4j 把裸异常类名塞给模型。
            String errorMessage = "写入文件失败: " + relativeFilePath + ", 错误: " + e.getMessage();
            log.error(errorMessage, e);
            throw new IllegalStateException(errorMessage, e);
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
        return String.format("[Tool] %s `%s` %s", getDisplayName(),
                StrUtil.blankToDefault(relativeFilePath, "(未提供 relativeFilePath)"), mode);
    }
}
