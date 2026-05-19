package com.casy.casyaicodemother.logger.encoder;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 针对日志最终文本中出现的、类似 {@code "字段名":"字段值"} 的片段做字段值脱敏（用 {@code *} 覆盖指定区间），不依赖 JSON 解析器。
 * <p>
 * <b>适用场景</b>：日志中混杂了上述键值外观的结构，不要求整段是合法 JSON，只希望按字段名将引号内的值按起始/结束下标打成星号。
 * </p>
 * <p>
 * <b>限制</b>：嵌套对象、转义引号、无引号值、键名与冒号之间有空格等非“标准”片段时，本实现的匹配可能失败或误匹配，应视为启发式处理。
 * </p>
 * <p>
 * <b>规则字符串格式</b>（与 {@link #parseRules(String)} 一致）：
 * <pre>
 *   字段名1=起始下标,结束下标;字段名2=起始下标,结束下标
 * </pre>
 * 下标从 <b>0</b> 开始，表示一对双引号<strong>包围的字段值内部</strong>的字符偏移（不包含两侧引号）。
 * 若配置 {@code 结束下标 &lt; 0}，在 {@link #desensitize} 中会按“值长度减 1”截断为有效结束下标。
 * </p>
 *
 * <p><b>使用示例</b>：</p>
 * <pre>{@code
 * Map<String, JsonFieldDesensitizer.Range> rules =
 *     JsonFieldDesensitizer.parseRules("password=0,2;mobile=3,7");
 * String out = JsonFieldDesensitizer.desensitize(
 *     "req={\"password\":\"abc123\",\"mobile\":\"13800138000\"}", rules);
 * }</pre>
 */
public final class JsonFieldDesensitizer {

    private JsonFieldDesensitizer() {
    }

    /**
     * 将规则文本解析为「字段名 → 替换区间」映射。
     *
     * @param rules 规则串：多条用英文分号 {@code ;} 分隔，每条形如 {@code 字段名=起始,结束}，
     *              例如 {@code password=0,3;phone=4,7}；字段名首尾空白会 trim；无法解析的片段跳过且不抛异常；
     *              区间数字非法时 {@link Integer#parseInt} 会抛异常，由调用方保证格式合法
     * @return 有序映射（迭代顺序与字段在规则串中首次出现顺序一致）；{@code rules} 为 null 或空白则返回空 Map
     */
    public static Map<String, Range> parseRules(String rules) {
        if (rules == null || rules.trim().isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, Range> map = new LinkedHashMap<>();
        for (String part : rules.split(";")) {
            part = part.trim();
            if (part.isEmpty()) {
                continue;
            }
            int eq = part.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String key = part.substring(0, eq).trim();
            String range = part.substring(eq + 1).trim();
            int comma = range.indexOf(',');
            if (comma < 0) {
                continue;
            }
            int begin = Integer.parseInt(range.substring(0, comma).trim());
            int end = Integer.parseInt(range.substring(comma + 1).trim());
            map.put(key, new Range(begin, end));
        }
        return map;
    }

    /**
     * 在整段文本中反复扫描：对每个规则字段，找到 {@code "字段名"} 后紧随的标准字符串值，并将值内 {@code [begin, end]} 替换为 {@code *}。
     * <p>
     * 匹配与替换步骤概要：
     * <ol>
     *   <li>在全文中查找子串 {@code "字段名"}（带双引号，大小写须与 JSON 片段一致）。</li>
     *   <li>从该子串末尾向后找第一个冒号 {@code :}，再从其后找值的结束边界：
     *       取同一“层级”上先出现的 {@code ,} 或 {@code }} 作为结束（简单分隔，不解析完整 JSON）。</li>
     *   <li>假定值为标准形式 {@code :"值内容"}：冒号后紧跟双引号，值内容长度为
     *       {@code endField - colon - 1 - 2}（去掉 {@code :}、两端引号所占位置）。</li>
     *   <li>在值内容下标范围 {@code [begin, end]} 内置为 {@code *}；扫描从 {@code endField} 继续，支持同名字段多次出现。</li>
     * </ol>
     * 每条规则独立做一轮全文扫描，多次执行会复用同一个 {@code char[]} 缓冲区，后执行的规则可能覆盖先执行的星号（若区间重叠且字段不同需自行避免）。
     * </p>
     *
     * @param text  原始日志行或任意字符串；{@code null} 则原样返回
     * @param rules {@link #parseRules(String)} 的返回值；空 Map 则原样返回
     * @return 替换后的新 {@link String}（内部新建 {@code char[]}，不修改入参 {@code text}）
     */
    public static String desensitize(String text, Map<String, Range> rules) {
        if (text == null || rules.isEmpty()) {
            return text;
        }
        char[] buf = text.toCharArray();
        for (Map.Entry<String, Range> e : rules.entrySet()) {
            String key = e.getKey();
            Range r = e.getValue();
            // 与 JSON 中键名一致，例如 key 为 password 时匹配 "password"
            String needle = "\"" + key + "\"";
            int from = 0;
            while (from <= buf.length - needle.length()) {
                if (!match(buf, from, needle)) {
                    from++;
                    continue;
                }
                // 其后应为 : 与引号括起的值，如 "password":"secret"
                int colon = indexOf(buf, ':', from + needle.length());
                if (colon < 0) {
                    from++;
                    continue;
                }
                int endField = indexOfDelim(buf, colon + 1);
                if (endField < 0) {
                    from++;
                    continue;
                }
                // 值内容长度：colon 到 endField 之间含 :、" ... "，故总长减 1（冒号）再减 2（一对引号）
                int length = endField - colon - 1 - 2;
                if (length <= 0) {
                    from = endField;
                    continue;
                }
                int desStart = r.begin;
                int desEnd = r.end;
                if (desEnd < 0 || desEnd > length - 1) {
                    desEnd = length - 1;
                }
                if (desStart > desEnd || desStart > length - 1) {
                    from = endField;
                    continue;
                }
                // 值首字符在缓冲中下标：冒号后是开引号，内容从 colon+2 起
                int base = colon + 2;
                for (int i = 0; i <= desEnd - desStart; i++) {
                    buf[base + desStart + i] = '*';
                }
                from = endField;
            }
        }
        return new String(buf);
    }

    /**
     * 判断从 {@code from} 起是否与 {@code needle} 等长片段逐字符相等。
     *
     * @param buf    全文缓冲
     * @param from   当前尝试匹配的起始下标
     * @param needle 待匹配串（已含 JSON 键两侧引号）
     */
    private static boolean match(char[] buf, int from, String needle) {
        for (int i = 0; i < needle.length(); i++) {
            if (buf[from + i] != needle.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 在 {@code text[from ..]} 中查找第一个 {@code c}。
     *
     * @return 下标；未找到返回 {@code -1}
     */
    private static int indexOf(char[] text, char c, int from) {
        for (int i = from; i < text.length; i++) {
            if (text[i] == c) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 找当前字符串值片段的结束位置：从 {@code from}（通常为开引号后）起，同一“层”上先遇到的 {@code ,} 或 {@code }}。
     * 若只有一种合法分隔符存在，则返回该种；都缺失则返回 {@code -1}。
     */
    private static int indexOfDelim(char[] text, int from) {
        int comma = indexOf(text, ',', from);
        int brace = indexOf(text, '}', from);
        if (comma < 0) {
            return brace;
        }
        if (brace < 0) {
            return comma;
        }
        return Math.min(comma, brace);
    }

    /**
     * 描述单个 JSON 字符串字段值内部要打星的区间 {@code [begin, end]}，下标从 0 起。
     * <p>
     * 在 {@link #desensitize} 中，若 {@code end &lt; 0} 或 {@code end} 超过值尾下标，实际结束下标会被限制为 {@code length - 1}；
     * 若 {@code begin} 超出范围或与 {@code end} 无效组合，则该次匹配跳过替换。
     * </p>
     */
    public static final class Range {

        /** 字段值内容区内开始替换为 {@code *} 的下标（含） */
        public final int begin;

        /**
         * 字段值内容区内结束替换为 {@code *} 的下标（含）。
         * 可为负数，表示语义上“直到末尾”，在 {@link #desensitize} 中会规范为 {@code length - 1}。
         */
        public final int end;

        public Range(int begin, int end) {
            this.begin = begin;
            this.end = end;
        }
    }
}
