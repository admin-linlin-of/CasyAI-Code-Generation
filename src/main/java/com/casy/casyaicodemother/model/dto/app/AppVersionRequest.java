package com.casy.casyaicodemother.model.dto.app;

import com.casy.casyaicodemother.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.io.Serializable;

@EqualsAndHashCode(callSuper = true)
@Data
public class AppVersionRequest extends PageRequest implements Serializable {

    private String id;

    /**
     * 应用id
     */
    private String appId;

    /**
     * 关联的AI对话消息id
     */
    private String chatHistoryId;

    /**
     * 版本号，从1递增
     */
    private Integer versionNum;

    /**
     * 代码目录，如 v1、v2
     */
    private String codeDir;

    /**
     * 生成该版本使用的AI模型
     */
    private String modelType;

    /**
     * 创建用户id
     */
    private Long userId;

    @Serial
    private static final long serialVersionUID = 1L;
}
