package com.playmate.space.common.exception;

import com.playmate.space.common.ErrorCode;

public class UnauthorizedException extends BusinessException {

    public UnauthorizedException() {
        super(ErrorCode.UNAUTHORIZED.code(), ErrorCode.UNAUTHORIZED.message());
    }

    public UnauthorizedException(String message) {
        super(ErrorCode.UNAUTHORIZED.code(), message);
    }
}
