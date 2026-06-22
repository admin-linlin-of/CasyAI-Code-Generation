package com.casy.casyaicodemother.model.enums;

import cn.hutool.core.util.ObjUtil;
import lombok.Getter;

@Getter
public enum VersionBuildStatusEnum {

    PENDING("pending"),
    BUILDING("building"),
    SUCCESS("success"),
    FAILED("failed");

    private final String value;

    VersionBuildStatusEnum(String value) {
        this.value = value;
    }

    public static VersionBuildStatusEnum getEnumByValue(String value) {
        if (ObjUtil.isEmpty(value)) {
            return null;
        }
        for (VersionBuildStatusEnum anEnum : VersionBuildStatusEnum.values()) {
            if (anEnum.value.equals(value)) {
                return anEnum;
            }
        }
        return null;
    }
}
