package xyz.hrishabhjoshi.codeexecutionengine.config;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class ComplexityProfileStartupValidator {

    private final ComplexityProfileExecutionProperties properties;
    private final Environment environment;

    public ComplexityProfileStartupValidator(
            ComplexityProfileExecutionProperties properties,
            Environment environment) {
        this.properties = properties;
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
        if ("kubernetes-job".equalsIgnoreCase(properties.getSandbox().getBackend())
                && !properties.getSandbox().isProductionVerified()) {
            throw new IllegalStateException(
                    "COMPLEXITY_PROFILE kubernetes-job sandbox requires production-verified gate (Phase 5)");
        }
        if ("local-process".equalsIgnoreCase(properties.getSandbox().getBackend())
                && properties.getSandbox().isTrustedDevelopmentOnly()
                && isProductionLikeProfile()) {
            throw new IllegalStateException(
                    "COMPLEXITY_PROFILE local-process sandbox is trusted-development-only; "
                            + "disable execution.complexity-profile.enabled in production (Phase 5 kubernetes-job required)");
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
