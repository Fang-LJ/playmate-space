package com.playmate.space.common.exception;

import com.playmate.space.common.ErrorCode;

public class NotFoundException extends BusinessException {

    public NotFoundException() {
        super(ErrorCode.NOT_FOUND.code(), ErrorCode.NOT_FOUND.message());
    }

    public NotFoundException(String message) {
        super(ErrorCode.NOT_FOUND.code(), message);
    }
}
