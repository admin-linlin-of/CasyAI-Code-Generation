package com.casy.casyaicodemother.service;

import com.casy.casyaicodemother.model.dto.chathistory.ChatHistoryQueryRequest;
import com.casy.casyaicodemother.model.entity.ChatHistory;
import com.casy.casyaicodemother.model.entity.User;
import com.casy.casyaicodemother.model.vo.chathistory.ChatHistoryVO;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.service.IService;

import java.util.List;

public interface ChatHistoryService extends IService<ChatHistory> {

    long saveUserMessage(Long appId, String message, User loginUser);

    long saveAiMessage(Long appId, Long parentId, String message, User loginUser);

    long saveErrorMessage(Long appId, Long parentId, String errorMessage, User loginUser);

    boolean deleteByAppId(Long appId);

    Page<ChatHistoryVO> listAppChatHistoryByPage(ChatHistoryQueryRequest chatHistoryQueryRequest, User loginUser);

    Page<ChatHistoryVO> listChatHistoryByPage(ChatHistoryQueryRequest chatHistoryQueryRequest);

    ChatHistoryVO getChatHistoryVO(ChatHistory chatHistory);

    List<ChatHistoryVO> getChatHistoryVOList(List<ChatHistory> chatHistoryList, boolean includeAppInfo);

    QueryWrapper getQueryWrapper(ChatHistoryQueryRequest chatHistoryQueryRequest);
}
