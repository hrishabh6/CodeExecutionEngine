package xyz.hrishabhjoshi.codeexecutionengine.service.utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility to parse Docker stats memory output and convert between units.
 * Handles formats like "12.45MiB / 256MiB" or "1.2GiB / 2GiB".
 */
public class MemoryParser {

    // Pattern: "12.45MiB / 256MiB" or "1.2GiB / 2GiB"
    private static final Pattern MEMORY_PATTERN = Pattern.compile("([0-9.]+)(\\w+)\\s*/\\s*([0-9.]+)(\\w+)");

    /**
     * Parse Docker stats memory string to bytes.
     * 
     * @param memoryStats String like "12.45MiB / 256MiB"
     * @return Peak memory in bytes, or null if parsing fails
     */
    public static Long parseMemoryToBytes(String memoryStats) {
        if (memoryStats == null || memoryStats.trim().isEmpty()) {
            return null;
        }

        Matcher matcher = MEMORY_PATTERN.matcher(memoryStats.trim());
        if (!matcher.find()) {
            return null;
        }

        String valueStr = matcher.group(1);
        String unit = matcher.group(2);

        try {
            double value = Double.parseDouble(valueStr);
            return convertToBytes(value, unit);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Convert value with unit to bytes.
     * Supports both binary (MiB, GiB) and decimal (MB, GB) units.
     */
    private static long convertToBytes(double value, String unit) {
        return switch (unit.toLowerCase()) {
            case "b" -> (long) value;
            // Binary units (1024-based)
            case "kib" -> (long) (value * 1024);
            case "mib" -> (long) (value * 1024 * 1024);
            case "gib" -> (long) (value * 1024 * 1024 * 1024);
            // Decimal units (1000-based)
            case "kb" -> (long) (value * 1000);
            case "mb" -> (long) (value * 1000 * 1000);
            case "gb" -> (long) (value * 1000 * 1000 * 1000);
            default -> 0L;
        };
    }

    /**
     * Convert bytes to KB for display in API responses.
     * 
     * @param bytes Memory in bytes
     * @return Memory in kilobytes (KB)
     */
    public static int bytesToKB(long bytes) {
        return (int) (bytes / 1024);
    }
}
