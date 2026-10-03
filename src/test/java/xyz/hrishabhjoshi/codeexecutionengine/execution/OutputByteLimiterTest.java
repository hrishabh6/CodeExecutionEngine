package xyz.hrishabhjoshi.codeexecutionengine.execution;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OutputByteLimiterTest {

    @Test
    void truncatesWithoutSplittingUtf8CodePoint() {
        String value = "a".repeat(10);
        String truncated = OutputByteLimiter.truncateUtf8(value, 5);
        assertTrue(truncated.getBytes().length <= 5);
    }
}
