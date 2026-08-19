package com.casy.casyaicodemother.model.dto.aimodel;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/** 管理员启用/停用模型 */
@Data
public class AiModelEnabledUpdateRequest implements Serializable {

    /** t_ai_model.id */
    private Long id;

    /** 1 启用，0 停用 */
    private Integer enabled;

    @Serial
    private static final long serialVersionUID = 1L;
}
