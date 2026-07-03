package com.playmate.space.common;

import org.slf4j.MDC;

public final class TraceIdHolder {

    public static final String TRACE_ID = "traceId";

    private TraceIdHolder() {
    }

    public static String getTraceId() {
        return MDC.get(TRACE_ID);
    }
}
