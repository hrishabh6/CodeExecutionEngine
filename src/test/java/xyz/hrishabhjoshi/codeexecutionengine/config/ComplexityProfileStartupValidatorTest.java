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
        ComplexityProfileStartupValidator validator = new ComplexityProfileStartupValidator(properties, environment);
        assertThrows(IllegalStateException.class, validator::validateConfiguration);
    }

    @Test
    void allowsDisabledProfilerRegardlessOfProfile() {
        ComplexityProfileExecutionProperties properties = new ComplexityProfileExecutionProperties();
        properties.setEnabled(false);
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        ComplexityProfileStartupValidator validator = new ComplexityProfileStartupValidator(properties, environment);
        assertDoesNotThrow(validator::validateConfiguration);
    }
}
