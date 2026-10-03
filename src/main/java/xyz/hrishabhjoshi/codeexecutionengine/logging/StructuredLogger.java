package xyz.hrishabhjoshi.codeexecutionengine.logging;

import net.logstash.logback.argument.StructuredArguments;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class StructuredLogger {

    private static final int MAX_STRING_LENGTH = 500;
    private final Logger slf4jLogger;
    private final String component;

    public StructuredLogger(Class<?> clazz, String serviceName) {
        this.slf4jLogger = LoggerFactory.getLogger(clazz);
        this.component = clazz.getSimpleName();
    }

    public void info(String message, Object... fields) {
        logAtLevel("INFO", message, fields);
    }

    public void warn(String message, Object... fields) {
        logAtLevel("WARN", message, fields);
    }

    public void error(String message, Throwable throwable, Object... fields) {
        Map<String, Object> logEntry = buildLogEntry("ERROR", fields);
        if (throwable != null) {
            logEntry.put(LoggingConstants.EXCEPTION_CLASS, throwable.getClass().getName());
            logEntry.put(LoggingConstants.ERROR_MESSAGE,
                    sanitizeValue(LoggingConstants.ERROR_MESSAGE, throwable.getMessage()));
        }
        slf4jLogger.error(message, StructuredArguments.entries(logEntry), throwable);
    }

    public void debug(String message, Object... fields) {
        if (slf4jLogger.isDebugEnabled()) {
            logAtLevel("DEBUG", message, fields);
        }
    }

    public void logRequest(String requestId, String method, String path, String userAgent, String remoteIp,
            Object... additionalFields) {
        Map<String, Object> logEntry = buildLogEntry("INFO", additionalFields);
        logEntry.put(LoggingConstants.REQUEST_ID, requestId);
        logEntry.put(LoggingConstants.EVENT_TYPE, LoggingConstants.EventType.REQUEST);
        logEntry.put(LoggingConstants.TYPE, "REQUEST");
        logEntry.put(LoggingConstants.HTTP_METHOD, method);
        logEntry.put(LoggingConstants.HTTP_PATH, path);
        logEntry.put(LoggingConstants.USER_AGENT, userAgent);
        logEntry.put(LoggingConstants.REMOTE_IP, remoteIp);
        slf4jLogger.info("Incoming HTTP request", StructuredArguments.entries(logEntry));
    }

    public void logResponse(String requestId, int statusCode, long durationMs, Object... additionalFields) {
        Map<String, Object> logEntry = buildLogEntry("INFO", additionalFields);
        logEntry.put(LoggingConstants.REQUEST_ID, requestId);
        logEntry.put(LoggingConstants.EVENT_TYPE, LoggingConstants.EventType.RESPONSE);
        logEntry.put(LoggingConstants.HTTP_STATUS, statusCode);
        logEntry.put(LoggingConstants.TYPE, LoggingConstants.getHttpStatusType(statusCode));
        logEntry.put(LoggingConstants.DURATION_MS, durationMs);
        slf4jLogger.info("HTTP response", StructuredArguments.entries(logEntry));
    }

    public static String generateRequestId() {
        return UUID.randomUUID().toString();
    }

    private void logAtLevel(String level, String message, Object... fields) {
        Map<String, Object> logEntry = buildLogEntry(level, fields);
        switch (level) {
            case "ERROR" -> slf4jLogger.error(message, StructuredArguments.entries(logEntry));
            case "WARN" -> slf4jLogger.warn(message, StructuredArguments.entries(logEntry));
            case "DEBUG" -> slf4jLogger.debug(message, StructuredArguments.entries(logEntry));
            default -> slf4jLogger.info(message, StructuredArguments.entries(logEntry));
        }
    }

    private Map<String, Object> buildLogEntry(String level, Object... fields) {
        Map<String, Object> logEntry = new LinkedHashMap<>();
        logEntry.put(LoggingConstants.COMPONENT, component);
        logEntry.put(LoggingConstants.TYPE, level);
        logEntry.put(LoggingConstants.EVENT_TYPE, LoggingConstants.EventType.LIFECYCLE);

        String requestId = RequestContext.getRequestId();
        if (requestId != null) {
            logEntry.put(LoggingConstants.REQUEST_ID, requestId);
        }

        if (fields != null && fields.length > 0) {
            if (fields.length % 2 != 0) {
                throw new IllegalArgumentException("Fields must be passed as key-value pairs");
            }
            for (int i = 0; i < fields.length; i += 2) {
                String key = String.valueOf(fields[i]);
                logEntry.put(key, sanitizeValue(key, fields[i + 1]));
            }
        }
        return logEntry;
    }

    private Object sanitizeValue(String key, Object value) {
        if (value == null) {
            return null;
        }
        String normalizedKey = key == null ? "" : key.toLowerCase();
        if (isSensitiveKey(normalizedKey)) {
            return "[REDACTED]";
        }
        if (value instanceof String stringValue && stringValue.length() > MAX_STRING_LENGTH) {
            return stringValue.substring(0, MAX_STRING_LENGTH) + "...[truncated]";
        }
        return value;
    }

    private boolean isSensitiveKey(String key) {
        return key.contains("authorization")
                || key.contains("password")
                || key.equals("token")
                || key.endsWith("_token")
                || key.contains("jwt")
                || key.contains("cookie")
                || key.equals("code")
                || key.equals("body")
                || key.equals("payload")
                || key.equals("testcase")
                || key.equals("testcases")
                || key.equals("input")
                || key.equals("output");
    }
}
