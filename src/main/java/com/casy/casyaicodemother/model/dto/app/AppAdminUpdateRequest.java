package com.casy.casyaicodemother.model.dto.app;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 管理员更新应用
 */
@Data
public class AppAdminUpdateRequest implements Serializable {

    private Long id;

    /**
     * 应用名称
     */
    private String appName;

    private String cover;

    private Integer priority;

    @Serial
    private static final long serialVersionUID = 1L;
}
