package xyz.hrishabhjoshi.codeexecutionengine.complexityprofile;

import org.junit.jupiter.api.Test;
import xyz.hrishabhjoshi.codeexecutionengine.config.ComplexityProfileExecutionProperties;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComplexityProfileQueueIsolationTest {

    @Test
    void profileQueueUsesDedicatedRedisNamespace() {
        ComplexityProfileExecutionProperties properties = new ComplexityProfileExecutionProperties();
        assertTrue(properties.getQueue().getName().contains("complexity-profile"));
        assertNotEquals("execution:queue", properties.getQueue().getName());
        assertTrue(properties.getQueue().getStatusPrefix().contains("complexity-profile"));
        assertTrue(properties.getQueue().getResultPrefix().contains("complexity-profile"));
    }
}
