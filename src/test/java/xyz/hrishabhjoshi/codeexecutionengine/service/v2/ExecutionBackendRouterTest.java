package xyz.hrishabhjoshi.codeexecutionengine.service.v2;

import org.junit.jupiter.api.Test;
import xyz.hrishabhjoshi.codeexecutionengine.config.ExecutionRoutingProperties;
import xyz.hrishabhjoshi.codeexecutionengine.config.ExecutionV2Properties;
import xyz.hrishabhjoshi.codeexecutionengine.dto.ExecutionMode;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExecutionBackendRouterTest {

    @Test
    void defaultsToLegacyWhenFlagsDisabled() {
        ExecutionV2Properties v2 = new ExecutionV2Properties();
        ExecutionRoutingProperties routing = new ExecutionRoutingProperties();
        ExecutionBackendRouter router = new ExecutionBackendRouter(v2, routing);
        assertEquals(ExecutionRoutingProperties.Backend.LEGACY_QUEUE,
                router.resolve(ExecutionMode.SUBMISSION));
    }

    @Test
    void selectsPersistentOnlyWhenExplicitlyEnabled() {
        ExecutionV2Properties v2 = new ExecutionV2Properties();
        v2.setEnabled(true);
        ExecutionRoutingProperties routing = new ExecutionRoutingProperties();
        routing.setPersistentRunnerEnabled(true);
        routing.getModeBackends().put(ExecutionMode.SUBMISSION, ExecutionRoutingProperties.Backend.PERSISTENT_NSJAIL);
        ExecutionBackendRouter router = new ExecutionBackendRouter(v2, routing);
        assertEquals(ExecutionRoutingProperties.Backend.PERSISTENT_NSJAIL,
                router.resolve(ExecutionMode.SUBMISSION));
        assertEquals(ExecutionRoutingProperties.Backend.KUBERNETES_JOB,
                router.resolve(ExecutionMode.PLAYGROUND));
    }

    @Test
    void playgroundUsesPersistentNsjailOnlyWhenItsOwnBackendSaysSo() {
        ExecutionV2Properties v2 = new ExecutionV2Properties();
        v2.setEnabled(true);
        ExecutionRoutingProperties routing = new ExecutionRoutingProperties();
        routing.setPersistentRunnerEnabled(true);
        routing.getModeBackends().put(ExecutionMode.PLAYGROUND, ExecutionRoutingProperties.Backend.PERSISTENT_NSJAIL);
        ExecutionBackendRouter router = new ExecutionBackendRouter(v2, routing);
        assertEquals(ExecutionRoutingProperties.Backend.PERSISTENT_NSJAIL,
                router.resolve(ExecutionMode.PLAYGROUND, "java"));
        assertEquals(ExecutionRoutingProperties.Backend.KUBERNETES_JOB,
                router.resolve(ExecutionMode.PLAYGROUND, "python"));
        assertEquals(ExecutionRoutingProperties.Backend.KUBERNETES_JOB,
                router.resolve(ExecutionMode.PLAYGROUND, "PYTHON"));
        assertEquals(ExecutionRoutingProperties.Backend.KUBERNETES_JOB,
                router.resolve(ExecutionMode.PLAYGROUND));
    }
}
