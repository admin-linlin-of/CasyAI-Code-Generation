package com.casy.casyaicodemother.util;

import cn.hutool.core.util.StrUtil;
import com.casy.casyaicodemother.exception.BusinessException;
import com.casy.casyaicodemother.exception.ErrorCode;

public final class NumberUtils {

    private NumberUtils() {
    }

    public static Long parseNullableLong(String value) {
        if (StrUtil.isBlank(value)) {
            return null;
        }
        return parseLong(value);
    }

    public static Long parseRequiredLong(String value) {
        if (StrUtil.isBlank(value)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "ID parameter is required");
        }
        return parseLong(value);
    }

    private static Long parseLong(String value) {
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "ID parameter format error");
        }
    }
}
