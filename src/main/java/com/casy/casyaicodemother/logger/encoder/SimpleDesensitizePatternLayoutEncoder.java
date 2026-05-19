package com.casy.casyaicodemother.logger.encoder;

import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;

import java.util.Collections;
import java.util.Map;

/**
 * 扩展 Logback 的 {@link PatternLayoutEncoder}：在把日志事件写成字节流之前，对由 pattern 渲染得到的整行字符串做指定字段的脱敏。
 * <p>
 * 脱敏算法由 {@link JsonFieldDesensitizer} 完成；本类只负责衔接 Logback 生命周期（{@link #start()}、{@link #encode}）以及按 Encoder 的 charset 输出字节。
 * </p>
 *
 * <p><b>在 logback.xml / logback-spring.xml 中的配置示例</b></p>
 * <pre>{@code
 * <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
 *   <encoder class="com.casy.casyaicodemother.logger.encoder.SimpleDesensitizePatternLayoutEncoder">
 *     <charset>UTF-8</charset>
 *     <pattern>%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n</pattern>
 *     <!-- 规则语法同 JsonFieldDesensitizer.parseRules：字段名=起始下标,结束下标，多条英文分号分隔 -->
 *     <rules>password=0,5;mobile=3,7</rules>
 *   </encoder>
 * </appender>
 * }</pre>
 *
 * <p><b>行为说明</b></p>
 * <ul>
 *   <li><b>初始化</b>：{@link #start()} 在 encoder 启动时调用，将配置的 {@code &lt;rules&gt;} 解析为 {@link #ruleMap}；
 *       若未配置或为空串，则 {@link #ruleMap} 为空，行为与未启用脱敏的 {@link PatternLayoutEncoder} 一致（仅 layout + 编码）。</li>
 *   <li><b>编码</b>：{@link #encode(ILoggingEvent)} 先用当前 {@code layout} 渲染出 {@link String}，
 *       再调用 {@link JsonFieldDesensitizer#desensitize(String, Map)}，最后按 {@link #getCharset()} 转为 {@code byte[]}。</li>
 *   <li><b>异常安全</b>：捕获任意 {@link RuntimeException}（含脱敏或编码过程中的错误），失败时回退为对原文再编码，
 *       避免因日志管线异常导致业务线程感知失败（与“宁丢脱敏、不断日志”的常见策略一致）。</li>
 *   <li><b>字符集</b>：与标准 {@link PatternLayoutEncoder} 一致，未显式设置 charset 时使用 JVM 默认编码（与父类行为一致）。</li>
 * </ul>
 *
 * <p><b>依赖</b>：运行时 classpath 需包含本模块与 {@code logback-classic}（Spring Boot 已传递引入）。</p>
 */
public class SimpleDesensitizePatternLayoutEncoder extends PatternLayoutEncoder {

    /**
     * Logback 通过 JavaBean setter 注入：XML 中 {@code &lt;rules&gt;} 对应此属性。
     * 在 {@link #start()} 之前可被多次写入；真正生效的是解析后的 {@link #ruleMap}。
     */
    private String rules;

    /**
     * 在 {@link #start()} 中根据 {@link #rules} 调用 {@link JsonFieldDesensitizer#parseRules(String)} 得到；
     * 空 Map 表示不做任何替换。
     */
    private Map<String, JsonFieldDesensitizer.Range> ruleMap = Collections.emptyMap();

    /**
     * 由 Logback / Spring 容器注入，对应 XML 或属性中的 rules 字段。
     *
     * @param rules 规则字符串，语法见 {@link JsonFieldDesensitizer#parseRules(String)}
     */
    public void setRules(String rules) {
        this.rules = rules;
    }

    /**
     * 解析规则并调用父类启动逻辑（注册 layout 等），由 Appender 初始化阶段调用一次。
     */
    @Override
    public void start() {
        ruleMap = JsonFieldDesensitizer.parseRules(rules);
        super.start();
    }

    /**
     * 将单条日志事件编码为字节数组：渲染 → 可选脱敏 → 编码。
     *
     * @param event 当前日志事件；级别、消息、MDC、throwable 等均由 layout 消费
     * @return 供 Appender 写入的输出字节数组
     */
    @Override
    public byte[] encode(ILoggingEvent event) {
        String txt = layout.doLayout(event);
        if (txt == null) {
            return toBytes("");
        }
        try {
            if (ruleMap.isEmpty()) {
                return toBytes(txt);
            }
            return toBytes(JsonFieldDesensitizer.desensitize(txt, ruleMap));
        } catch (RuntimeException e) {
            return toBytes(txt);
        }
    }

    /**
     * 使用本 Encoder 配置的字符集将字符串转为字节；{@code getCharset() == null} 时退化为 {@link String#getBytes()}。
     */
    private byte[] toBytes(String s) {
        if (getCharset() == null) {
            return s.getBytes();
        }
        return s.getBytes(getCharset());
    }
}
