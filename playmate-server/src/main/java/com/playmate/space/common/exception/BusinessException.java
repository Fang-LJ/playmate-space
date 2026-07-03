package com.playmate.space.common.exception;

import com.playmate.space.common.ErrorCode;

public class BusinessException extends RuntimeException {

    private final String code;

    public BusinessException(String message) {
        this(ErrorCode.BUSINESS_ERROR.code(), message);
    }

    public BusinessException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
