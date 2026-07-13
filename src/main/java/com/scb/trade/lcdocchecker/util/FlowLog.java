package com.scb.trade.lcdocchecker.util;

import org.slf4j.Logger;

/**
 * Unified flow logging for interview demos and production troubleshooting.
 * Every log line starts with {@code className} and {@code methodName}, followed by
 * optional {@code key=value} fields in a fixed order.
 */
public final class FlowLog {

    private FlowLog() {
    }

    /**
     * @param fields alternating key/value pairs, e.g. {@code "stage", "START", "runId", id}
     */
    public static void info(Logger log, Class<?> clazz, String methodName, Object... fields) {
        log.info(format(clazz, methodName, fields));
    }

    /**
     * @param fields alternating key/value pairs
     */
    public static void warn(Logger log, Class<?> clazz, String methodName, Object... fields) {
        log.warn(format(clazz, methodName, fields));
    }

    /**
     * @param fields alternating key/value pairs
     */
    public static void error(Logger log, Class<?> clazz, String methodName, Throwable cause, Object... fields) {
        if (cause != null) {
            log.error(format(clazz, methodName, fields), cause);
        } else {
            log.error(format(clazz, methodName, fields));
        }
    }

    private static String format(Class<?> clazz, String methodName, Object... fields) {
        StringBuilder sb = new StringBuilder(128);
        sb.append("className=").append(clazz.getSimpleName());
        sb.append(", methodName=").append(methodName);
        for (int i = 0; i + 1 < fields.length; i += 2) {
            sb.append(", ").append(fields[i]).append('=').append(fields[i + 1]);
        }
        return sb.toString();
    }
}
