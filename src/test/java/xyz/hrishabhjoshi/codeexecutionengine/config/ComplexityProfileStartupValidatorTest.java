package xyz.hrishabhjoshi.codeexecutionengine.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ComplexityProfileStartupValidatorTest {

    @Test
    void blocksLocalProcessProfilerInProductionProfile() {
        ComplexityProfileExecutionProperties properties = new ComplexityProfileExecutionProperties();
        properties.setEnabled(true);
        properties.getSandbox().setBackend("local-process");
        properties.getSandbox().setTrustedDevelopmentOnly(true);
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        ComplexityProfileStartupValidator validator = new ComplexityProfileStartupValidator(
                properties, new KubernetesExecutionProperties(), environment);
        assertThrows(IllegalStateException.class, validator::validateConfiguration);
    }

    @Test
    void blocksKubernetesBackendWithoutProductionVerified() {
        ComplexityProfileExecutionProperties properties = new ComplexityProfileExecutionProperties();
        properties.setEnabled(true);
        properties.getSandbox().setBackend("kubernetes-job");
        properties.getSandbox().setProductionVerified(false);
        ComplexityProfileStartupValidator validator = new ComplexityProfileStartupValidator(
                properties, new KubernetesExecutionProperties(), new MockEnvironment());
        assertThrows(IllegalStateException.class, validator::validateConfiguration);
    }

    @Test
    void blocksKubernetesBackendWithoutJobImage() {
        ComplexityProfileExecutionProperties properties = new ComplexityProfileExecutionProperties();
        properties.setEnabled(true);
        properties.getSandbox().setBackend("kubernetes-job");
        properties.getSandbox().setProductionVerified(true);
        KubernetesExecutionProperties k8s = new KubernetesExecutionProperties();
        ComplexityProfileStartupValidator validator = new ComplexityProfileStartupValidator(
                properties, k8s, new MockEnvironment());
        assertThrows(IllegalStateException.class, validator::validateConfiguration);
    }

    @Test
    void blocksKubernetesBackendWithoutSandboxRedisCredentials() {
        ComplexityProfileExecutionProperties properties = new ComplexityProfileExecutionProperties();
        properties.setEnabled(true);
        properties.getSandbox().setBackend("kubernetes-job");
        properties.getSandbox().setProductionVerified(true);
        properties.getSandbox().setRequireSandboxRedisCredentials(true);
        KubernetesExecutionProperties k8s = new KubernetesExecutionProperties();
        k8s.setJobImage("example/cxe:2.0.0");
        ComplexityProfileStartupValidator validator = new ComplexityProfileStartupValidator(
                properties, k8s, new MockEnvironment());
        assertThrows(IllegalStateException.class, validator::validateConfiguration);
    }

    @Test
    void allowsVerifiedKubernetesBackendWithJobImage() {
        ComplexityProfileExecutionProperties properties = new ComplexityProfileExecutionProperties();
        properties.setEnabled(true);
        properties.getSandbox().setBackend("kubernetes-job");
        properties.getSandbox().setProductionVerified(true);
        KubernetesExecutionProperties k8s = new KubernetesExecutionProperties();
        k8s.setJobImage("example/cxe:2.0.0");
        properties.getSandbox().setSandboxRedisUsername("cxe-profile-sandbox");
        properties.getSandbox().setSandboxRedisPassword("secret");
        ComplexityProfileStartupValidator validator = new ComplexityProfileStartupValidator(
                properties, k8s, new MockEnvironment());
        assertDoesNotThrow(validator::validateConfiguration);
    }

    @Test
    void allowsDisabledProfilerRegardlessOfProfile() {
        ComplexityProfileExecutionProperties properties = new ComplexityProfileExecutionProperties();
        properties.setEnabled(false);
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        ComplexityProfileStartupValidator validator = new ComplexityProfileStartupValidator(
                properties, new KubernetesExecutionProperties(), environment);
        assertDoesNotThrow(validator::validateConfiguration);
    }
}
