package com.casy.casyaicodemother.core;

import com.casy.casyaicodemother.ai.model.HtmlCodeResult;
import com.casy.casyaicodemother.ai.model.MultiFileCodeResult;

import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * 代码解析器
 * 提供静态方法解析不同类型的代码内容
 *
 * @author yupi
 */
public class CodeParser {

    /** Markdown 围栏：```html 与闭合 ``` 之间的正文，含换行；无围栏时多文件 HTML 走 {@link #LOOSE_HTML_PATTERN}。 */
    private static final Pattern HTML_CODE_PATTERN = Pattern.compile("```html\\s*\\n([\\s\\S]*?)```", Pattern.CASE_INSENSITIVE);
    /** Markdown 围栏：```css … ``` 之间。 */
    private static final Pattern CSS_CODE_PATTERN = Pattern.compile("```css\\s*\\n([\\s\\S]*?)```", Pattern.CASE_INSENSITIVE);
    /** Markdown 围栏：```js/javascript … ``` 之间（须有收尾 ```）。 */
    private static final Pattern JS_CODE_PATTERN = Pattern.compile("```(?:js|javascript)\\s*\\n([\\s\\S]*?)```", Pattern.CASE_INSENSITIVE);

    /**
     * 无收尾围栏时，JS 正文截断位置（零宽先行断言，不消费字符）。满足任一即结束惰性 {@code *?}：
     * <ul>
     * <li>换行后为 Markdown 围栏行（三个反引号开头）；</li>
     * <li>至少一行仅空白后，下一行以 CJK（U+4E00–U+9FFF）开头（常见总结句）；</li>
     * <li>单独一行：可选缩进后以 CJK 开头，且行末为全角 ！。？ 或省略号 …（U+2026）；</li>
     * <li>输入结束 {@code \Z}。</li>
     * </ul>
     * 局限：纯英文收尾、`//` 中文注释等不靠上述规则截断的，会一直保持到 {@code \Z}。
     */
    private static final String JS_BLOCK_TAIL = "(?=\\R\\s*```|\\R(?:[\\t\\f ]*\\R)+[\\t\\f ]*[\\u4e00-\\u9fff]"
            + "|\\R[\\t\\f ]*[\\u4e00-\\u9fff][^\\r\\n]*[！。\\u2026]|\\Z)";
    /** 开场 ```js/javascript 后无闭合 ``` 时用；正文范围至 {@link #JS_BLOCK_TAIL}。 */
    private static final Pattern JS_FENCE_RELAXED_PATTERN = Pattern.compile(
            "```(?:js|javascript)\\s*\\R([\\s\\S]*?)" + JS_BLOCK_TAIL, Pattern.CASE_INSENSITIVE);
    /**
     * 无 html 围栏时：从 {@code <!DOCTYPE html…} 或 {@code <html…} 到对应 {@code </html>}（非贪婪）。
     * 若页内多段 html 只取首次匹配。
     */
    private static final Pattern LOOSE_HTML_PATTERN = Pattern.compile(
            "(?is)(<!DOCTYPE\\s+html[^>]*>[\\s\\S]*?</html>|<html\\b[^>]*>[\\s\\S]*?</html>)");
    /**
     * 行内标题「css 格式」下一行起，到下一个行首 fenced 围栏（三个反引号）或全文末；不把其后 JS 等围栏算进 CSS。
     */
    private static final Pattern LOOSE_CSS_PATTERN = Pattern.compile("(?is)css\\s*格式\\s*\\R([\\s\\S]*?)(?=\\R\\s*```|\\Z)");
    /** 「js/javascript 格式」下一行起，截断规则同 {@link #JS_BLOCK_TAIL}。 */
    private static final Pattern LOOSE_JS_LABEL_PATTERN = Pattern.compile(
            "(?is)(?:js|javascript)\\s*格式\\s*\\R([\\s\\S]*?)" + JS_BLOCK_TAIL);

    /**
     * 解析 HTML 单文件代码
     */
    public static HtmlCodeResult parseHtmlCode(String codeContent) {
        HtmlCodeResult result = new HtmlCodeResult();
        // 提取 HTML 代码
        String htmlCode = extractHtmlCode(codeContent);
        if (htmlCode != null && !htmlCode.trim().isEmpty()) {
            result.setHtmlCode(htmlCode.trim());
        } else {
            // 如果没有找到代码块，将整个内容作为HTML
            result.setHtmlCode(codeContent.trim());
        }
        return result;
    }

    /**
     * 解析多文件代码（HTML + CSS + JS）
     */
    public static MultiFileCodeResult parseMultiFileCode(String codeContent) {
        MultiFileCodeResult result = new MultiFileCodeResult();
        // 提取各类代码
        String htmlCode = firstNonBlank(
                extractCodeByPattern(codeContent, HTML_CODE_PATTERN),
                extractFullMatch(codeContent, LOOSE_HTML_PATTERN));
        String cssCode = firstNonBlank(
                extractCodeByPattern(codeContent, CSS_CODE_PATTERN),
                extractCodeByPattern(codeContent, LOOSE_CSS_PATTERN));
        String jsCode = firstNonBlank(
                extractCodeByPattern(codeContent, JS_CODE_PATTERN),
                extractCodeByPattern(codeContent, JS_FENCE_RELAXED_PATTERN),
                extractCodeByPattern(codeContent, LOOSE_JS_LABEL_PATTERN));
        // 设置HTML代码
        if (htmlCode != null && !htmlCode.trim().isEmpty()) {
            result.setHtmlCode(htmlCode.trim());
        }
        // 设置CSS代码
        if (cssCode != null && !cssCode.trim().isEmpty()) {
            result.setCssCode(cssCode.trim());
        }
        // 设置JS代码
        if (jsCode != null && !jsCode.trim().isEmpty()) {
            result.setJsCode(jsCode.trim());
        }
        return result;
    }

    /**
     * 提取HTML代码内容
     *
     * @param content 原始内容
     * @return HTML代码
     */
    private static String extractHtmlCode(String content) {
        Matcher matcher = HTML_CODE_PATTERN.matcher(content);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    /**
     * 根据正则模式提取代码
     *
     * @param content 原始内容
     * @param pattern 正则模式
     * @return 提取的代码
     */
    private static String extractCodeByPattern(String content, Pattern pattern) {
        Matcher matcher = pattern.matcher(content);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private static String extractFullMatch(String content, Pattern pattern) {
        Matcher matcher = pattern.matcher(content);
        if (matcher.find()) {
            return matcher.group(0);
        }
        return null;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String v : values) {
            if (v != null && !v.trim().isEmpty()) {
                return v;
            }
        }
        return null;
    }
}
