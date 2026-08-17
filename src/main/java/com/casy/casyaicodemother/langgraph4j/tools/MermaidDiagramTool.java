package com.casy.casyaicodemother.langgraph4j.tools;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.RuntimeUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.system.SystemUtil;
import com.casy.casyaicodemother.exception.BusinessException;
import com.casy.casyaicodemother.exception.ErrorCode;
import com.casy.casyaicodemother.langgraph4j.state.ImageCategoryEnum;
import com.casy.casyaicodemother.langgraph4j.state.ImageResource;
import com.casy.casyaicodemother.manager.oss.OssManager;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
@Component
public class MermaidDiagramTool {

    @Resource
    private OssManager ossManager;

    @Value("${mermaid.chrome-path:}")
    private String chromePath;

    @Tool("将 Mermaid 代码转换为架构图图片，用于展示系统结构和技术关系")
    public List<ImageResource> generateMermaidDiagram(@P("Mermaid 图表代码") String mermaidCode,
                                                      @P("架构图描述") String description) {
        if (StrUtil.isBlank(mermaidCode)) {
            return new ArrayList<>();
        }
        try {
            File diagramFile = convertMermaidToSvg(mermaidCode);
            String keyName = String.format("mermaid/%s/%s",
                    RandomUtil.randomString(5), diagramFile.getName());
            String cosUrl = ossManager.uploadFile(keyName, diagramFile);
            FileUtil.del(diagramFile);
            if (StrUtil.isNotBlank(cosUrl)) {
                return Collections.singletonList(ImageResource.builder()
                        .category(ImageCategoryEnum.ARCHITECTURE)
                        .description(description)
                        .url(cosUrl)
                        .build());
            }
        } catch (Exception e) {
            log.error("生成架构图失败: {}", e.getMessage(), e);
        }
        return new ArrayList<>();
    }

    private File convertMermaidToSvg(String mermaidCode) {
        File tempInputFile = FileUtil.createTempFile("mermaid_input_", ".mmd", true);
        FileUtil.writeUtf8String(mermaidCode, tempInputFile);
        File tempOutputFile = FileUtil.createTempFile("mermaid_output_", ".svg", true);
        File puppeteerConfigFile = null;
        String command = SystemUtil.getOsInfo().isWindows() ? "mmdc.cmd" : "mmdc";
        List<String> cmds = new ArrayList<>();
        cmds.add(command);
        cmds.add("-i");
        cmds.add(tempInputFile.getAbsolutePath());
        cmds.add("-o");
        cmds.add(tempOutputFile.getAbsolutePath());
        cmds.add("-b");
        cmds.add("transparent");
        if (StrUtil.isNotBlank(chromePath)) {
            puppeteerConfigFile = FileUtil.createTempFile("puppeteer_config_", ".json", true);
            String configJson = String.format(
                    "{\"executablePath\":%s,\"args\":[\"--no-sandbox\",\"--disable-gpu\",\"--disable-dev-shm-usage\"]}",
                    toJsonString(chromePath)
            );
            FileUtil.writeUtf8String(configJson, puppeteerConfigFile);
            cmds.add("-p");
            cmds.add(puppeteerConfigFile.getAbsolutePath());
        }
        String output = RuntimeUtil.execForStr(cmds.toArray(new String[0]));
        FileUtil.del(tempInputFile);
        FileUtil.del(puppeteerConfigFile);
        if (!tempOutputFile.exists() || tempOutputFile.length() == 0) {
            log.error("Mermaid CLI 输出: {}", output);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "Mermaid CLI 执行失败");
        }
        return tempOutputFile;
    }

    private static String toJsonString(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
