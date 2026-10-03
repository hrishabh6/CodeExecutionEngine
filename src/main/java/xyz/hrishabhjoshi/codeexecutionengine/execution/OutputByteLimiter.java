package xyz.hrishabhjoshi.codeexecutionengine.execution;

import java.nio.charset.StandardCharsets;

public final class OutputByteLimiter {

    private OutputByteLimiter() {
    }

    public static String truncateUtf8(String value, int maxBytes) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= maxBytes) {
            return value;
        }
        int end = maxBytes;
        while (end > 0 && (bytes[end] & 0xC0) == 0x80) {
            end--;
        }
        return new String(bytes, 0, end, StandardCharsets.UTF_8);
    }
}
