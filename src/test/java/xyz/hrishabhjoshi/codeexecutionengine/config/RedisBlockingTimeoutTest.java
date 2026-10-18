package xyz.hrishabhjoshi.codeexecutionengine.config;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RedisBlockingTimeoutTest {

    @Test
    void commandTimeoutOutlastsFiveSecondBrpop() {
        Duration resolved = RedisConfig.blockingCommandTimeout(Duration.ofMillis(5000), 5);
        assertEquals(Duration.ofSeconds(7), resolved);
    }

    @Test
    void longerConfiguredTimeoutIsKept() {
        Duration resolved = RedisConfig.blockingCommandTimeout(Duration.ofSeconds(30), 5);
        assertEquals(Duration.ofSeconds(30), resolved);
    }
}
