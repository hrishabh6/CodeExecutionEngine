package xyz.hrishabhjoshi.codeexecutionengine.complexityprofile;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import xyz.hrishabhjoshi.codeexecutionengine.complexityprofile.dto.ComplexityProfileJobPayload;
import xyz.hrishabhjoshi.codeexecutionengine.complexityprofile.service.ComplexityProfileQueueService;
import xyz.hrishabhjoshi.codeexecutionengine.config.ComplexityProfileExecutionProperties;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class ComplexityProfileRedisLifecycleTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;
    @Mock
    private ValueOperations<String, Object> valueOperations;
    @Mock
    private ListOperations<String, Object> listOperations;

    private ComplexityProfileQueueService queueService;
    private ComplexityProfileExecutionProperties properties;

    @BeforeEach
    void setUp() {
        properties = new ComplexityProfileExecutionProperties();
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(redisTemplate.opsForList()).thenReturn(listOperations);
        lenient().when(listOperations.size(anyString())).thenReturn(0L);
        queueService = new ComplexityProfileQueueService(redisTemplate, new ObjectMapper(), properties);
    }

    @Test
    void statusAndJobPayloadRecordsUseTtl() {
        ComplexityProfileJobPayload payload = ComplexityProfileJobPayload.builder()
                .executionId("exec-ttl")
                .submissionId("sub")
                .questionId(1L)
                .language("JAVA")
                .sourceCode("class Solution {}")
                .build();
        queueService.enqueue(payload);

        ArgumentCaptor<Long> ttlCaptor = ArgumentCaptor.forClass(Long.class);
        verify(valueOperations, atLeastOnce()).set(
                startsWith(properties.getQueue().getStatusPrefix()),
                any(),
                ttlCaptor.capture(),
                eq(TimeUnit.SECONDS));
        assertTrue(ttlCaptor.getAllValues().stream()
                .anyMatch(v -> v.equals(properties.getQueue().getStatusTtlSeconds())));

        verify(valueOperations).set(
                eq(properties.getQueue().getJobPayloadPrefix() + "exec-ttl"),
                eq(payload),
                eq(properties.getQueue().getQueuedJobMaxAgeSeconds()),
                eq(TimeUnit.SECONDS));
    }

    @Test
    void storeResultAppliesResultTtl() {
        queueService.storeResult("exec-1", new xyz.hrishabhjoshi.codeexecutionengine.complexityprofile.dto.ComplexityProfileExecutionDtos.ComplexityProfilePollResponse(
                "exec-1", "COMPLETED", "SUCCESS", "21", "v1", "v1", "rt", "fp", null, null, null));
        verify(valueOperations).set(
                eq(properties.getQueue().getResultPrefix() + "exec-1"),
                any(),
                eq(properties.getQueue().getResultTtlSeconds()),
                eq(TimeUnit.SECONDS));
    }

    @Test
    void defaultTtlsAreBounded() {
        assertTrue(properties.getQueue().getStatusTtlSeconds() > 0);
        assertTrue(properties.getQueue().getResultTtlSeconds() > 0);
        assertTrue(properties.getQueue().getQueuedJobMaxAgeSeconds() > 0);
        assertEquals(3600, properties.getQueue().getStatusTtlSeconds());
    }
}
