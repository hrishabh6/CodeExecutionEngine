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
        if ((mode == ExecutionMode.SUBMISSION || mode == ExecutionMode.PLAYGROUND)
                && v2Properties.isEnabled()
                && routingProperties.isPersistentRunnerEnabled()
                && routingProperties.backendFor(mode) == ExecutionRoutingProperties.Backend.PERSISTENT_NSJAIL) {
            return ExecutionRoutingProperties.Backend.PERSISTENT_NSJAIL;
        }
        return routingProperties.backendFor(mode);
    }
}
