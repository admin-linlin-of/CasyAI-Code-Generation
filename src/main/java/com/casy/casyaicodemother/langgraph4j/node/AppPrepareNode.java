package com.casy.casyaicodemother.langgraph4j.node;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.casy.casyaicodemother.exception.ErrorCode;
import com.casy.casyaicodemother.exception.ThrowUtils;
import com.casy.casyaicodemother.langgraph4j.state.WorkflowContext;
import com.casy.casyaicodemother.model.dto.app.AppAddRequest;
import com.casy.casyaicodemother.model.entity.App;
import com.casy.casyaicodemother.model.entity.User;
import com.casy.casyaicodemother.model.enums.CodeGenTypeEnum;
import com.casy.casyaicodemother.service.AppService;
import com.casy.casyaicodemother.service.UserService;
import com.casy.casyaicodemother.util.SpringContextUtil;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.prebuilt.MessagesState;

import java.util.List;

import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
 * 应用准备节点，对齐首页发起会话：
 * 无 appId 时先创建应用（对应 Home.vue addApp）；
 * 已有 appId 时加载应用并带出已保存的生成类型。
 */
@Slf4j
public class AppPrepareNode {

    public static AsyncNodeAction<MessagesState<String>> create() {
        return node_async(state -> {
            WorkflowContext context = WorkflowContext.getContext(state);
            AppService appService = SpringContextUtil.getBean(AppService.class);
            UserService userService = SpringContextUtil.getBean(UserService.class);
            Long appId = context.getAppId();

            if (appId == null || appId <= 0) {
                // 对齐 Home.vue createAppByPrompt：无 appId 时先落库创建应用
                log.info("执行节点: 创建应用");
                ThrowUtils.throwIf(StrUtil.isBlank(context.getOriginalPrompt()), ErrorCode.PARAMS_ERROR, "提示词不能为空");
                User user = resolveUser(userService, context);
                AppAddRequest request = new AppAddRequest();
                String prompt = context.getOriginalPrompt();
                request.setInitPrompt(prompt);
                request.setAppName(prompt.substring(0, Math.min(prompt.length(), 12)));
                // 首页下拉框已选类型则带入，否则交给 createApp 内部 AI 路由
                if (context.getGenerationType() != null) {
                    request.setCodeGenType(context.getGenerationType().getValue());
                }
                if (context.getModelTypeEnum() != null) {
                    request.setModelType(context.getModelTypeEnum().getModelName());
                }
                long newAppId = appService.createApp(request, user);
                App app = appService.getAppById(newAppId);
                context.setAppId(newAppId);
                context.setUserId(user.getId());
                CodeGenTypeEnum generationType = CodeGenTypeEnum.getEnumByValue(app.getCodeGenType());
                if (generationType != null) {
                    context.setGenerationType(generationType);
                }
                context.setCurrentStep("创建应用");
                log.info("已创建应用 appId={}, codeGenType={}", newAppId, app.getCodeGenType());
            } else {
                // 已有应用：走对话页 fetchAppInfo，使用库中已保存的生成类型
                log.info("执行节点: 加载应用");
                App app = appService.getAppById(appId);
                if (context.getUserId() == null || context.getUserId() <= 0) {
                    context.setUserId(app.getUserId());
                }
                CodeGenTypeEnum generationType = CodeGenTypeEnum.getEnumByValue(app.getCodeGenType());
                if (generationType != null) {
                    context.setGenerationType(generationType);
                }
                context.setCurrentStep("加载应用");
                log.info("已加载应用 appId={}, codeGenType={}", appId, app.getCodeGenType());
            }
            return WorkflowContext.saveContext(context);
        });
    }

    /**
     * 解析创建应用所用用户：优先上下文 userId，否则取库中第一个用户（测试无登录态）。
     */
    private static User resolveUser(UserService userService, WorkflowContext context) {
        if (context.getUserId() != null && context.getUserId() > 0) {
            User user = userService.getById(context.getUserId());
            ThrowUtils.throwIf(user == null, ErrorCode.NOT_FOUND_ERROR, "用户不存在");
            return user;
        }
        List<User> users = userService.list();
        ThrowUtils.throwIf(CollUtil.isEmpty(users), ErrorCode.NOT_FOUND_ERROR, "无可用用户，无法创建应用");
        return users.getFirst();
    }
}
