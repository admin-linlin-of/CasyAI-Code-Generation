package com.casy.casyaicodemother.exception;

import dev.langchain4j.guardrail.InputGuardrailException;
import lombok.Getter;

/**
 * 输入护轨拦截异常。
 * <p>
 * LangChain4j 的 {@code InputGuardrail.fatal(message, cause)} 会把本异常作为 cause
 * 包装成 {@link InputGuardrailException} 再抛出，因此流式调用侧拿到的往往是包装后的异常。
 * 本类额外携带触发规则、命中细节和原始输入，供 SSE 推送准确错误信息、以及落库审计使用。
 */
@Getter
public class GuardrailBlockedException extends BusinessException {

    /**
     * 触发规则类型：LENGTH / EMPTY / SENSITIVE_WORD / INJECTION
     */
    private final String ruleType;

    /**
     * 命中细节：超长时的 actual 长度、敏感词原文、或注入正则
     */
    private final String ruleDetail;

    /**
     * 被拦截的用户输入（超过 4000 字会截断，避免异常对象过大）
     */
    private final String inputContent;

    /**
     * @param ruleType     规则类型
     * @param ruleDetail   命中细节
     * @param inputContent  原始输入
     * @param message       返回给前端的可读错误信息
     */
    public GuardrailBlockedException(String ruleType, String ruleDetail, String inputContent, String message) {
        super(ErrorCode.GUARDRAIL_BLOCKED, message);
        this.ruleType = ruleType;
        this.ruleDetail = ruleDetail;
        this.inputContent = inputContent == null
                ? ""
                : (inputContent.length() > 4000 ? inputContent.substring(0, 4000) : inputContent);
    }

    /**
     * 沿 cause 链解开 LangChain4j 包装，取出本异常。
     * 找不到则返回 null（例如其它护轨抛出的纯 {@link InputGuardrailException}）。
     */
    public static GuardrailBlockedException unwrap(Throwable e) {
        while (e != null) {
            if (e instanceof GuardrailBlockedException blocked) {
                return blocked;
            }
            e = e.getCause();
        }
        return null;
    }

    /**
     * 判断异常链上是否存在护轨拦截（本异常或 LangChain4j 的 InputGuardrailException）。
     */
    public static boolean isGuardrail(Throwable e) {
        while (e != null) {
            if (e instanceof GuardrailBlockedException || e instanceof InputGuardrailException) {
                return true;
            }
            e = e.getCause();
        }
        return false;
    }

    /**
     * 提取应展示给前端的错误文案：优先用本异常的 message，
     * 其次用 InputGuardrailException 的 cause 信息，最后回落到通用「输入被安全护轨拦截」。
     */
    public static String userMessage(Throwable e) {
        GuardrailBlockedException blocked = unwrap(e);
        if (blocked != null) {
            return blocked.getMessage();
        }
        while (e != null) {
            if (e instanceof InputGuardrailException) {
                return e.getCause() != null && e.getCause().getMessage() != null
                        ? e.getCause().getMessage()
                        : ErrorCode.GUARDRAIL_BLOCKED.getMessage();
            }
            e = e.getCause();
        }
        return ErrorCode.GUARDRAIL_BLOCKED.getMessage();
    }
}
