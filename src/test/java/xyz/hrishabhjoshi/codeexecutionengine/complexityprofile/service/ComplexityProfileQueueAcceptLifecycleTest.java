package xyz.hrishabhjoshi.codeexecutionengine.complexityprofile.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import xyz.hrishabhjoshi.codeexecutionengine.complexityprofile.dto.ComplexityProfileJobPayload;
import xyz.hrishabhjoshi.codeexecutionengine.config.ComplexityProfileExecutionProperties;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ComplexityProfileQueueAcceptLifecycleTest {

    private final Map<String, String> stringStore = new ConcurrentHashMap<>();
    private ComplexityProfileQueueService queueService;

    @BeforeEach
    void setUp() {
        stringStore.clear();
        ComplexityProfileExecutionProperties properties = new ComplexityProfileExecutionProperties();
        RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);
        @SuppressWarnings("unchecked")
        ListOperations<String, Object> listOps = mock(ListOperations.class);
        lenient().when(redisTemplate.opsForList()).thenReturn(listOps);
        lenient().when(redisTemplate.hasKey(any())).thenReturn(false);
        @SuppressWarnings("unchecked")
        ValueOperations<String, Object> valueOps = mock(ValueOperations.class);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
        lenient().when(valueOps.get(any())).thenReturn(null);

        StringRedisTemplate stringRedisTemplate = mock(StringRedisTemplate.class);
        when(stringRedisTemplate.hasKey(any())).thenAnswer(inv -> stringStore.containsKey(inv.getArgument(0)));
        when(stringRedisTemplate.delete(any(String.class))).thenAnswer(inv -> {
            stringStore.remove(inv.getArgument(0));
            return Boolean.TRUE;
        });
        when(stringRedisTemplate.execute(any(RedisScript.class), anyList(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(inv -> {
                    String acceptKey = inv.getArgument(1, java.util.List.class).get(0).toString();
                    if (stringStore.containsKey(acceptKey)) {
                        return 0L;
                    }
                    stringStore.put(acceptKey, "1");
                    return 1L;
                });

        queueService = new ComplexityProfileQueueService(
                redisTemplate, stringRedisTemplate, new ObjectMapper(), properties);
    }

    @Test
    void orphanAcceptMarkerAllowsRetryAfterExecutionArtifactsExpire() {
        String executionId = "cpa-analysis-retry";
        String acceptKey = propertiesAcceptKey(executionId);
        stringStore.put(acceptKey, "1");

        assertFalse(queueService.hasAcceptedExecution(executionId));
        assertEquals(
                ComplexityProfileQueueService.EnqueueOutcome.ACCEPTED_NEW,
                queueService.tryEnqueue(samplePayload(executionId)));
    }

    @Test
    void acceptMarkerTtlMatchesLongestArtifactTtl() {
        ComplexityProfileExecutionProperties properties = new ComplexityProfileExecutionProperties();
        properties.getQueue().setStatusTtlSeconds(100);
        properties.getQueue().setResultTtlSeconds(200);
        properties.getQueue().setQueuedJobMaxAgeSeconds(150);
        RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);
        StringRedisTemplate stringRedisTemplate = mock(StringRedisTemplate.class);
        ComplexityProfileQueueService service = new ComplexityProfileQueueService(
                redisTemplate, stringRedisTemplate, new ObjectMapper(), properties);
        assertEquals(200L, service.acceptMarkerTtlSeconds());
    }

    private static ComplexityProfileJobPayload samplePayload(String executionId) {
        return ComplexityProfileJobPayload.builder()
                .executionId(executionId)
                .submissionId("sub")
                .questionId(1L)
                .language("java")
                .sourceCode("class S {}")
                .build();
    }

    private String propertiesAcceptKey(String executionId) {
        return new ComplexityProfileExecutionProperties().getQueue().getAcceptPrefix() + executionId;
    }
}
