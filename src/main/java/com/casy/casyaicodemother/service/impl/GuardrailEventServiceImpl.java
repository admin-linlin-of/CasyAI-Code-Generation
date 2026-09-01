package com.casy.casyaicodemother.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.dev33.satoken.stp.StpUtil;
import com.casy.casyaicodemother.exception.GuardrailBlockedException;
import com.casy.casyaicodemother.mapper.GuardrailEventMapper;
import com.casy.casyaicodemother.model.entity.GuardrailEvent;
import com.casy.casyaicodemother.model.entity.User;
import com.casy.casyaicodemother.service.GuardrailEventService;
import com.casy.casyaicodemother.service.UserService;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 护轨拦截事件落库实现。
 * 聊天 SSE 的 Flux 错误发生在 Reactor 线程上，RequestContextHolder 可能为空，
 * 因此用户、appId 优先用调用方传入的参数，request 仅作 URI / IP 补充。
 */
@Slf4j
@Service
public class GuardrailEventServiceImpl extends ServiceImpl<GuardrailEventMapper, GuardrailEvent>
        implements GuardrailEventService {

    @Resource
    private UserService userService;

    /**
     * {@inheritDoc}
     */
    @Override
    public void record(Throwable error, User loginUser, Long appId, HttpServletRequest request, String handleResult) {
        try {
            // 解开 LangChain4j 包装，取规则类型 / 命中细节 / 原文；解不开则记 UNKNOWN
            GuardrailBlockedException blocked = GuardrailBlockedException.unwrap(error);
            String ruleType = blocked != null ? blocked.getRuleType() : "UNKNOWN";
            String ruleDetail = blocked != null ? blocked.getRuleDetail() : StrUtil.blankToDefault(error.getMessage(), "");
            String inputContent = blocked != null ? blocked.getInputContent() : null;
            String failMessage = GuardrailBlockedException.userMessage(error);
            User user = loginUser != null ? loginUser : tryGetLoginUser();
            Long resolvedAppId = appId != null ? appId : parseAppId(request);
            String uri = request != null ? request.getRequestURI() : null;
            String ip = resolveClientIp(request);
            log.warn("护轨拦截 userId={} account={} ruleType={} ruleDetail={} uri={} ip={} result={} message={} input={}",
                    user != null ? user.getId() : null,
                    user != null ? user.getUserAccount() : null,
                    ruleType, ruleDetail, uri, ip, handleResult, failMessage, inputContent);
            GuardrailEvent event = GuardrailEvent.builder()
                    .userId(user != null ? user.getId() : null)
                    .userAccount(user != null ? user.getUserAccount() : null)
                    .userName(user != null ? user.getUserName() : null)
                    .appId(resolvedAppId)
                    .requestUri(uri)
                    .requestIp(ip)
                    .inputContent(inputContent)
                    .ruleType(ruleType)
                    .ruleDetail(StrUtil.maxLength(ruleDetail, 512))
                    .failMessage(StrUtil.maxLength(failMessage, 512))
                    .handleResult(handleResult)
                    .build();
            this.save(event);
        } catch (Exception ex) {
            // 审计失败不能阻断主流程，否则前端收不到 business-error
            log.warn("护轨拦截事件落库失败", ex);
        }
    }

    /**
     * 全局异常处理器等未传入 User 时，从当前登录态补全；未登录或查询失败返回 null。
     */
    private User tryGetLoginUser() {
        try {
            if (!StpUtil.isLogin()) {
                return null;
            }
            return userService.getLoginUser();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 从查询参数 appId 解析应用 ID，格式错误时忽略。
     */
    private Long parseAppId(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        try {
            String value = request.getParameter("appId");
            return StrUtil.isBlank(value) ? null : Long.valueOf(value);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 解析客户端 IP：X-Forwarded-For → X-Real-IP → remoteAddr，多级代理只取第一个。
     */
    private String resolveClientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String ip = request.getHeader("X-Forwarded-For");
        if (StrUtil.isBlank(ip) || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (StrUtil.isBlank(ip) || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
