package com.casy.casyaicodemother.core.parser;

import com.casy.casyaicodemother.ai.model.MultiFileCodeResult;
import com.casy.casyaicodemother.model.enums.CodeGenTypeEnum;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MultiFileCodeParserTest {

    @Test
    void parseInlineStyleAndScriptWhenCssJsFencesMissing() {
        String codeContent = """
                设计说明：五子棋单页。
                ```html
                <!DOCTYPE html>
                <html>
                <head>
                    <style>
                    body { margin: 0; }
                    .board { display: grid; }
                    </style>
                </head>
                <body>
                    <div class="board"></div>
                    <script>
                    console.log('gobang');
                    </script>
                </body>
                </html>
                ```
                """;
        MultiFileCodeResult result = (MultiFileCodeResult) CodeParserExecutor.executeParser(
                CodeGenTypeEnum.MULTI_FILE, codeContent);
        assertTrue(result.getHtmlCode().contains("board"));
        assertTrue(result.getCssCode().contains("display: grid"));
        assertTrue(result.getJsCode().contains("gobang"));
    }
}
