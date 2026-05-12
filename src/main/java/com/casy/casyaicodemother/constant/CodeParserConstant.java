package com.casy.casyaicodemother.constant;

import java.util.regex.Pattern;

public class CodeParserConstant {

    /**
     * Markdown 围栏：```html 与闭合 ``` 之间的正文，含换行；无围栏时多文件 HTML 走 {@link #LOOSE_HTML_PATTERN}。
     */
    public static final Pattern HTML_CODE_PATTERN = Pattern.compile("```html\\s*\\n([\\s\\S]*?)```", Pattern.CASE_INSENSITIVE);

    /**
     * Markdown 围栏：```css … ``` 之间。
     */
    public static final Pattern CSS_CODE_PATTERN = Pattern.compile("```css\\s*\\n([\\s\\S]*?)```", Pattern.CASE_INSENSITIVE);

    /**
     * Markdown 围栏：```js/javascript … ``` 之间（须有收尾 ```）。
     */
    public static final Pattern JS_CODE_PATTERN = Pattern.compile("```(?:js|javascript)\\s*\\n([\\s\\S]*?)```", Pattern.CASE_INSENSITIVE);

    /**
     * 无收尾围栏时，JS 正文截断位置（零宽先行断言，不消费字符）。满足任一即结束惰性 {@code *?}：
     * 换行后为 Markdown 围栏行（三个反引号开头）；
     * 至少一行仅空白后，下一行以 CJK（U+4E00–U+9FFF）开头（常见总结句）；
     * 单独一行：可选缩进后以 CJK 开头，且行末为全角 ！。？ 或省略号 …（U+2026）；
     * 输入结束 {@code \Z}。
     * 局限：纯英文收尾、`//` 中文注释等不靠上述规则截断的，会一直保持到 {@code \Z}。
     */
    public static final String JS_BLOCK_TAIL = "(?=\\R\\s*```|\\R(?:[\\t\\f ]*\\R)+[\\t\\f ]*[\\u4e00-\\u9fff]"
            + "|\\R[\\t\\f ]*[\\u4e00-\\u9fff][^\\r\\n]*[！。\\u2026]|\\Z)";

    /**
     * 开场 ```js/javascript 后无闭合 ``` 时用；正文范围至 {@link #JS_BLOCK_TAIL}。
     */
    public static final Pattern JS_FENCE_RELAXED_PATTERN = Pattern.compile(
            "```(?:js|javascript)\\s*\\R([\\s\\S]*?)" + JS_BLOCK_TAIL, Pattern.CASE_INSENSITIVE);

    /**
     * 无 html 围栏时：从 {@code <!DOCTYPE html…} 或 {@code <html…} 到对应 {@code </html>}（非贪婪）。
     * 若页内多段 html 只取首次匹配。
     */
    public static final Pattern LOOSE_HTML_PATTERN = Pattern.compile(
            "(?is)(<!DOCTYPE\\s+html[^>]*>[\\s\\S]*?</html>|<html\\b[^>]*>[\\s\\S]*?</html>)");

    /**
     * 行内标题「css 格式」下一行起，到下一个行首 fenced 围栏（三个反引号）或全文末；不把其后 JS 等围栏算进 CSS。
     */
    public static final Pattern LOOSE_CSS_PATTERN = Pattern.compile("(?is)css\\s*格式\\s*\\R([\\s\\S]*?)(?=\\R\\s*```|\\Z)");

    /**
     * 「js/javascript 格式」下一行起，截断规则同 {@link #JS_BLOCK_TAIL}。
     */
    public static final Pattern LOOSE_JS_LABEL_PATTERN = Pattern.compile(
            "(?is)(?:js|javascript)\\s*格式\\s*\\R([\\s\\S]*?)" + JS_BLOCK_TAIL);
}
