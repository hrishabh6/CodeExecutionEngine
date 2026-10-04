package xyz.hrishabhjoshi.codeexecutionengine.config;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class ComplexityProfileStartupValidator {

    private final ComplexityProfileExecutionProperties properties;
    private final KubernetesExecutionProperties kubernetesProperties;
    private final Environment environment;

    public ComplexityProfileStartupValidator(
            ComplexityProfileExecutionProperties properties,
            KubernetesExecutionProperties kubernetesProperties,
            Environment environment) {
        this.properties = properties;
        this.kubernetesProperties = kubernetesProperties;
        this.environment = environment;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void validateConfiguration() {
        if (!properties.isEnabled()) {
            return;
        }
        if (properties.isInternalAuthRequireToken()
                && !StringUtils.hasText(properties.getInternalServiceToken())) {
            throw new IllegalStateException(
                    "execution.complexity-profile.internal-auth-require-token is true but internal service token is missing");
        }
        if (properties.getSandbox().isKubernetesJobBackend()) {
            if (!properties.getSandbox().isProductionVerified()) {
                throw new IllegalStateException(
                        "COMPLEXITY_PROFILE kubernetes-job sandbox requires execution.complexity-profile.sandbox.production-verified=true");
            }
            if (!StringUtils.hasText(kubernetesProperties.getJobImage())) {
                throw new IllegalStateException(
                        "COMPLEXITY_PROFILE kubernetes-job sandbox requires execution.kubernetes.job-image");
            }
            if (!StringUtils.hasText(properties.getSandbox().getJobServiceAccountName())) {
                throw new IllegalStateException(
                        "COMPLEXITY_PROFILE kubernetes-job sandbox requires job service account name");
            }
            if (properties.getSandbox().isRequireSandboxRedisCredentials()
                    && (!StringUtils.hasText(properties.getSandbox().getSandboxRedisUsername())
                    || !StringUtils.hasText(properties.getSandbox().getSandboxRedisPassword()))) {
                throw new IllegalStateException(
                        "COMPLEXITY_PROFILE production sandbox requires dedicated sandbox Redis credentials "
                                + "(execution.complexity-profile.sandbox.sandbox-redis-username/password)");
            }
        }
        if ("local-process".equalsIgnoreCase(properties.getSandbox().getBackend())
                && properties.getSandbox().isTrustedDevelopmentOnly()
                && isProductionLikeProfile()) {
            throw new IllegalStateException(
                    "COMPLEXITY_PROFILE local-process sandbox is trusted-development-only; "
                            + "use kubernetes-job with production-verified in production profiles");
        }
    }

    private boolean isProductionLikeProfile() {
        for (String profile : environment.getActiveProfiles()) {
            if ("prod".equalsIgnoreCase(profile) || "production".equalsIgnoreCase(profile)) {
                return true;
            }
        }
        return false;
    }
}
