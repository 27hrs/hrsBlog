package com.example.hrsblog.common;

/**
 * 可预期的业务异常：由 {@link GlobalExceptionHandler} 翻译为统一错误响应。
 *
 * <p>只用于「已经能明确归类」的失败（登录失败、slug 冲突、资源不存在等）；
 * 未预期异常交给兜底处理，避免把内部细节暴露给调用方。
 */
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.message());
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
