package com.casy.casyaicodemother.service;

import com.casy.casyaicodemother.model.dto.chathistory.ChatHistoryQueryRequest;
import com.casy.casyaicodemother.model.entity.ChatHistory;
import com.casy.casyaicodemother.model.entity.User;
import com.casy.casyaicodemother.model.vo.chathistory.ChatHistoryVO;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.service.IService;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;

import java.util.List;

public interface ChatHistoryService extends IService<ChatHistory> {

    /**
     * 加载记忆到缓存
     *
     * @param appId 应用ID
     * @param chatMemory 会话记忆对象
     * @param maxCount 最大记忆数
     * @return 记忆加载到缓存的数量
     */
    int loadChatHistoryToMemory(Long appId, MessageWindowChatMemory chatMemory, int maxCount);

    /**
     * 保存用户消息
     *
     * @param appId 应用id
     * @param message 消息
     * @param loginUser 登录用户
     * @return 消息ID
     */
    long saveUserMessage(Long appId, String message, User loginUser);

    /**
     * 保存AI消息
     *
     * @param appId 应用id
     * @param parentId 父消息id
     * @param message 消息
     * @param loginUser 登录用户
     * @return 消息ID
     */
    long saveAiMessage(Long appId, Long parentId, String message, User loginUser);

    /**
     * 保存AI异常消息
     *
     * @param appId 应用id
     * @param parentId 父消息id
     * @param errorMessage 异常消息
     * @param loginUser 登录用户
     * @return 消息ID
     */
    long saveErrorMessage(Long appId, Long parentId, String errorMessage, User loginUser);

    /**
     * 删除消息记录
     *
     * @param appId 应用id
     * @return 删除结果
     */
    boolean deleteByAppId(Long appId);

    /**
     *
     * @param chatHistoryQueryRequest
     * @param loginUser
     * @return
     */
    Page<ChatHistoryVO> listAppChatHistoryByPage(ChatHistoryQueryRequest chatHistoryQueryRequest, User loginUser);

    Page<ChatHistoryVO> listChatHistoryByPage(ChatHistoryQueryRequest chatHistoryQueryRequest);

    ChatHistoryVO getChatHistoryVO(ChatHistory chatHistory);

    List<ChatHistoryVO> getChatHistoryVOList(List<ChatHistory> chatHistoryList, boolean includeAppInfo);

    QueryWrapper getQueryWrapper(ChatHistoryQueryRequest chatHistoryQueryRequest);
}
