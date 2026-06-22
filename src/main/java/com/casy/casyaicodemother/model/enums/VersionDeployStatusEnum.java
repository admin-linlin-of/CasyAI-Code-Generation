package com.casy.casyaicodemother.model.enums;

import cn.hutool.core.util.ObjUtil;
import lombok.Getter;

@Getter
public enum VersionDeployStatusEnum {

    NOT_DEPLOYED("not_deployed"),
    DEPLOYING("deploying"),
    SUCCESS("success"),
    FAILED("failed");

    private final String value;

    VersionDeployStatusEnum(String value) {
        this.value = value;
    }

    public static VersionDeployStatusEnum getEnumByValue(String value) {
        if (ObjUtil.isEmpty(value)) {
            return null;
        }
        for (VersionDeployStatusEnum anEnum : VersionDeployStatusEnum.values()) {
            if (anEnum.value.equals(value)) {
                return anEnum;
            }
        }
        return null;
    }
}
