package com.casy.casyaicodemother.model.dto.app;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
public class AppDeployRequest implements Serializable {

    /**
     * 应用 id
     */
    private String appId;

    /**
     * 部署的版本目录，如 v1；为空时部署最新版本
     */
    private String codeDir;

    @Serial
    private static final long serialVersionUID = 1L;
}
