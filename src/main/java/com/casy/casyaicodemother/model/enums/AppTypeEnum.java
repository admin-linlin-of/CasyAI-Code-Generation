package com.casy.casyaicodemother.model.enums;

import cn.hutool.core.util.ObjUtil;

public enum AppTypeEnum {
    WEBSITE("网站", "website"),
    TOOL("工具", "tool"),
    BLOG("博客", "blog"),
    ADMIN("管理后台", "admin");

    private final String text;

    private final String value;

    AppTypeEnum(String text, String value) {
        this.text = text;
        this.value = value;
    }

    public String getText() {
        return text;
    }

    public String getValue() {
        return value;
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
        String normalized = value.trim();
        // 历史取值兼容：管理系统 → 管理后台，应用网站 → 网站
        if ("management".equals(normalized)) {
            return ADMIN;
        }
        if ("application".equals(normalized)) {
            return WEBSITE;
        }
        for (AppTypeEnum anEnum : AppTypeEnum.values()) {
            if (anEnum.value.equals(normalized)) {
                return anEnum;
            }
        }
        return null;
    }
}
