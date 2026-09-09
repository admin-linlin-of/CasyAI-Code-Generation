package com.casy.casyaicodemother.model.dto.sysparam;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
public class SysParamUpdateRequest implements Serializable {

    private Long id;
    private String paramKey;
    private String paramValue;
    private String paramName;
    private String remark;
    private Integer enabled;
    private Integer isPublic;
    private Integer sortOrder;

    @Serial
    private static final long serialVersionUID = 1L;
}
