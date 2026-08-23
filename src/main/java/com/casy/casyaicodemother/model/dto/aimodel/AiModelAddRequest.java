package com.casy.casyaicodemother.model.dto.aimodel;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
public class AiModelAddRequest implements Serializable {

    private String modelCode;
    private String modelName;
    private Integer enabled;
    private Integer isDefault;
    private Integer sortOrder;
    private String description;
    private String routingRule;

    @Serial
    private static final long serialVersionUID = 1L;
}
