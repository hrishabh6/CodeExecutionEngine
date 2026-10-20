package xyz.hrishabhjoshi.codeexecutionengine.service.v2;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import xyz.hrishabhjoshi.codeexecutionengine.config.ExecutionV2Properties;
import xyz.hrishabhjoshi.codeexecutionengine.dto.SubmissionStatusDto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ExecutionV2StatusStoreTest {

    @Test
    void readsCompatibleStatusFromV2StringKey() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get("execution:v2:status:submission-1"))
                .thenReturn("{\"submissionId\":\"submission-1\",\"executionId\":\"execution-1\",\"status\":\"COMPLETED\"}");

        ExecutionV2StatusStore store = new ExecutionV2StatusStore(
                redis, new ObjectMapper().findAndRegisterModules(), new ExecutionV2Properties());

        SubmissionStatusDto status = store.getStatus("submission-1").orElseThrow();
        assertEquals("execution-1", status.getExecutionId());
        assertEquals("COMPLETED", status.getStatus());
    }

    @Test
    void ignoresMalformedStatusWithoutBreakingLegacyFallback() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get("execution:v2:status:submission-2")).thenReturn("not-json");

        ExecutionV2StatusStore store = new ExecutionV2StatusStore(
                redis, new ObjectMapper().findAndRegisterModules(), new ExecutionV2Properties());

        assertTrue(store.getStatus("submission-2").isEmpty());
    }
}
