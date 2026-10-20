package xyz.hrishabhjoshi.codeexecutionengine.service.helperservice;

import org.junit.jupiter.api.Test;
import xyz.hrishabhjoshi.codeexecutionengine.dto.SubmissionStatusDto;
import xyz.hrishabhjoshi.codeexecutionengine.service.v2.ExecutionV2StatusStore;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SubmissionStatusServiceV2Test {

    @Test
    void fallsBackToV2StatusWhenLegacyStatusIsAbsent() {
        ExecutionQueueService queue = mock(ExecutionQueueService.class);
        ExecutionV2StatusStore v2 = mock(ExecutionV2StatusStore.class);
        SubmissionStatusDto completed = SubmissionStatusDto.builder()
                .submissionId("submission-1")
                .status("COMPLETED")
                .build();
        when(queue.getRedisStatus("submission-1")).thenReturn(Optional.empty());
        when(v2.getStatus("submission-1")).thenReturn(Optional.of(completed));

        SubmissionStatusService service = new SubmissionStatusService(queue, Optional.of(v2));

        assertEquals("COMPLETED", service.getFullResults("submission-1").orElseThrow().getStatus());
    }

    @Test
    void keepsLegacyStatusAuthoritativeWhenPresent() {
        ExecutionQueueService queue = mock(ExecutionQueueService.class);
        ExecutionV2StatusStore v2 = mock(ExecutionV2StatusStore.class);
        SubmissionStatusDto legacy = SubmissionStatusDto.builder()
                .submissionId("submission-2")
                .status("RUNNING")
                .build();
        when(queue.getRedisStatus("submission-2")).thenReturn(Optional.of(legacy));

        SubmissionStatusService service = new SubmissionStatusService(queue, Optional.of(v2));

        assertEquals("RUNNING", service.getStatus("submission-2").orElseThrow().getStatus());
        verifyNoInteractions(v2);
    }
}
