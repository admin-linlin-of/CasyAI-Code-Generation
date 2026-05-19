package com.casy.casyaicodemother.exception;


/**
 * 非法状态异常Supplier
 */
public class IllegalStateExceptionSupplier extends ExceptionSupplier<IllegalStateException> {


    /**
     * @param msg 错误消息
     */
    private IllegalStateExceptionSupplier(String msg) {
        this(msg, null);
    }

    /**
     * @param msg 错误消息
     * @param cause 错误原因
     */
    public IllegalStateExceptionSupplier(String msg, Throwable cause) {
        super(msg, cause);
    }

    /**
     * 构造Supplier
     * @param msg 异常消息
     * @return Supplier
     */
    public static IllegalStateExceptionSupplier of(String msg) {
        return new IllegalStateExceptionSupplier(msg);
    }

    /**
     * 构造Supplier
     * @param format 格式
     * @param args 格式参数
     * @return Supplier
     */
    public static IllegalStateExceptionSupplier of(String format, Object... args) {
        return new IllegalStateExceptionSupplier(format(format, args),  extractThrowable(args));
    }

    @Override
    public IllegalStateException get() {
        if (cause == null) {
            return new IllegalStateException(msg);
        }
        return new IllegalStateException(msg, cause);
    }
}
