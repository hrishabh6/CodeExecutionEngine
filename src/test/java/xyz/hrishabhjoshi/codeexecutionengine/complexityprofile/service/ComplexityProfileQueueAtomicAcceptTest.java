package xyz.hrishabhjoshi.codeexecutionengine.complexityprofile.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import xyz.hrishabhjoshi.codeexecutionengine.complexityprofile.dto.ComplexityProfileJobPayload;
import xyz.hrishabhjoshi.codeexecutionengine.config.ComplexityProfileExecutionProperties;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ComplexityProfileQueueAtomicAcceptTest {

    private final Map<String, String> stringStore = new ConcurrentHashMap<>();
    private final List<String> queue = new ArrayList<>();
    private final Object lock = new Object();
    private ComplexityProfileQueueService queueService;

    @BeforeEach
    void setUp() {
        stringStore.clear();
        synchronized (lock) {
            queue.clear();
        }
        ComplexityProfileExecutionProperties properties = new ComplexityProfileExecutionProperties();
        RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);
        @SuppressWarnings("unchecked")
        ListOperations<String, Object> listOps = mock(ListOperations.class);
        lenient().when(redisTemplate.opsForList()).thenReturn(listOps);
        lenient().when(listOps.size(anyString())).thenReturn(0L);

        StringRedisTemplate stringRedisTemplate = mock(StringRedisTemplate.class);
        when(stringRedisTemplate.execute(any(RedisScript.class), anyList(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(inv -> {
            @SuppressWarnings("unchecked")
            List<String> keys = inv.getArgument(1);
            String statusJson = inv.getArgument(2);
            String jobJson = inv.getArgument(3);
            String executionId = inv.getArgument(4);
            String acceptKey = keys.get(0);
            String statusKey = keys.get(1);
            String jobKey = keys.get(2);
            String resultKey = keys.get(3);
            String queueName = keys.get(4);
            synchronized (lock) {
                if (stringStore.containsKey(acceptKey)
                        || stringStore.containsKey(statusKey)
                        || stringStore.containsKey(jobKey)
                        || stringStore.containsKey(resultKey)) {
                    return 0L;
                }
                stringStore.put(acceptKey, "1");
                stringStore.put(statusKey, statusJson);
                stringStore.put(jobKey, jobJson);
                queue.add(executionId);
                return 1L;
            }
        });
        when(stringRedisTemplate.hasKey(anyString())).thenAnswer(inv -> stringStore.containsKey(inv.getArgument(0)));

        queueService = new ComplexityProfileQueueService(
                redisTemplate, stringRedisTemplate, new ObjectMapper(), properties);
    }

    @Test
    void concurrentSubmitSameExecutionIdEnqueuesOnce() throws Exception {
        ComplexityProfileJobPayload payload = ComplexityProfileJobPayload.builder()
                .executionId("cpa-analysis-1")
                .submissionId("sub")
                .questionId(1L)
                .language("java")
                .sourceCode("class S {}")
                .build();
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger acceptedNew = new AtomicInteger();
        AtomicInteger alreadyAccepted = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    ComplexityProfileQueueService.EnqueueOutcome outcome = queueService.tryEnqueue(payload);
                    if (outcome == ComplexityProfileQueueService.EnqueueOutcome.ACCEPTED_NEW) {
                        acceptedNew.incrementAndGet();
                    } else if (outcome == ComplexityProfileQueueService.EnqueueOutcome.ALREADY_ACCEPTED) {
                        alreadyAccepted.incrementAndGet();
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        done.await();
        pool.shutdown();

        assertEquals(1, acceptedNew.get());
        assertEquals(threads - 1, alreadyAccepted.get());
        synchronized (lock) {
            assertEquals(1, queue.size());
        }
    }
}
