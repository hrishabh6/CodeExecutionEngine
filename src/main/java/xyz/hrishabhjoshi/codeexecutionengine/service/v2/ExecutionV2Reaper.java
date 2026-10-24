package xyz.hrishabhjoshi.codeexecutionengine.service.v2;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import xyz.hrishabhjoshi.codeexecutionengine.config.ExecutionV2Properties;
import xyz.hrishabhjoshi.codeexecutionengine.core.v2.ExecutionV2Keys;

import java.time.Duration;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "execution.v2", name = "reaper-enabled", havingValue = "true")
public class ExecutionV2Reaper {

    private final StringRedisTemplate stringRedisTemplate;
    private final ExecutionV2Properties v2Properties;

    @Scheduled(fixedDelayString = "${execution.v2.reaper-interval-seconds:30}000")
    public void reconcileExpiredClaims() {
        ExecutionV2Keys keys = v2Properties.toKeys();
        Boolean acquired = stringRedisTemplate.opsForValue().setIfAbsent(
                keys.reaperLockKey(), "1", Duration.ofSeconds(v2Properties.getReaperIntervalSeconds()));
        if (!Boolean.TRUE.equals(acquired)) {
            return;
        }
        try {
            List<String> processing = stringRedisTemplate.opsForList().range(keys.processingList(), 0, -1);
            if (processing == null) {
                return;
            }
            long now = java.time.Instant.now().getEpochSecond();
            for (String executionId : processing) {
                String claim = stringRedisTemplate.opsForValue().get(keys.claimKey(executionId));
                if (claim == null) {
                    stringRedisTemplate.opsForList().remove(keys.processingList(), 1, executionId);
                    if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(keys.taskKey(executionId)))) {
                        stringRedisTemplate.opsForList().leftPush(keys.readyList(), executionId);
                        log.warn("[V2] requeued orphaned processing entry executionId={}", executionId);
                    } else {
                        log.warn("[V2] removed stale processing entry without task executionId={}", executionId);
                    }
                    continue;
                }
                String[] parts = claim.split("\\|", 4);
                if (parts.length == 4) {
                    long expiry = Long.parseLong(parts[3]);
                    if (expiry < now) {
                        stringRedisTemplate.opsForList().remove(keys.processingList(), 1, executionId);
                        stringRedisTemplate.opsForList().leftPush(keys.readyList(), executionId);
                        log.warn("[V2] requeued expired lease executionId={}", executionId);
                    }
                }
            }
        } finally {
            stringRedisTemplate.delete(keys.reaperLockKey());
        }
    }
}
