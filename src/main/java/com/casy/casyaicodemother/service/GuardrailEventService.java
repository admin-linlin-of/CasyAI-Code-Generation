package com.casy.casyaicodemother.service;

import com.casy.casyaicodemother.model.entity.GuardrailEvent;
import com.casy.casyaicodemother.model.entity.User;
import com.mybatisflex.core.service.IService;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 输入护轨拦截事件服务。
 */
public interface GuardrailEventService extends IService<GuardrailEvent> {

    /**
     * 记录一次护轨拦截：写 warn 日志并插入 t_guardrail_event。
     * 落库失败只打日志，不向上抛，避免影响 SSE 错误推送。
     *
     * @param error        护轨相关异常（可能被 LangChain4j 包装）
     * @param loginUser     当前登录用户，可为 null（则尝试从 Sa-Token 补全）
     * @param appId        应用 ID，可为 null（则尝试从 request 参数 appId 解析）
     * @param request      当前请求，用于取 URI / IP
     * @param handleResult 处理结果：SSE_PUSHED 或 JSON_RETURNED
     */
    void record(Throwable error, User loginUser, Long appId, HttpServletRequest request, String handleResult);
}
