package com.casy.casyaicodemother.model.enums;

import cn.hutool.core.util.ObjUtil;

public enum AppTypeEnum {
    WEBSITE("网站", "website"),
    MANAGEMENT("管理系统", "management"),
    APPLICATION("应用网站", "application ");

    private final String text;

    private final String value;

    AppTypeEnum(String text, String value) {
        this.text = text;
        this.value = value;
    }

    /**
     * 根据 value 获取枚举
     *
     * @param value 枚举值的value
     * @return 枚举值
     */
    public static AppTypeEnum getEnumByValue(String value) {
        if (ObjUtil.isEmpty(value)) {
            return null;
        }
        for (AppTypeEnum anEnum : AppTypeEnum.values()) {
            if (anEnum.value.equals(value)) {
                return anEnum;
            }
        }
        return null;
    }
}
