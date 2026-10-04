package xyz.hrishabhjoshi.codeexecutionengine.complexityprofile.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import xyz.hrishabhjoshi.codeexecutionengine.config.ComplexityProfileExecutionProperties;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ComplexityProfileJobSlotRegistryTest {

    private final Map<String, String> store = new ConcurrentHashMap<>();
    private ComplexityProfileJobSlotRegistry registry;
    private ComplexityProfileExecutionProperties properties;

    @BeforeEach
    void setUp() {
        store.clear();
        properties = new ComplexityProfileExecutionProperties();
        properties.getSandbox().setMaxConcurrentJobs(2);
        properties.getSandbox().setJobActiveDeadlineSeconds(60);
        properties.getSandbox().setJobCompletionTimeoutSeconds(60);
        properties.getSandbox().setSlotTtlGraceSeconds(30);
        properties.getSandbox().setSlotKeyPrefix("execution:complexity-profile:slot:");

        StringRedisTemplate template = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(template.opsForValue()).thenReturn(ops);
        when(ops.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenAnswer(inv -> {
            String key = inv.getArgument(0);
            String value = inv.getArgument(1);
            return store.putIfAbsent(key, value) == null ? Boolean.TRUE : Boolean.FALSE;
        });
        when(template.execute(any(RedisScript.class), anyList(), any())).thenAnswer(inv -> {
            @SuppressWarnings("unchecked")
            List<String> keys = inv.getArgument(1);
            String executionId = inv.getArgument(2);
            String key = keys.getFirst();
            if (executionId.equals(store.get(key))) {
                store.remove(key);
                return 1L;
            }
            return 0L;
        });

        registry = new ComplexityProfileJobSlotRegistry(template, properties);
    }

    @Test
    void allowsNConcurrentAcquisitionsAndRejectsNPlusOne() {
        assertTrue(registry.tryAcquire("exec-1").isPresent());
        assertTrue(registry.tryAcquire("exec-2").isPresent());
        assertFalse(registry.tryAcquire("exec-3").isPresent());
    }

    @Test
    void releasesOnlyOwnedSlot() {
        store.clear();
        properties.getSandbox().setMaxConcurrentJobs(1);
        StringRedisTemplate template = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(template.opsForValue()).thenReturn(ops);
        when(ops.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenAnswer(inv -> {
            String key = inv.getArgument(0);
            String value = inv.getArgument(1);
            return store.putIfAbsent(key, value) == null ? Boolean.TRUE : Boolean.FALSE;
        });
        when(template.execute(any(RedisScript.class), anyList(), any())).thenAnswer(inv -> {
            @SuppressWarnings("unchecked")
            List<String> keys = inv.getArgument(1);
            String executionId = inv.getArgument(2);
            String key = keys.getFirst();
            if (executionId.equals(store.get(key))) {
                store.remove(key);
                return 1L;
            }
            return 0L;
        });
        ComplexityProfileJobSlotRegistry singleSlotRegistry = new ComplexityProfileJobSlotRegistry(template, properties);

        Optional<Integer> slot = singleSlotRegistry.tryAcquire("exec-a");
        assertTrue(slot.isPresent());
        singleSlotRegistry.release("exec-b", slot);
        assertFalse(singleSlotRegistry.tryAcquire("exec-c").isPresent());
        singleSlotRegistry.release("exec-a", slot);
        assertTrue(singleSlotRegistry.tryAcquire("exec-d").isPresent());
    }

    @Test
    void staleOwnerReleaseAfterReassignmentDoesNotDeleteCurrentOwner() {
        properties.getSandbox().setMaxConcurrentJobs(1);
        StringRedisTemplate template = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(template.opsForValue()).thenReturn(ops);
        when(ops.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenAnswer(inv -> {
            String key = inv.getArgument(0);
            String value = inv.getArgument(1);
            return store.putIfAbsent(key, value) == null ? Boolean.TRUE : Boolean.FALSE;
        });
        when(template.execute(any(RedisScript.class), anyList(), any())).thenAnswer(inv -> {
            @SuppressWarnings("unchecked")
            List<String> keys = inv.getArgument(1);
            String executionId = inv.getArgument(2);
            String key = keys.getFirst();
            if (executionId.equals(store.get(key))) {
                store.remove(key);
                return 1L;
            }
            return 0L;
        });
        ComplexityProfileJobSlotRegistry singleSlotRegistry = new ComplexityProfileJobSlotRegistry(template, properties);

        Optional<Integer> slot = singleSlotRegistry.tryAcquire("exec-a");
        assertTrue(slot.isPresent());
        String slotKey = properties.getSandbox().getSlotKeyPrefix() + slot.get();

        // TTL expired and a new execution acquired the same slot.
        store.put(slotKey, "exec-b");

        singleSlotRegistry.release("exec-a", slot);
        assertEquals("exec-b", store.get(slotKey), "stale owner must not remove reassigned slot");

        singleSlotRegistry.release("exec-b", slot);
        assertFalse(store.containsKey(slotKey), "current owner release must succeed");
    }
}
