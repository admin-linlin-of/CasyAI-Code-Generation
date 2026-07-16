package com.casy.casyaicodemother.model.dto.app;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
public class AppVersionRetryBuildRequest implements Serializable {

    private String appId;

    private String codeDir;

    @Serial
    private static final long serialVersionUID = 1L;
}
