package com.casy.casyaicodemother.model.dto.app;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

@Data
public class AppUpdateRequest implements Serializable {

    /**
     * id
     */
    private Long id;

    /**
     * 应用名称
     */
    private String appName;

    /**
     * 应用类型数组
     */
    private List<String> appTypes;

    /**
     * 是否公布 0-不公布 1-公布
     */
    private Integer isPublish;

    @Serial
    private static final long serialVersionUID = 1L;
}
