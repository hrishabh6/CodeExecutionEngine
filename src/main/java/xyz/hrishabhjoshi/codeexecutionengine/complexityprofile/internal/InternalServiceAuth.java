package xyz.hrishabhjoshi.codeexecutionengine.complexityprofile.internal;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import xyz.hrishabhjoshi.codeexecutionengine.config.ComplexityProfileExecutionProperties;
import xyz.hrishabhjoshi.codeexecutionengine.execution.ExecutionRequestRejectedException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
public class InternalServiceAuth {

    public static final String INTERNAL_CALL_HEADER = "X-Internal-Call";

    private final ComplexityProfileExecutionProperties properties;

    public InternalServiceAuth(ComplexityProfileExecutionProperties properties) {
        this.properties = properties;
    }

    public void requireInternal(HttpServletRequest request) {
        String configuredToken = properties.getInternalServiceToken();
        boolean tokenConfigured = StringUtils.hasText(configuredToken);
        if (properties.isInternalAuthRequireToken() || tokenConfigured) {
            if (!tokenConfigured) {
                throw new ExecutionRequestRejectedException(
                        "INTERNAL_AUTH_MISCONFIGURED",
                        "Internal service token is not configured");
            }
            String provided = request.getHeader(ComplexityProfileExecutionProperties.INTERNAL_SERVICE_TOKEN_HEADER);
            if (provided == null || !constantTimeEquals(configuredToken, provided)) {
                throw new ExecutionRequestRejectedException(
                        "FORBIDDEN",
                        "Internal service authentication required");
            }
            return;
        }
        String internalCall = request.getHeader(INTERNAL_CALL_HEADER);
        if (!"true".equalsIgnoreCase(internalCall)) {
            throw new ExecutionRequestRejectedException(
                    "FORBIDDEN",
                    "Internal call header required");
        }
    }

    private static boolean constantTimeEquals(String expected, String provided) {
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                provided.getBytes(StandardCharsets.UTF_8));
    }
}
