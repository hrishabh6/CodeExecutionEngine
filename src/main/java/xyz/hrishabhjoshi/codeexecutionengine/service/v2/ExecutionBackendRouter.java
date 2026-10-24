package xyz.hrishabhjoshi.codeexecutionengine.service.v2;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import xyz.hrishabhjoshi.codeexecutionengine.config.ExecutionRoutingProperties;
import xyz.hrishabhjoshi.codeexecutionengine.config.ExecutionV2Properties;
import xyz.hrishabhjoshi.codeexecutionengine.dto.ExecutionMode;

@Component
@RequiredArgsConstructor
public class ExecutionBackendRouter {

    private final ExecutionV2Properties v2Properties;
    private final ExecutionRoutingProperties routingProperties;

    public ExecutionRoutingProperties.Backend resolve(ExecutionMode mode) {
        return resolve(mode, null);
    }

    /**
     * Playground NsJail is Java-only. Python and any other playground language stay on
     * the Kubernetes Job backend even when the playground mode backend is persistent.
     */
    public ExecutionRoutingProperties.Backend resolve(ExecutionMode mode, String language) {
        if (mode == ExecutionMode.PLAYGROUND && !isJava(language)) {
            return ExecutionRoutingProperties.Backend.KUBERNETES_JOB;
        }
        if ((mode == ExecutionMode.SUBMISSION || mode == ExecutionMode.PLAYGROUND)
                && v2Properties.isEnabled()
                && routingProperties.isPersistentRunnerEnabled()
                && routingProperties.backendFor(mode) == ExecutionRoutingProperties.Backend.PERSISTENT_NSJAIL) {
            return ExecutionRoutingProperties.Backend.PERSISTENT_NSJAIL;
        }
        return routingProperties.backendFor(mode);
    }

    private static boolean isJava(String language) {
        return language != null && "java".equalsIgnoreCase(language.trim());
    }
}
