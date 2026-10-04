package xyz.hrishabhjoshi.codeexecutionengine.complexityprofile.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import xyz.hrishabhjoshi.codeexecutionengine.config.ComplexityProfileExecutionProperties;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Distributed concurrency cap using Redis SET NX per slot (worker/trusted Redis only).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ComplexityProfileJobSlotRegistry {

    /**
     * Compare-and-delete on worker/trusted Redis only (sandbox ACL user must not run scripts).
     */
    private static final RedisScript<Long> RELEASE_IF_OWNER_SCRIPT = new DefaultRedisScript<>(
            """
            if redis.call('get', KEYS[1]) == ARGV[1] then
              return redis.call('del', KEYS[1])
            else
              return 0
            end
            """,
            Long.class);

    private final StringRedisTemplate stringRedisTemplate;
    private final ComplexityProfileExecutionProperties properties;

    public Optional<Integer> tryAcquire(String executionId) {
        int max = properties.getSandbox().getMaxConcurrentJobs();
        if (max <= 0) {
            return Optional.of(-1);
        }
        Duration ttl = slotTtl();
        for (int slot = 0; slot < max; slot++) {
            String key = slotKey(slot);
            Boolean acquired = stringRedisTemplate.opsForValue().setIfAbsent(key, executionId, ttl);
            if (Boolean.TRUE.equals(acquired)) {
                log.debug("[PROFILE-SLOT] acquired slot={} executionId={}", slot, executionId);
                return Optional.of(slot);
            }
        }
        log.info("[PROFILE-SLOT] no slot available executionId={} max={}", executionId, max);
        return Optional.empty();
    }

    public void release(String executionId, Optional<Integer> slot) {
        if (slot.isEmpty() || slot.get() < 0) {
            return;
        }
        int slotIndex = slot.get();
        String key = slotKey(slotIndex);
        if (releaseIfOwner(key, executionId)) {
            log.debug("[PROFILE-SLOT] released slot={} executionId={}", slotIndex, executionId);
        }
    }

    public void releaseByExecutionId(String executionId) {
        int max = properties.getSandbox().getMaxConcurrentJobs();
        for (int slot = 0; slot < max; slot++) {
            if (releaseIfOwner(slotKey(slot), executionId)) {
                return;
            }
        }
    }

    private boolean releaseIfOwner(String key, String executionId) {
        Long deleted = stringRedisTemplate.execute(RELEASE_IF_OWNER_SCRIPT, List.of(key), executionId);
        return deleted != null && deleted > 0L;
    }

    private String slotKey(int slot) {
        return properties.getSandbox().getSlotKeyPrefix() + slot;
    }

    private Duration slotTtl() {
        long seconds = properties.getSandbox().getJobCompletionTimeoutSeconds()
                + properties.getSandbox().getJobActiveDeadlineSeconds()
                + properties.getSandbox().getSlotTtlGraceSeconds();
        return Duration.ofSeconds(Math.max(seconds, 60));
    }
}
