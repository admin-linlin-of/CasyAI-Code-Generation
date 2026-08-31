package com.casy.casyaicodemother.exception;

import dev.langchain4j.guardrail.InputGuardrailException;
import lombok.Getter;

@Getter
public class GuardrailBlockedException extends BusinessException {

    private final String ruleType;
    private final String ruleDetail;
    private final String inputContent;

    public GuardrailBlockedException(String ruleType, String ruleDetail, String inputContent, String message) {
        super(ErrorCode.GUARDRAIL_BLOCKED, message);
        this.ruleType = ruleType;
        this.ruleDetail = ruleDetail;
        this.inputContent = inputContent == null
                ? ""
                : (inputContent.length() > 4000 ? inputContent.substring(0, 4000) : inputContent);
    }

    public static GuardrailBlockedException unwrap(Throwable e) {
        while (e != null) {
            if (e instanceof GuardrailBlockedException blocked) {
                return blocked;
            }
            e = e.getCause();
        }
        return null;
    }

    public static boolean isGuardrail(Throwable e) {
        while (e != null) {
            if (e instanceof GuardrailBlockedException || e instanceof InputGuardrailException) {
                return true;
            }
            e = e.getCause();
        }
        return false;
    }

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
