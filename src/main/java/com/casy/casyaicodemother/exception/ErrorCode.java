package com.casy.casyaicodemother.exception;

import lombok.Getter;

/**
 * 自定义错误码时，建议跟主流的错误码（比如 HTTP 错误码）的含义保持一致，比如 “未登录” 定义为 40100，和 HTTP 401 错误（用户需要进行身份认证）保持一致，会更容易理解。
 * 错误码不要完全连续，预留一些间隔，便于后续扩展。
 */
@Getter
public enum ErrorCode {

    SUCCESS(0, "ok"),
    PARAMS_ERROR(40000, "请求参数错误"),
    NOT_LOGIN_ERROR(40100, "未登录"),
    NO_AUTH_ERROR(40101, "无权限"),
    LOGIN_ERROR(40102, "sa-token异常"),
    NOT_FOUND_ERROR(40400, "请求数据不存在"),
    FORBIDDEN_ERROR(40300, "禁止访问"),
    /** 输入护轨拦截（敏感词 / 注入 / 超长等），与 HTTP 403 语义一致 */
    GUARDRAIL_BLOCKED(40310, "输入被安全护轨拦截"),
    SYSTEM_ERROR(50000, "系统内部异常"),
    OPERATION_ERROR(50001, "操作失败"),
    TOO_MANY_REQUEST(42900, "请求过于频繁");

    /**
     * 状态码
     */
    private final int code;

    /**
     * 信息
     */
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

}
