package com.casy.casyaicodemother.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.casy.casyaicodemother.constant.ChatHistoryConstant;
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
import com.casy.casyaicodemother.util.AppAccessUtils;
import com.casy.casyaicodemother.util.ChatThinkingCodec;
import com.casy.casyaicodemother.util.NumberUtils;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ChatHistoryServiceImpl extends ServiceImpl<ChatHistoryMapper, ChatHistory> implements ChatHistoryService {

    @Resource
    private AppMapper appMapper;

    @Resource
    private UserService userService;

    @Override
    public int loadChatHistoryToMemory(Long appId, MessageWindowChatMemory chatMemory, int maxCount) {
        try {
            // 直接构造查询条件，起始点为 1 而不是 0，用于排除最新的用户消息
            QueryWrapper queryWrapper = QueryWrapper.create()
                    .eq(ChatHistory::getAppId, appId)
                    .orderBy(ChatHistory::getCreateTime, false)
                    .limit(1, maxCount);
            // 查询起始点设置为 1 而不是 0，这是为了排除最新的用户消息。因为在对话流程中，用户消息被添加到数据库后，AI 服务也会自动将用户消息添加到记忆中，如果不排除会导致消息重复。
            // 这涉及对话记忆的保存时机，在我们和ai对话时Langchain会保存这个条对话，这时如果从数据库中加载历史对话不应该包括当前的这条对话数据，防止redis中的对话记忆重复
            List<ChatHistory> historyList  = this.list(queryWrapper);
            if (CollUtil.isEmpty(historyList)) {
                return 0;
            }
            // 反转列表，确保按时间正序（老的在前，新的在后）
            historyList = historyList.reversed();
            // 按时间顺序添加到记忆中
            int loadedCount = 0;
            // 先清理历史缓存，防止重复加载
            chatMemory.clear();
            for (ChatHistory chatHistory : historyList) {
                if (MessageTypeEnum.USER.getValue().equals(chatHistory.getMessageType())) {
                    chatMemory.add(UserMessage.from(chatHistory.getMessage()));
                } else if (MessageTypeEnum.AI.getValue().equals(chatHistory.getMessageType())){
                    // <aiThinking> 只给前端回放；喂给模型的记忆必须去掉 reasoning
                    String messageForMemory = ChatThinkingCodec.stripThinking(chatHistory.getMessage());
                    messageForMemory = ChatThinkingCodec.stripNativeThink(messageForMemory);
                    if (StrUtil.isBlank(messageForMemory)) {
                        continue;
                    }
                    chatMemory.add(AiMessage.from(messageForMemory));
                }
                loadedCount++;
            }
            log.info("成功为 appId: {} 加载了 {} 条历史对话", appId, loadedCount);
            return loadedCount;
        } catch (Exception e) {
            log.error("加载历史对话失败，appId: {}, error: {}", appId, e.getMessage(), e);
            // 加载失败不影响系统运行，只是没有历史上下文
            return 0;
        }
    }

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
        Long appId = NumberUtils.parseRequiredLong(chatHistoryQueryRequest.getAppId());
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 ID 不能为空");
        App app = appMapper.selectOneById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        checkChatHistoryViewAuth(app, loginUser);
        int pageNum = chatHistoryQueryRequest.getPageNum();
        ThrowUtils.throwIf(chatHistoryQueryRequest.getPageSize() > ChatHistoryConstant.MAX_PAGE_SIZE,
                ErrorCode.PARAMS_ERROR, "每页最多查询 50 条对话");
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
        // pageNum 的值应为1
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

    /**
     * dto -> vo
     *
     * @param chatHistoryList 消息记录
     * @param includeAppInfo 是否包含应用信息
     * @return voList
     */
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

    /**
     * 获取查询包装类
     *
     * @param chatHistoryQueryRequest 消息查询对象
     * @return 查询的对象
     */
    @Override
    public QueryWrapper getQueryWrapper(ChatHistoryQueryRequest chatHistoryQueryRequest) {
        QueryWrapper queryWrapper = QueryWrapper.create();
        if (chatHistoryQueryRequest == null) {
            return queryWrapper;
        }
        Long id = NumberUtils.parseNullableLong(chatHistoryQueryRequest.getId());
        String message = chatHistoryQueryRequest.getMessage();
        String messageType = chatHistoryQueryRequest.getMessageType();
        Long appId = NumberUtils.parseNullableLong(chatHistoryQueryRequest.getAppId());
        Long userId = NumberUtils.parseNullableLong(chatHistoryQueryRequest.getUserId());
        LocalDateTime lastCreateTime = chatHistoryQueryRequest.getLastCreateTime();
        String sortField = chatHistoryQueryRequest.getSortField();
        String sortOrder = chatHistoryQueryRequest.getSortOrder();
        if (StrUtil.isNotBlank(messageType)) {
            ThrowUtils.throwIf(MessageTypeEnum.getEnumByValue(messageType) == null,
                    ErrorCode.PARAMS_ERROR, "消息类型无效");
        }
        // 拼接查询条件
        queryWrapper.eq(ChatHistory::getId, id)
                .like(ChatHistory::getMessage, message)
                .eq(ChatHistory::getMessageType, messageType)
                .eq(ChatHistory::getAppId, appId)
                .eq(ChatHistory::getUserId, userId);

        // 游标查询逻辑 - 只使用 create_time 作为游标
        if (lastCreateTime != null) {
            queryWrapper.lt(ChatHistory::getCreateTime, lastCreateTime);
        }
        // 排序
        if (StrUtil.isNotBlank(sortField)) {
            queryWrapper.orderBy(sortField, "ascend".equals(sortOrder));
        } else {
            // 默认按创建时间降序排列
            queryWrapper.orderBy(ChatHistory::getCreateTime, false);
        }
        return queryWrapper;
    }

    /**
     * 添加对话消息
     *
     * @param appId 应用id
     * @param message 消息内容
     * @param messageType 消息类型
     * @param loginUser 登录用户
     * @param parentId 父消息id，如果当前消息是AI消息或错误消息时其parentId为用户的消息ID
     * @return 消息的ID
     */
    private long saveMessage(Long appId, String message, String messageType, User loginUser, Long parentId) {
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用ID不能为空");
        ThrowUtils.throwIf(StrUtil.isBlank(message), ErrorCode.PARAMS_ERROR, "消息内容不能为空");
        ThrowUtils.throwIf(StrUtil.isBlank(messageType), ErrorCode.PARAMS_ERROR, "消息类型不能为空");
        ThrowUtils.throwIf(loginUser == null || loginUser.getId() == null || loginUser.getId() <= 0, ErrorCode.PARAMS_ERROR, "用户ID不能为空");
        // 兜底：即使某列仍是 TEXT(64KB)，也保证不因超长导致整条记录写库失败
        String safeMessage = clampToUtf8Bytes(message, ChatHistoryConstant.MAX_MESSAGE_SAFE_BYTES);
        ChatHistory chatHistory = ChatHistory.builder()
                .message(safeMessage)
                .messageType(messageType)
                .appId(appId)
                .userId(loginUser.getId())
                .parentId(parentId)
                .build();
        boolean result = save(chatHistory);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return chatHistory.getId();
    }

    /**
     * 将消息截断到指定 UTF-8 字节数以内（按字符边界截断，不切断多字节字符）。
     * <p>
     * Vue 流式生成会把整轮深度思考 + 工具标签合成一条 ai 消息，可能很大；
     * 若数据库字段容量不足（如 MySQL TEXT 仅 64KB）会抛 Data truncation。
     * 此处做防御性截断：优先建议把字段升级为 MEDIUMTEXT（见 sql/upgrade_t_chat_history_message_to_mediumtext.sql），
     * 截断只是避免极端情况下整轮对话记录丢失的最后保障。
     */
    private static String clampToUtf8Bytes(String message, int maxBytes) {
        if (message == null || maxBytes <= 0) {
            return message;
        }
        if (message.getBytes(StandardCharsets.UTF_8).length <= maxBytes) {
            return message;
        }
        // 二分查找：在不超过 maxBytes 的前提下取尽量长的前缀（不含截断提示，避免提示本身占掉预算）
        int low = 0;
        int high = message.length();
        while (low < high) {
            int mid = (low + high + 1) >>> 1;
            if (message.substring(0, mid).getBytes(StandardCharsets.UTF_8).length <= maxBytes) {
                low = mid;
            } else {
                high = mid - 1;
            }
        }
        String truncated = message.substring(0, low) + "\n…(内容过长，已截断保存)";
        log.warn("对话消息超过 {} 字节，已截断保存，原始长度：{} 字符", maxBytes, message.length());
        return truncated;
    }

    /**
     * 校验用户是否有权限查看对话历史
     *
     * @param app 应用
     * @param loginUser 用户
     */
    private void checkChatHistoryViewAuth(App app, User loginUser) {
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR);
        ThrowUtils.throwIf(!AppAccessUtils.canViewAppContent(app, loginUser),
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
