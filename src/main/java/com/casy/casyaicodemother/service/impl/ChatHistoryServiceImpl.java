package com.casy.casyaicodemother.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.casy.casyaicodemother.constant.ChatHistoryConstant;
import com.casy.casyaicodemother.constant.UserConstant;
import com.casy.casyaicodemother.exception.BusinessException;
import com.casy.casyaicodemother.exception.ErrorCode;
import com.casy.casyaicodemother.exception.ThrowUtils;
import com.casy.casyaicodemother.mapper.AppMapper;
import com.casy.casyaicodemother.mapper.ChatHistoryMapper;
import com.casy.casyaicodemother.model.dto.chathistory.ChatHistoryQueryRequest;
import com.casy.casyaicodemother.model.entity.App;
import com.casy.casyaicodemother.model.entity.ChatHistory;
import com.casy.casyaicodemother.model.entity.User;
import com.casy.casyaicodemother.model.enums.MessageTypeEnum;
import com.casy.casyaicodemother.model.vo.app.AppVO;
import com.casy.casyaicodemother.model.vo.chathistory.ChatHistoryVO;
import com.casy.casyaicodemother.model.vo.user.UserVO;
import com.casy.casyaicodemother.service.ChatHistoryService;
import com.casy.casyaicodemother.service.UserService;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ChatHistoryServiceImpl extends ServiceImpl<ChatHistoryMapper, ChatHistory> implements ChatHistoryService {

    @Resource
    private AppMapper appMapper;

    @Resource
    private UserService userService;

    @Override
    public long saveUserMessage(Long appId, String message, User loginUser) {
        return saveMessage(appId, message, MessageTypeEnum.USER.getValue(), loginUser, null);
    }

    @Override
    public long saveAiMessage(Long appId, Long parentId, String message, User loginUser) {
        return saveMessage(appId, message, MessageTypeEnum.AI.getValue(), loginUser, parentId);
    }

    @Override
    public long saveErrorMessage(Long appId, Long parentId, String errorMessage, User loginUser) {
        String message = "生成失败：" + (StrUtil.isBlank(errorMessage) ? "未知错误" : errorMessage);
        return saveMessage(appId, message, MessageTypeEnum.AI.getValue(), loginUser, parentId);
    }

    @Override
    public boolean deleteByAppId(Long appId) {
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR);
        return remove(QueryWrapper.create().eq("app_id", appId));
    }

    @Override
    public Page<ChatHistoryVO> listAppChatHistoryByPage(ChatHistoryQueryRequest chatHistoryQueryRequest, User loginUser) {
        ThrowUtils.throwIf(chatHistoryQueryRequest == null, ErrorCode.PARAMS_ERROR);
        Long appId = chatHistoryQueryRequest.getAppId();
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 ID 不能为空");
        App app = appMapper.selectOneById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        checkChatHistoryViewAuth(app, loginUser);
        int pageNum = chatHistoryQueryRequest.getPageNum();
        ThrowUtils.throwIf(chatHistoryQueryRequest.getPageSize() > ChatHistoryConstant.MAX_PAGE_SIZE,
                ErrorCode.PARAMS_ERROR, "每页最多查询 20 条对话");
        int pageSize = chatHistoryQueryRequest.getPageSize() <= 0
                ? ChatHistoryConstant.DEFAULT_PAGE_SIZE
                : Math.min(chatHistoryQueryRequest.getPageSize(), ChatHistoryConstant.MAX_PAGE_SIZE);
        if (StrUtil.isBlank(chatHistoryQueryRequest.getSortField())) {
            chatHistoryQueryRequest.setSortField("create_time");
        }
        if (StrUtil.isBlank(chatHistoryQueryRequest.getSortOrder())) {
            chatHistoryQueryRequest.setSortOrder("descend");
        }
        Page<ChatHistory> chatHistoryPage = page(Page.of(pageNum, pageSize), getQueryWrapper(chatHistoryQueryRequest));
        return toChatHistoryVOPage(chatHistoryPage, pageNum, pageSize, false);
    }

    @Override
    public Page<ChatHistoryVO> listChatHistoryByPage(ChatHistoryQueryRequest chatHistoryQueryRequest) {
        ThrowUtils.throwIf(chatHistoryQueryRequest == null, ErrorCode.PARAMS_ERROR);
        int pageNum = chatHistoryQueryRequest.getPageNum();
        int pageSize = chatHistoryQueryRequest.getPageSize() <= 0
                ? ChatHistoryConstant.DEFAULT_PAGE_SIZE
                : chatHistoryQueryRequest.getPageSize();
        if (StrUtil.isBlank(chatHistoryQueryRequest.getSortField())) {
            chatHistoryQueryRequest.setSortField("create_time");
        }
        if (StrUtil.isBlank(chatHistoryQueryRequest.getSortOrder())) {
            chatHistoryQueryRequest.setSortOrder("descend");
        }
        Page<ChatHistory> chatHistoryPage = page(Page.of(pageNum, pageSize), getQueryWrapper(chatHistoryQueryRequest));
        return toChatHistoryVOPage(chatHistoryPage, pageNum, pageSize, true);
    }

    @Override
    public ChatHistoryVO getChatHistoryVO(ChatHistory chatHistory) {
        if (chatHistory == null) {
            return null;
        }
        ChatHistoryVO chatHistoryVO = new ChatHistoryVO();
        BeanUtil.copyProperties(chatHistory, chatHistoryVO, "createTime");
        chatHistoryVO.setCreateTime(toLocalDateTime(chatHistory.getCreateTime()));
        return chatHistoryVO;
    }

    @Override
    public List<ChatHistoryVO> getChatHistoryVOList(List<ChatHistory> chatHistoryList, boolean includeAppInfo) {
        if (CollUtil.isEmpty(chatHistoryList)) {
            return new ArrayList<>();
        }
        Map<Long, UserVO> userVOMap = buildUserVOMap(
                chatHistoryList.stream().map(ChatHistory::getUserId).collect(Collectors.toSet()));
        Map<Long, AppVO> appVOMap = includeAppInfo
                ? buildAppVOMap(chatHistoryList.stream().map(ChatHistory::getAppId).collect(Collectors.toSet()))
                : Collections.emptyMap();
        return chatHistoryList.stream().map(chatHistory -> {
            ChatHistoryVO chatHistoryVO = getChatHistoryVO(chatHistory);
            chatHistoryVO.setUser(userVOMap.get(chatHistory.getUserId()));
            if (includeAppInfo) {
                chatHistoryVO.setApp(appVOMap.get(chatHistory.getAppId()));
            }
            return chatHistoryVO;
        }).collect(Collectors.toList());
    }

    @Override
    public QueryWrapper getQueryWrapper(ChatHistoryQueryRequest chatHistoryQueryRequest) {
        if (chatHistoryQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }
        Long id = chatHistoryQueryRequest.getId();
        Long appId = chatHistoryQueryRequest.getAppId();
        Long userId = chatHistoryQueryRequest.getUserId();
        String messageType = chatHistoryQueryRequest.getMessageType();
        String sortField = chatHistoryQueryRequest.getSortField();
        String sortOrder = chatHistoryQueryRequest.getSortOrder();
        if (StrUtil.isNotBlank(messageType)) {
            ThrowUtils.throwIf(MessageTypeEnum.getEnumByValue(messageType) == null,
                    ErrorCode.PARAMS_ERROR, "消息类型无效");
        }
        return QueryWrapper.create()
                .eq("id", id)
                .eq("app_id", appId)
                .eq("user_id", userId)
                .eq("message_type", messageType, StrUtil.isNotBlank(messageType))
                .orderBy(sortField, "ascend".equals(sortOrder));
    }

    private long saveMessage(Long appId, String message, String messageType, User loginUser, Long parentId) {
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(StrUtil.isBlank(message), ErrorCode.PARAMS_ERROR, "消息内容不能为空");
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR);
        ChatHistory chatHistory = ChatHistory.builder()
                .message(message)
                .messageType(messageType)
                .appId(appId)
                .userId(loginUser.getId())
                .parentId(parentId)
                .build();
        boolean result = save(chatHistory);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return chatHistory.getId();
    }

    private void checkChatHistoryViewAuth(App app, User loginUser) {
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR);
        if (UserConstant.ADMIN_ROLE.equals(loginUser.getUserRole())) {
            return;
        }
        ThrowUtils.throwIf(!app.getUserId().equals(loginUser.getId()),
                ErrorCode.NO_AUTH_ERROR, "无权查看该应用对话历史");
    }

    private Page<ChatHistoryVO> toChatHistoryVOPage(Page<ChatHistory> chatHistoryPage, int pageNum, int pageSize, boolean includeAppInfo) {
        Page<ChatHistoryVO> chatHistoryVOPage = new Page<>(pageNum, pageSize, chatHistoryPage.getTotalRow());
        chatHistoryVOPage.setRecords(getChatHistoryVOList(chatHistoryPage.getRecords(), includeAppInfo));
        return chatHistoryVOPage;
    }

    private Map<Long, UserVO> buildUserVOMap(Set<Long> userIds) {
        if (CollUtil.isEmpty(userIds)) {
            return Collections.emptyMap();
        }
        userIds.removeIf(Objects::isNull);
        if (CollUtil.isEmpty(userIds)) {
            return Collections.emptyMap();
        }
        return userService.listByIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, userService::getUserVO));
    }

    private Map<Long, AppVO> buildAppVOMap(Set<Long> appIds) {
        if (CollUtil.isEmpty(appIds)) {
            return Collections.emptyMap();
        }
        appIds.removeIf(Objects::isNull);
        if (CollUtil.isEmpty(appIds)) {
            return Collections.emptyMap();
        }
        return appMapper.selectListByQuery(QueryWrapper.create().in("id", appIds)).stream()
                .collect(Collectors.toMap(App::getId, app -> {
                    AppVO appVO = new AppVO();
                    BeanUtil.copyProperties(app, appVO);
                    return appVO;
                }));
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        if (timestamp == null) {
            return null;
        }
        return timestamp.toLocalDateTime();
    }
}
