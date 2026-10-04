package xyz.hrishabhjoshi.codeexecutionengine.complexityprofile.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import xyz.hrishabhjoshi.codeexecutionengine.complexityprofile.dto.ComplexityProfileExecutionDtos.ComplexityProfileSubmitRequest;
import xyz.hrishabhjoshi.codeexecutionengine.complexityprofile.dto.ComplexityProfileExecutionDtos.ComplexityProfileSubmitResponse;
import xyz.hrishabhjoshi.codeexecutionengine.config.ComplexityProfileExecutionProperties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ComplexityProfileSubmitIdempotencyTest {

    @Mock
    private ComplexityProfileRequestValidator requestValidator;
    @Mock
    private ComplexityProfileQueueService queueService;
    @Mock
    private ComplexityProfileExecutionProperties properties;

    private ComplexityProfileOrchestrationService orchestrationService;

    @BeforeEach
    void setUp() {
        orchestrationService = new ComplexityProfileOrchestrationService(requestValidator, queueService, properties);
    }

    @Test
    void resubmitWithSameExecutionIdDoesNotEnqueueAgain() {
        ComplexityProfileSubmitRequest request = org.mockito.Mockito.mock(ComplexityProfileSubmitRequest.class);
        when(request.executionId()).thenReturn("cpa-analysis-1");
        when(queueService.hasAcceptedExecution("cpa-analysis-1")).thenReturn(true);
        when(queueService.getStatus("cpa-analysis-1")).thenReturn(java.util.Optional.of(
                new xyz.hrishabhjoshi.codeexecutionengine.complexityprofile.dto.ComplexityProfileExecutionDtos.ComplexityProfilePollResponse(
                        "cpa-analysis-1", "QUEUED", null, null, null, null, null, null, null, null, null)));

        ComplexityProfileSubmitResponse response = orchestrationService.submit(request);

        assertEquals("cpa-analysis-1", response.executionId());
        verify(queueService, times(0)).tryEnqueue(any());
    }
}
