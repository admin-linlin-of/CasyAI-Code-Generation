package com.casy.casyaicodemother.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Sa-Token 使用全局拦截器完成注解鉴权功能，为了不为项目带来不必要的性能负担，拦截器默认处于关闭状态。
 * 因此，为了使用注解鉴权，必须手动将 Sa-Token 的全局拦截器注册到你项目中
 */
@Configuration
public class SaTokenConfigure implements WebMvcConfigurer {

    /**
     * SseEmitter 等在虚拟线程上 complete 后，Spring 会发起 {@link DispatcherType#ASYNC} 二次派发，
     * 此时 Sa-Token 上下文不在该线程，{@code @SaCheckLogin} 会抛 SaTokenContextException。
     * 首次 REQUEST 派发已完成鉴权，ASYNC 派发跳过即可。
     */
    private static final HandlerInterceptor SA_INTERCEPTOR = new SaInterceptor();

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
                    throws Exception {
                if (DispatcherType.ASYNC == request.getDispatcherType()) {
                    return true;
                }
                return SA_INTERCEPTOR.preHandle(request, response, handler);
            }

            @Override
            public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler,
                                   org.springframework.web.servlet.ModelAndView modelAndView) throws Exception {
                if (DispatcherType.ASYNC == request.getDispatcherType()) {
                    return;
                }
                SA_INTERCEPTOR.postHandle(request, response, handler, modelAndView);
            }

            @Override
            public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
                                        Exception ex) throws Exception {
                if (DispatcherType.ASYNC == request.getDispatcherType()) {
                    return;
                }
                SA_INTERCEPTOR.afterCompletion(request, response, handler, ex);
            }
        }).addPathPatterns("/**");
    }
}
