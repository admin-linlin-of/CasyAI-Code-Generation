package com.casy.casyaicodemother.model.dto.app;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

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
     * 应用类型数组
     */
    private List<String> appTypes;

    /**
     * 模型类型
     */
    private String modelType;

    @Serial
    private static final long serialVersionUID = 1L;
}
