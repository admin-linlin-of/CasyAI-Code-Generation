package com.casy.casyaicodemother.ai.tools;

import cn.hutool.json.JSONObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 文件类工具必填参数校验测试。
 * <p>
 * 回归背景：LLM 曾返回大量缺少 relativeFilePath 的 writeFile 调用，
 * 空路径进入 {@code Paths.get(null)} 抛 NullPointerException，langchain4j 只把裸异常类名
 * （java.lang.NullPointerException）回传模型，导致模型反复重试同样的错误调用。
 * 现在要求在触碰文件系统前抛出带明确指引的 IllegalArgumentException。
 */
class FileToolsValidationTest {

    @Test
    void writeFile_missingRelativeFilePath_shouldThrowIllegalArgumentException() {
        FileWriteTool tool = new FileWriteTool();
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tool.writeFile(null, "console.log('x')", false, 1L));
        assertTrue(ex.getMessage().contains("writeFile"), ex.getMessage());
        assertTrue(ex.getMessage().contains("relativeFilePath"), ex.getMessage());
    }

    @Test
    void writeFile_blankRelativeFilePath_shouldThrowIllegalArgumentException() {
        FileWriteTool tool = new FileWriteTool();
        assertThrows(IllegalArgumentException.class,
                () -> tool.writeFile("   ", "console.log('x')", false, 1L));
    }

    @Test
    void writeFile_missingContent_shouldThrowIllegalArgumentException() {
        FileWriteTool tool = new FileWriteTool();
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tool.writeFile("src/App.vue", null, false, 1L));
        assertTrue(ex.getMessage().contains("content"), ex.getMessage());
    }

    @Test
    void modifyFile_missingRelativeFilePath_shouldThrowIllegalArgumentException() {
        FileModifyTool tool = new FileModifyTool();
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tool.modifyFile("", "old", "new", 1L));
        assertTrue(ex.getMessage().contains("relativeFilePath"), ex.getMessage());
    }

    @Test
    void modifyFile_blankOldContent_shouldThrowIllegalArgumentException() {
        FileModifyTool tool = new FileModifyTool();
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tool.modifyFile("src/App.vue", "", "new", 1L));
        assertTrue(ex.getMessage().contains("oldContent"), ex.getMessage());
    }

    @Test
    void readFile_missingRelativeFilePath_shouldThrowIllegalArgumentException() {
        FileReadTool tool = new FileReadTool();
        assertThrows(IllegalArgumentException.class, () -> tool.readFile(null, 1L));
    }

    @Test
    void deleteFile_missingRelativeFilePath_shouldThrowIllegalArgumentException() {
        FileDeleteTool tool = new FileDeleteTool();
        assertThrows(IllegalArgumentException.class, () -> tool.deleteFile(null, 1L));
    }

    @Test
    void generateToolExecutedResult_missingPath_shouldNotRenderNull() {
        JSONObject arguments = new JSONObject();
        arguments.set("append", false);
        String result = new FileWriteTool().generateToolExecutedResult(arguments);
        assertTrue(!result.contains("null"), result);
        assertTrue(result.contains("relativeFilePath"), result);
    }
}
