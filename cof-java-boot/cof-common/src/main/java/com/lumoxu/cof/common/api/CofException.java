package com.lumoxu.cof.common.api;

public class CofException extends RuntimeException {

    private final ErrorCode errorCode;

    public CofException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public CofException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
