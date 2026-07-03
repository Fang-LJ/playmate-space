package com.playmate.space.common;

public record ApiResponse<T>(
        String code,
        String message,
        T data,
        String traceId
) {
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>("SUCCESS", "success", data, TraceIdHolder.getTraceId());
    }

    public static <T> ApiResponse<T> fail(String code, String message) {
        return new ApiResponse<>(code, message, null, TraceIdHolder.getTraceId());
    }
}
