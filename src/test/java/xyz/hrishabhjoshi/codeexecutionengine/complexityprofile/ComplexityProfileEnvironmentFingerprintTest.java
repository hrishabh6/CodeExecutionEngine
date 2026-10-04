package xyz.hrishabhjoshi.codeexecutionengine.complexityprofile;

import org.junit.jupiter.api.Test;
import xyz.hrishabhjoshi.codeexecutionengine.complexityprofile.support.ComplexityProfileEnvironmentFingerprint;
import xyz.hrishabhjoshi.codeexecutionengine.config.ComplexityProfileExecutionProperties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ComplexityProfileEnvironmentFingerprintTest {

    @Test
    void equivalentConfigurationsProduceIdenticalFingerprint() {
        ComplexityProfileExecutionProperties first = baseProperties();
        ComplexityProfileExecutionProperties second = baseProperties();
        String fp1 = ComplexityProfileEnvironmentFingerprint.build(first);
        String fp2 = ComplexityProfileEnvironmentFingerprint.build(second);
        assertEquals(fp1, fp2);
        assertFalse(fp1.isBlank());
    }

    @Test
    void fingerprintDoesNotEmbedProcessSpecificValues() {
        String fp = ComplexityProfileEnvironmentFingerprint.build(baseProperties());
        assertFalse(fp.contains(String.valueOf(ProcessHandle.current().pid())));
    }

    private static ComplexityProfileExecutionProperties baseProperties() {
        ComplexityProfileExecutionProperties properties = new ComplexityProfileExecutionProperties();
        properties.setHarnessVersion("v1");
        properties.setMeasurementPolicyVersion("v1");
        properties.getSandbox().setBackend("local-process");
        return properties;
    }
}
