package com.casy.casyaicodemother.exception;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import cn.hutool.json.JSONUtil;
import com.casy.casyaicodemother.common.BaseResponse;
import com.casy.casyaicodemother.common.ResultUtils;
import com.casy.casyaicodemother.service.GuardrailEventService;
import dev.langchain4j.guardrail.InputGuardrailException;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 全局异常处理
 * 由于本项目使用的 Spring Boot 版本 >= 3.4、并且是 OpenAPI 3 版本的 Knife4j，
 * 这会导致 @RestControllerAdvice 注解不兼容，所以必须给这个类加上 @Hidden 注解，不被 Swagger 加载。
 *
 * Sa-Token 异常与前端 axios 拦截器约定：
 * - 40100：未登录 → 前端跳转登录页
 * - 40101：已登录但无权限 → 前端仅提示
 *
 * SSE（text/event-stream）不能返回 BaseResponse JSON，否则会报
 * No converter for BaseResponse with preset Content-Type 'text/event-stream'。
 */
@Hidden
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @Resource
    private GuardrailEventService guardrailEventService;

    @ExceptionHandler({InputGuardrailException.class, GuardrailBlockedException.class})
    public Object guardrailExceptionHandler(RuntimeException e,
                                            HttpServletRequest request,
                                            HttpServletResponse response) {
        String message = GuardrailBlockedException.userMessage(e);
        String handleResult = isSseRequest(request, response) ? "SSE_PUSHED" : "JSON_RETURNED";
        guardrailEventService.record(e, null, null, request, handleResult);
        return respond(ErrorCode.GUARDRAIL_BLOCKED.getCode(), message, request, response);
    }

    @ExceptionHandler(BusinessException.class)
    public Object businessExceptionHandler(BusinessException e,
                                           HttpServletRequest request,
                                           HttpServletResponse response) {
        log.error("BusinessException", e);
        return respond(e.getCode(), e.getMessage(), request, response);
    }

    /** Sa-Token 未登录异常 → 40100，前端统一跳转登录 */
    @ExceptionHandler(NotLoginException.class)
    public Object handlerNotLoginException(NotLoginException nle,
                                           HttpServletRequest request,
                                           HttpServletResponse response) {
        String message;
        if (nle.getType().equals(NotLoginException.NOT_TOKEN)) {
            message = "未能读取到有效 token";
        } else if (nle.getType().equals(NotLoginException.INVALID_TOKEN)) {
            message = "token 无效";
        } else if (nle.getType().equals(NotLoginException.TOKEN_TIMEOUT)) {
            message = "token 已过期";
        } else if (nle.getType().equals(NotLoginException.BE_REPLACED)) {
            message = "token 已被顶下线";
        } else if (nle.getType().equals(NotLoginException.KICK_OUT)) {
            message = "token 已被踢下线";
        } else if (nle.getType().equals(NotLoginException.TOKEN_FREEZE)) {
            message = "token 已被冻结";
        } else if (nle.getType().equals(NotLoginException.NO_PREFIX)) {
            message = "未按照指定前缀提交 token";
        } else {
            message = "当前会话未登录";
        }
        return respond(ErrorCode.NOT_LOGIN_ERROR.getCode(), message, request, response);
    }

    /** @SaCheckPermission 校验失败 → 40101 */
    @ExceptionHandler(NotPermissionException.class)
    public Object handlerNotPermissionException(NotPermissionException e,
                                                HttpServletRequest request,
                                                HttpServletResponse response) {
        return respond(ErrorCode.NO_AUTH_ERROR.getCode(), "无权限：" + e.getPermission(), request, response);
    }

    /** @SaCheckRole 校验失败 → 40101 */
    @ExceptionHandler(NotRoleException.class)
    public Object handlerNotRoleException(NotRoleException e,
                                          HttpServletRequest request,
                                          HttpServletResponse response) {
        return respond(ErrorCode.NO_AUTH_ERROR.getCode(), "无权限：" + e.getRole(), request, response);
    }

    @ExceptionHandler(RuntimeException.class)
    public Object runtimeExceptionHandler(RuntimeException e,
                                          HttpServletRequest request,
                                          HttpServletResponse response) {
        log.error("RuntimeException", e);
        return respond(ErrorCode.SYSTEM_ERROR.getCode(), "系统错误", request, response);
    }

    /**
     * 按接口类型写出错误：普通 HTTP 返回 JSON；SSE 返回 event-stream 文本。
     * <p>
     * SseEmitter.completeWithError 会把异常交回本方法。若仍返回 {@link BaseResponse}，
     * Spring 会按 JSON 找转换器，但响应头已是 text/event-stream，于是抛
     * HttpMessageNotWritableException。
     * <p>
     * 事件名 business-error 与前端 EventSource.addEventListener('business-error') 对齐，
     * 避免占用浏览器默认 error 事件。随后发 done 结束流。
     * 末尾必须有空行（\n\n），否则浏览器会一直等这条 SSE 结束。
     * <p>
     * 流已经向前端推过数据后 response.isCommitted() == true，不能再改 Content-Type / 状态码，
     * 只能往已打开的输出流里追加一段 SSE。尚未提交时补齐 SSE 响应头再写出。
     * 返回 null 表示 body 已自行写完。
     */
    private Object respond(int code, String message, HttpServletRequest request, HttpServletResponse response) {
        BaseResponse<?> body = ResultUtils.error(code, message);
        if (!isSseRequest(request, response)) {
            return body;
        }
        String sse = "event: business-error\ndata: " + JSONUtil.toJsonStr(body) + "\n\n"
                + "event: done\ndata: {}\n\n";
        try {
            if (!response.isCommitted()) {
                response.setContentType(MediaType.TEXT_EVENT_STREAM_VALUE);
                response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                response.setHeader(HttpHeaders.CACHE_CONTROL, "no-cache");
                response.setHeader(HttpHeaders.CONNECTION, "keep-alive");
            }
            response.getOutputStream().write(sse.getBytes(StandardCharsets.UTF_8));
            response.flushBuffer();
        } catch (IOException ex) {
            log.warn("SSE 错误事件写入失败", ex);
        }
        return null;
    }

    /**
     * 判断当前请求是否为 SSE。
     * 优先看响应 Content-Type：异步派发时 SseEmitter 往往已经写成 text/event-stream。
     * 流尚未开始时看 Accept：浏览器 EventSource 会带 text/event-stream。
     */
    private boolean isSseRequest(HttpServletRequest request, HttpServletResponse response) {
        String contentType = response.getContentType();
        if (contentType != null && contentType.contains(MediaType.TEXT_EVENT_STREAM_VALUE)) {
            return true;
        }
        String accept = request.getHeader(HttpHeaders.ACCEPT);
        return accept != null && accept.contains(MediaType.TEXT_EVENT_STREAM_VALUE);
    }
}
