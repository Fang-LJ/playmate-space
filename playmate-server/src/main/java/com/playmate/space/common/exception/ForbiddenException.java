package com.playmate.space.common.exception;

import com.playmate.space.common.ErrorCode;

public class ForbiddenException extends BusinessException {

    public ForbiddenException() {
        super(ErrorCode.FORBIDDEN.code(), ErrorCode.FORBIDDEN.message());
    }

    public ForbiddenException(String message) {
        super(ErrorCode.FORBIDDEN.code(), message);
    }
}
