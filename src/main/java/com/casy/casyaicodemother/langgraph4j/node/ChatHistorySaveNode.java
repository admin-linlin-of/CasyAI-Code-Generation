package com.casy.casyaicodemother.langgraph4j.node;

import com.casy.casyaicodemother.exception.ErrorCode;
import com.casy.casyaicodemother.exception.ThrowUtils;
import com.casy.casyaicodemother.langgraph4j.state.WorkflowContext;
import com.casy.casyaicodemother.model.entity.User;
import com.casy.casyaicodemother.service.ChatHistoryService;
import com.casy.casyaicodemother.service.UserService;
import com.casy.casyaicodemother.util.SpringContextUtil;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.prebuilt.MessagesState;

import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
 * 保存用户消息节点，对齐 chatToGenCode：生成代码前先把用户提示词写入对话历史。
 */
@Slf4j
public class ChatHistorySaveNode {

    public static AsyncNodeAction<MessagesState<String>> create() {
        return node_async(state -> {
            WorkflowContext context = WorkflowContext.getContext(state);
            log.info("执行节点: 保存用户消息");
            if (context.getUserMessageId() != null && context.getUserMessageId() > 0) {
                context.setCurrentStep("保存用户消息");
                log.info("已有 userMessageId={}，跳过重复写入", context.getUserMessageId());
                return WorkflowContext.saveContext(context);
            }
            Long appId = context.getAppId();
            Long userId = context.getUserId();
            ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "工作流缺少真实 appId");
            ThrowUtils.throwIf(userId == null || userId <= 0, ErrorCode.PARAMS_ERROR, "工作流缺少 userId");

            UserService userService = SpringContextUtil.getBean(UserService.class);
            User user = userService.getById(userId);
            ThrowUtils.throwIf(user == null, ErrorCode.NOT_FOUND_ERROR, "用户不存在");

            ChatHistoryService chatHistoryService = SpringContextUtil.getBean(ChatHistoryService.class);
            long userMessageId = chatHistoryService.saveUserMessage(appId, context.getOriginalPrompt(), user);
            context.setUserMessageId(userMessageId);
            context.setCurrentStep("保存用户消息");
            log.info("已保存用户消息 userMessageId={}, appId={}", userMessageId, appId);
            return WorkflowContext.saveContext(context);
        });
    }
}
