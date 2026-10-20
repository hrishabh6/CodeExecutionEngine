package xyz.hrishabhjoshi.codeexecutionengine.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import xyz.hrishabhjoshi.codeexecutionengine.dto.ExecutionMode;

import java.util.EnumMap;
import java.util.Map;

@Getter
@Setter
@ConfigurationProperties(prefix = "execution.routing")
public class ExecutionRoutingProperties {

    public enum Backend {
        LEGACY_QUEUE,
        KUBERNETES_JOB,
        PERSISTENT_NSJAIL
    }

    /** When false, SUBMISSION always uses legacy queue / configured kubernetes job path. */
    private boolean persistentRunnerEnabled = false;

    private Map<ExecutionMode, Backend> modeBackends = defaultBackends();

    private static Map<ExecutionMode, Backend> defaultBackends() {
        Map<ExecutionMode, Backend> map = new EnumMap<>(ExecutionMode.class);
        map.put(ExecutionMode.SUBMISSION, Backend.LEGACY_QUEUE);
        map.put(ExecutionMode.PLAYGROUND, Backend.KUBERNETES_JOB);
        map.put(ExecutionMode.COMPLEXITY_PROFILE, Backend.LEGACY_QUEUE);
        return map;
    }

    public Backend backendFor(ExecutionMode mode) {
        return modeBackends.getOrDefault(mode, Backend.LEGACY_QUEUE);
    }
}
