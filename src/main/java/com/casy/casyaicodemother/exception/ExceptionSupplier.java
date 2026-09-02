package com.casy.casyaicodemother.exception;

import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;

import java.util.function.Supplier;

/**
 * exception supplier基类
 */
@ToString
@Slf4j
public abstract class ExceptionSupplier<T extends Exception> implements Supplier<T> {

    /** 错误消息 */
    protected final String msg;
    /** 错误原因 */
    protected final Throwable cause;

    /**
     *
     * @param msg 错误消息
     * @param cause 错误原因
     */
    protected ExceptionSupplier(String msg, @Nullable Throwable cause) {
        this.msg = msg;
        this.cause = cause;
    }

    /**
     * 获取格式化后消息
     * @param format 消息格式
     * @param args 消息参数
     * @return 格式化后消息
     */
    protected static String format(String format, Object... args) {
        Throwable throwable = extractThrowable(args);
        final Object[] msgArgs;
        if (throwable != null) {
            msgArgs = trimmedCopy(args);
        } else {
            msgArgs = args;
        }
        return msgArgs.length != 0 ? String.format(format, msgArgs) : format;
    }


    /**
     * 从Object数组参数中抽取Throwable对象
     * @param argArray 异常参数
     * @return Throwable
     */
    protected static Throwable extractThrowable(Object[] argArray) {
        if (argArray == null || argArray.length == 0) {
            return null;
        }

        final Object lastEntry = argArray[argArray.length - 1];
        if (lastEntry instanceof Throwable) {
            return (Throwable) lastEntry;
        }
        return null;
    }

    /**
     * This method should be called only if the last element is a throwable object.
     *
     * @param argArray 数组参数
     * @return 去除数组最后一个元素
     */
    protected static Object[] trimmedCopy(Object[] argArray) {
        if (argArray == null || argArray.length == 0) {
            throw new IllegalStateException("nonsensical empty or null argument array");
        }
        final int trimmedLen = argArray.length - 1;
        if (trimmedLen == 0) {
            return new Object[0];
        }
        Object[] trimmed = new Object[trimmedLen];
        System.arraycopy(argArray, 0, trimmed, 0, trimmedLen);
        return trimmed;
    }


}
