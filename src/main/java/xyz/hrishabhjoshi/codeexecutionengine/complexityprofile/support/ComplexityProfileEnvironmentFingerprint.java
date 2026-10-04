package xyz.hrishabhjoshi.codeexecutionengine.complexityprofile.support;

import xyz.hrishabhjoshi.codeexecutionengine.config.ComplexityProfileExecutionProperties;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

public final class ComplexityProfileEnvironmentFingerprint {

    private ComplexityProfileEnvironmentFingerprint() {
    }

    public static String build(ComplexityProfileExecutionProperties properties) {
        String canonical = String.join("|",
                safe(properties.getHarnessVersion()),
                safe(properties.getMeasurementPolicyVersion()),
                "cxe-local-process-v1",
                safe(System.getProperty("java.vendor")),
                safe(System.getProperty("java.version")),
                safe(System.getProperty("os.arch")),
                safe(properties.getSandbox().getBackend()),
                sandboxResourceClass(properties));
        return sha256Hex(canonical);
    }

    private static String sandboxResourceClass(ComplexityProfileExecutionProperties properties) {
        return "cpu=shared-worker|memory=shared-worker|image=cxe-dev-local-process";
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
