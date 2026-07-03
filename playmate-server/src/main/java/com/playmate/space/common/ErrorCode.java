package com.playmate.space.common;

public enum ErrorCode {
    PARAM_ERROR("PARAM_ERROR", "参数错误"),
    UNAUTHORIZED("UNAUTHORIZED", "未登录或登录已失效"),
    FORBIDDEN("FORBIDDEN", "无权限操作"),
    NOT_FOUND("NOT_FOUND", "资源不存在"),
    BUSINESS_ERROR("BUSINESS_ERROR", "业务处理失败"),
    INTERNAL_ERROR("INTERNAL_ERROR", "系统异常");

    private final String code;
    private final String message;

    ErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    public String code() {
        return code;
    }

    public String message() {
        return message;
    }
}
