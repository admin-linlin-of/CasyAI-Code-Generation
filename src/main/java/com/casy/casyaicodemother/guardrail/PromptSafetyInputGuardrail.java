package com.casy.casyaicodemother.guardrail;

import com.casy.casyaicodemother.exception.GuardrailBlockedException;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.InputGuardrailResult;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

public class PromptSafetyInputGuardrail implements InputGuardrail {

    private static final List<String> SENSITIVE_WORDS = Arrays.asList(
            "忽略之前的指令", "ignore previous instructions", "ignore above",
            "破解", "hack", "绕过", "bypass", "越狱", "jailbreak"
    );

    private static final List<Pattern> INJECTION_PATTERNS = Arrays.asList(
            Pattern.compile("(?i)ignore\\s+(?:previous|above|all)\\s+(?:instructions?|commands?|prompts?)"),
            Pattern.compile("(?i)(?:forget|disregard)\\s+(?:everything|all)\\s+(?:above|before)"),
            Pattern.compile("(?i)(?:pretend|act|behave)\\s+(?:as|like)\\s+(?:if|you\\s+are)"),
            Pattern.compile("(?i)system\\s*:\\s*you\\s+are"),
            Pattern.compile("(?i)new\\s+(?:instructions?|commands?|prompts?)\\s*:")
    );

    @Override
    public InputGuardrailResult validate(UserMessage userMessage) {
        String input = userMessage.singleText();
        if (input.length() > 1000) {
            return reject("LENGTH", "max=1000,actual=" + input.length(), input, "输入内容过长，不要超过 1000 字");
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

    private InputGuardrailResult reject(String ruleType, String ruleDetail, String input, String message) {
        return fatal(message, new GuardrailBlockedException(ruleType, ruleDetail, input, message));
    }
}
