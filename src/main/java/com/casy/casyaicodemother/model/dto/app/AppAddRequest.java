package com.casy.casyaicodemother.model.dto.app;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 用户创建应‍用
 */
@Data
public class AppAddRequest implements Serializable {

    private String appName;

    /**
     * 应用初始化的 prompt
     */
    private String initPrompt;

    private String codeGenType;

    /**
     * 模型类型
     */
    private String modelType;

    @Serial
    private static final long serialVersionUID = 1L;
}
