package xyz.hrishabhjoshi.codeexecutionengine.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PlaygroundSandboxStartupValidator {

    private final PlaygroundExecutionProperties playgroundProperties;
    private final PlaygroundSandboxProperties sandboxProperties;

    @EventListener(ApplicationReadyEvent.class)
    public void validate() {
        if (!playgroundProperties.isEnabled()) {
            return;
        }
        try {
            sandboxProperties.validateEnablement(true);
        } catch (IllegalStateException e) {
            log.error("[PLAYGROUND] Invalid sandbox configuration: {}", e.getMessage());
            throw e;
        }
        if (sandboxProperties.isKubernetesJobBackend()) {
            log.warn(
                    "[PLAYGROUND] kubernetes-job sandbox active — ensure NetworkPolicy, SA {}, and job hardening are deployed",
                    sandboxProperties.getJobServiceAccountName());
        } else {
            log.warn(
                    "[PLAYGROUND] local-process sandbox — NOT safe for hostile code; use only in trusted development");
        }
    }
}
