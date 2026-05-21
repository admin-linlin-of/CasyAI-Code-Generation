package com.casy.casyaicodemother.exception;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import com.casy.casyaicodemother.common.BaseResponse;
import com.casy.casyaicodemother.common.ResultUtils;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理
 * 由于本项目使用的 Spring Boot 版本 >= 3.4、并且是 OpenAPI 3 版本的 Knife4j，
 * 这会导致 @RestControllerAdvice 注解不兼容，所以必须给这个类加上 @Hidden 注解，不被 Swagger 加载。
 *
 * Sa-Token 异常与前端 axios 拦截器约定：
 * - 40100：未登录 → 前端跳转登录页
 * - 40101：已登录但无权限 → 前端仅提示
 */
@Hidden
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public BaseResponse<?> businessExceptionHandler(BusinessException e) {
        log.error("BusinessException", e);
        return ResultUtils.error(e.getCode(), e.getMessage());
    }

    /** Sa-Token 未登录异常 → 40100，前端统一跳转登录 */
    @ExceptionHandler(NotLoginException.class)
    public BaseResponse<?> handlerNotLoginException(NotLoginException nle) {
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
        return ResultUtils.error(ErrorCode.NOT_LOGIN_ERROR, message);
    }

    /** @SaCheckPermission 校验失败 → 40101 */
    @ExceptionHandler(NotPermissionException.class)
    public BaseResponse<?> handlerNotPermissionException(NotPermissionException e) {
        return ResultUtils.error(ErrorCode.NO_AUTH_ERROR, "无权限：" + e.getPermission());
    }

    /** @SaCheckRole 校验失败 → 40101 */
    @ExceptionHandler(NotRoleException.class)
    public BaseResponse<?> handlerNotRoleException(NotRoleException e) {
        return ResultUtils.error(ErrorCode.NO_AUTH_ERROR, "无权限：" + e.getRole());
    }

    @ExceptionHandler(RuntimeException.class)
    public BaseResponse<?> runtimeExceptionHandler(RuntimeException e) {
        log.error("RuntimeException", e);
        return ResultUtils.error(ErrorCode.SYSTEM_ERROR, "系统错误");
    }

}
