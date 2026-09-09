package com.casy.casyaicodemother.ai.guardrail;

import com.casy.casyaicodemother.exception.GuardrailBlockedException;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.InputGuardrailResult;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 输入护轨：在用户 Prompt 交给 AI 模型之前做安全审查。
 * <p>
 * 命中规则时通过 {@link #fatal(String, Throwable)} 返回 fatal 结果，并把
 * {@link GuardrailBlockedException} 作为 cause 附带规则类型、命中细节和原文。
 * LangChain4j 随后会抛出 {@code InputGuardrailException}，由全局异常处理或 SSE
 * {@code onErrorResume} 捕获后推送准确错误信息并落库。
 */
public class PromptSafetyInputGuardrail implements InputGuardrail {

    /**
     * 提示词注入 / 越狱相关敏感词（大小写不敏感匹配）
     */
    private static final List<String> SENSITIVE_WORDS = Arrays.asList(
            "忽略之前的指令", "ignore previous instructions", "ignore above",
            "破解", "hack", "绕过", "bypass", "越狱", "jailbreak"
    );

    /**
     * 常见注入句式正则，例如 ignore previous instructions、pretend you are、system: you are
     */
    private static final List<Pattern> INJECTION_PATTERNS = Arrays.asList(
            Pattern.compile("(?i)ignore\\s+(?:previous|above|all)\\s+(?:instructions?|commands?|prompts?)"),
            Pattern.compile("(?i)(?:forget|disregard)\\s+(?:everything|all)\\s+(?:above|before)"),
            Pattern.compile("(?i)(?:pretend|act|behave)\\s+(?:as|like)\\s+(?:if|you\\s+are)"),
            Pattern.compile("(?i)system\\s*:\\s*you\\s+are"),
            Pattern.compile("(?i)new\\s+(?:instructions?|commands?|prompts?)\\s*:")
    );

    /**
     * 按长度 → 空内容 → 敏感词 → 注入正则依次检查，全部通过才放行。
     */
    @Override
    public InputGuardrailResult validate(UserMessage userMessage) {
        String input = userMessage.singleText();
        // 限制单次输入长度，避免超大 Prompt 打爆模型上下文
        if (input.length() > 3100) {
            return reject("LENGTH", "max=3000,actual=" + input.length(),
                    input, "输入内容过长，不要超过 3000 字");
        }
        if (input.trim().isEmpty()) {
            return reject("EMPTY", "blank", input, "输入内容不能为空");
        }
        String lowerInput = input.toLowerCase();
        for (String sensitiveWord : SENSITIVE_WORDS) {
            if (lowerInput.contains(sensitiveWord.toLowerCase())) {
                return reject("SENSITIVE_WORD", sensitiveWord, input, "输入包含不当内容，请修改后重试");
            }
        }
        for (Pattern pattern : INJECTION_PATTERNS) {
            if (pattern.matcher(input).find()) {
                return reject("INJECTION", pattern.pattern(), input, "检测到恶意输入，请求被拒绝");
            }
        }
        return success();
    }

    /**
     * 构造 fatal 结果：message 给前端看，cause 带审计字段供异常处理器解包。
     */
    private InputGuardrailResult reject(String ruleType, String ruleDetail, String input, String message) {
        return fatal(message, new GuardrailBlockedException(ruleType, ruleDetail, input, message));
    }
}
