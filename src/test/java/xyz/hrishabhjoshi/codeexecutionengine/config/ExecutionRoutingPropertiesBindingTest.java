package xyz.hrishabhjoshi.codeexecutionengine.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import xyz.hrishabhjoshi.codeexecutionengine.dto.ExecutionMode;

import static org.assertj.core.api.Assertions.assertThat;

class ExecutionRoutingPropertiesBindingTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfiguration.class);

    @Test
    void bindsSubmissionBackendFromLocalLauncherProperty() {
        contextRunner
                .withPropertyValues(
                        "execution.routing.persistent-runner-enabled=true",
                        "execution.routing.mode-backends.SUBMISSION=PERSISTENT_NSJAIL")
                .run(context -> {
                    ExecutionRoutingProperties properties = context.getBean(ExecutionRoutingProperties.class);
                    assertThat(properties.isPersistentRunnerEnabled()).isTrue();
                    assertThat(properties.backendFor(ExecutionMode.SUBMISSION))
                            .isEqualTo(ExecutionRoutingProperties.Backend.PERSISTENT_NSJAIL);
                    assertThat(properties.backendFor(ExecutionMode.PLAYGROUND))
                            .isEqualTo(ExecutionRoutingProperties.Backend.KUBERNETES_JOB);
                    assertThat(properties.backendFor(ExecutionMode.COMPLEXITY_PROFILE))
                            .isEqualTo(ExecutionRoutingProperties.Backend.LEGACY_QUEUE);
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(ExecutionRoutingProperties.class)
    static class TestConfiguration {
    }
}
