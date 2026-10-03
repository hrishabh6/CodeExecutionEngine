package xyz.hrishabhjoshi.codeexecutionengine.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Sandbox isolation for PLAYGROUND. SUBMISSION jobs are unchanged unless {@code hardenAllExecutionJobs} is set.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "execution.playground.sandbox")
public class PlaygroundSandboxProperties {

    /**
     * {@code local-process} runs in the CXE worker pod (trusted local dev only).
     * {@code kubernetes-job} runs each playground in a hardened one-shot Job.
     */
    private String backend = "local-process";

    /**
     * Operator attestation that cluster NetworkPolicy, SA, and job template were verified (Section 25).
     */
    private boolean productionVerified = false;

    /** Dedicated Job SA with no RBAC and automountServiceAccountToken=false. */
    private String jobServiceAccountName = "cxe-playground-executor";

    private long runAsUser = 65532;
    private long runAsGroup = 65532;
    private long fsGroup = 65532;

    /** Apply the same pod hardening to harness SUBMISSION kubernetes jobs. */
    private boolean hardenAllExecutionJobs = false;

    public boolean isKubernetesJobBackend() {
        return "kubernetes-job".equalsIgnoreCase(backend == null ? "" : backend.trim());
    }

    public boolean isLocalProcessBackend() {
        return !isKubernetesJobBackend();
    }

    public void validateEnablement(boolean playgroundEnabled) {
        if (!playgroundEnabled) {
            return;
        }
        if (isKubernetesJobBackend() && !productionVerified) {
            throw new IllegalStateException(
                    "PLAYGROUND kubernetes-job sandbox requires execution.playground.sandbox.production-verified=true");
        }
    }
}
