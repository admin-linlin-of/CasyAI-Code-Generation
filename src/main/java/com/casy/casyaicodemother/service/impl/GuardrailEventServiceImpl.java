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

@Slf4j
@Service
public class GuardrailEventServiceImpl extends ServiceImpl<GuardrailEventMapper, GuardrailEvent>
        implements GuardrailEventService {

    @Resource
    private UserService userService;

    @Override
    public void record(Throwable error, User loginUser, Long appId, HttpServletRequest request, String handleResult) {
        try {
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
            log.warn("护轨拦截事件落库失败", ex);
        }
    }

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
