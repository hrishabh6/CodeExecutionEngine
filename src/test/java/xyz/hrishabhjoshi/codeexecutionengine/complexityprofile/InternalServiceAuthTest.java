package xyz.hrishabhjoshi.codeexecutionengine.complexityprofile;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import xyz.hrishabhjoshi.codeexecutionengine.complexityprofile.internal.InternalServiceAuth;
import xyz.hrishabhjoshi.codeexecutionengine.config.ComplexityProfileExecutionProperties;
import xyz.hrishabhjoshi.codeexecutionengine.execution.ExecutionRequestRejectedException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InternalServiceAuthTest {

    @Mock
    private HttpServletRequest request;

    @Test
    void requiresTokenInProductionMode() {
        ComplexityProfileExecutionProperties properties = new ComplexityProfileExecutionProperties();
        properties.setInternalAuthRequireToken(true);
        properties.setInternalServiceToken("secret");
        InternalServiceAuth auth = new InternalServiceAuth(properties);
        when(request.getHeader(ComplexityProfileExecutionProperties.INTERNAL_SERVICE_TOKEN_HEADER)).thenReturn(null);
        assertThrows(ExecutionRequestRejectedException.class, () -> auth.requireInternal(request));
    }

    @Test
    void developmentHeaderFallbackAllowed() {
        ComplexityProfileExecutionProperties properties = new ComplexityProfileExecutionProperties();
        InternalServiceAuth auth = new InternalServiceAuth(properties);
        when(request.getHeader(InternalServiceAuth.INTERNAL_CALL_HEADER)).thenReturn("true");
        assertDoesNotThrow(() -> auth.requireInternal(request));
    }
}
