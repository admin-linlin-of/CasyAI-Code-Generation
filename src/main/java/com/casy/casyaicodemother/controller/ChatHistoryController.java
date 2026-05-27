package com.casy.casyaicodemother.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckRole;
import com.casy.casyaicodemother.common.BaseResponse;
import com.casy.casyaicodemother.common.ResultUtils;
import com.casy.casyaicodemother.constant.UserConstant;
import com.casy.casyaicodemother.exception.ErrorCode;
import com.casy.casyaicodemother.exception.ThrowUtils;
import com.casy.casyaicodemother.model.dto.chathistory.ChatHistoryQueryRequest;
import com.casy.casyaicodemother.model.entity.User;
import com.casy.casyaicodemother.model.vo.chathistory.ChatHistoryVO;
import com.casy.casyaicodemother.service.ChatHistoryService;
import com.casy.casyaicodemother.service.UserService;
import com.mybatisflex.core.paginate.Page;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/chatHistory")
public class ChatHistoryController {

    @Resource
    private ChatHistoryService chatHistoryService;

    @Resource
    private UserService userService;

    @SaCheckLogin
    @PostMapping("/list/app/page/vo")
    public BaseResponse<Page<ChatHistoryVO>> listAppChatHistoryByPage(@RequestBody ChatHistoryQueryRequest chatHistoryQueryRequest) {
        ThrowUtils.throwIf(chatHistoryQueryRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser();
        Page<ChatHistoryVO> chatHistoryVOPage = chatHistoryService.listAppChatHistoryByPage(chatHistoryQueryRequest, loginUser);
        return ResultUtils.success(chatHistoryVOPage);
    }

    @SaCheckRole(UserConstant.ADMIN_ROLE)
    @PostMapping("/list/page/vo")
    public BaseResponse<Page<ChatHistoryVO>> listChatHistoryByPage(@RequestBody ChatHistoryQueryRequest chatHistoryQueryRequest) {
        ThrowUtils.throwIf(chatHistoryQueryRequest == null, ErrorCode.PARAMS_ERROR);
        Page<ChatHistoryVO> chatHistoryVOPage = chatHistoryService.listChatHistoryByPage(chatHistoryQueryRequest);
        return ResultUtils.success(chatHistoryVOPage);
    }
}
