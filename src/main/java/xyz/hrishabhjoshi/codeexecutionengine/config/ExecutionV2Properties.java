package xyz.hrishabhjoshi.codeexecutionengine.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import xyz.hrishabhjoshi.codeexecutionengine.core.v2.ExecutionV2Keys;

@Getter
@Setter
@ConfigurationProperties(prefix = "execution.v2")
public class ExecutionV2Properties {

    /** Master gate for v2 publisher, reaper, and routing (default off). */
    private boolean enabled = false;

    private String keyPrefix = "execution:v2:";

    private long taskTtlSeconds = 3600;

    private long statusTtlSeconds = 3600;

    private long queueCapacity = 100;

    private long claimGraceSeconds = 15;

    private long reaperIntervalSeconds = 30;

    private boolean reaperEnabled = false;

    public ExecutionV2Keys toKeys() {
        return new ExecutionV2Keys(keyPrefix);
    }
}
