package xyz.hrishabhjoshi.codeexecutionengine.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "execution.playground")
public class PlaygroundExecutionProperties {

    /**
     * When false, PLAYGROUND requests are rejected at ingress and in the worker.
     */
    private boolean enabled = false;

    private long executionTimeoutSeconds = 5;
    private int maxCombinedOutputBytes = 64 * 1024;
    private int maxCompilerOutputBytes = 32 * 1024;
    private int maxSourceBytes = 32 * 1024;
    private int maxStdinBytes = 8 * 1024;
}
