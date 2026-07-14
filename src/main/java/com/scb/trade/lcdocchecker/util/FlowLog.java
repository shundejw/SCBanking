package com.scb.trade.lcdocchecker.util;

import org.slf4j.Logger;

/**
 * Unified flow logging for interview demos and production troubleshooting.
 * Every log line starts with {@code className} and {@code methodName}, followed
 * by
 * optional {@code key=value} fields in a fixed order.
 */
public final class FlowLog {

    private static final int DEFAULT_MAX_VALUE_LENGTH = 500;

    private FlowLog() {
    }

    /**
     * @param fields alternating key/value pairs, e.g.
     *               {@code "stage", "START", "runId", id}
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

    public static String prettyValue(Object value) {
        return prettyValue(value, DEFAULT_MAX_VALUE_LENGTH);
    }

    public static String prettyValue(Object value, int maxLength) {
        if (value == null) {
            return "null";
        }
        String text = String.valueOf(value).replace("\r\n", "\n").replace('\r', '\n');
        if (text.length() > maxLength) {
            text = text.substring(0, maxLength) + "... (len=" + text.length() + ")";
        }
        if (text.contains("\n")) {
            return "\n" + indent(text, "    ");
        }
        return text;
    }

    private static String indent(String text, String prefix) {
        StringBuilder sb = new StringBuilder(text.length() + 16);
        String[] lines = text.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) {
                sb.append('\n');
            }
            sb.append(prefix).append(lines[i]);
        }
        return sb.toString();
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
