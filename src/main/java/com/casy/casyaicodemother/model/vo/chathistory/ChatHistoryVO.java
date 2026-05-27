package com.casy.casyaicodemother.model.vo.chathistory;

import com.casy.casyaicodemother.model.vo.app.AppVO;
import com.casy.casyaicodemother.model.vo.user.UserVO;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class ChatHistoryVO implements Serializable {

    private Long id;

    private String message;

    private String messageType;

    private Long appId;

    private Long userId;

    private Long parentId;

    private LocalDateTime createTime;

    private AppVO app;

    private UserVO user;

    private static final long serialVersionUID = 1L;
}
