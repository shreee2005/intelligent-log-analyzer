package com.loganalyzer.ingestion.tracing;

import java.util.regex.Pattern;

public final class TraceContext {

    private static final Pattern TRACE_ID = Pattern.compile("[0-9a-f]{32}");
    private static final Pattern SPAN_ID = Pattern.compile("[0-9a-f]{16}");

    private TraceContext() {
    }

    public static Context parse(String traceparent) {
        if (traceparent == null || traceparent.isBlank()) {
            return new Context(null, null);
        }

        String[] parts = traceparent.trim().split("-");
        if (parts.length != 4
                || !"00".equals(parts[0])
                || !TRACE_ID.matcher(parts[1]).matches()
                || !SPAN_ID.matcher(parts[2]).matches()
                || "0000000000000000".equals(parts[2])
                || !parts[3].matches("[0-9a-f]{2}")) {
            return new Context(null, null);
        }

        return new Context(parts[1], parts[2]);
    }

    public record Context(String traceId, String parentSpanId) {
    }
}
