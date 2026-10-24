package xyz.hrishabhjoshi.codeexecutionengine.service.v2;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import xyz.hrishabhjoshi.codeexecutionengine.config.ExecutionV2Properties;

import java.time.Duration;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExecutionV2ReaperTest {

    @Test
    void removesProcessingEntryWhenBothClaimAndTaskAreGone() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        @SuppressWarnings("unchecked")
        ListOperations<String, String> lists = mock(ListOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(redis.opsForList()).thenReturn(lists);
        when(values.setIfAbsent(eq("execution:v2:reaper-lock"), eq("1"), any(Duration.class)))
                .thenReturn(true);
        when(lists.range("execution:v2:processing", 0, -1)).thenReturn(List.of("execution-1"));
        when(values.get("execution:v2:claim:execution-1")).thenReturn(null);
        when(redis.hasKey("execution:v2:task:execution-1")).thenReturn(false);

        new ExecutionV2Reaper(redis, new ExecutionV2Properties()).reconcileExpiredClaims();

        verify(lists).remove("execution:v2:processing", 1, "execution-1");
        verify(lists, never()).leftPush("execution:v2:ready", "execution-1");
        verify(redis).delete("execution:v2:reaper-lock");
    }
}
