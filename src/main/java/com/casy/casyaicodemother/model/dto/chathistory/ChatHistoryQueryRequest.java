package com.casy.casyaicodemother.model.dto.chathistory;

import com.casy.casyaicodemother.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.io.Serializable;

@EqualsAndHashCode(callSuper = true)
@Data
public class ChatHistoryQueryRequest extends PageRequest implements Serializable {

    private Long id;

    private Long appId;

    private Long userId;

    private String messageType;

    @Serial
    private static final long serialVersionUID = 1L;
}
