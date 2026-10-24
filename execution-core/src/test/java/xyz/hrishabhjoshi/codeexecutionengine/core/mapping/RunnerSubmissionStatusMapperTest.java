package xyz.hrishabhjoshi.codeexecutionengine.core.mapping;

import org.junit.jupiter.api.Test;
import xyz.hrishabhjoshi.codeexecutionengine.dto.CodeExecutionResultDTO;
import xyz.hrishabhjoshi.codeexecutionengine.dto.Status;
import xyz.hrishabhjoshi.codeexecutionengine.dto.SubmissionStatusDto;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RunnerSubmissionStatusMapperTest {

    @Test
    void mapsSuccessfulRunnerResultToCompatibleCompletedStatus() {
        CodeExecutionResultDTO result = CodeExecutionResultDTO.builder()
                .submissionId("submission-1")
                .executionId("execution-1")
                .overallStatus(Status.SUCCESS)
                .testCaseOutputs(List.of(CodeExecutionResultDTO.TestCaseOutput.builder()
                        .testCaseIndex(0)
                        .actualOutput("5")
                        .executionTimeMs(7)
                        .memoryBytes(2048L)
                        .build()))
                .build();

        SubmissionStatusDto status = RunnerSubmissionStatusMapper.terminal(result, "runner-local", 1234L);

        assertEquals("COMPLETED", status.getStatus());
        assertEquals("submission-1", status.getSubmissionId());
        assertEquals("execution-1", status.getExecutionId());
        assertEquals(7, status.getRuntimeMs());
        assertEquals(2, status.getMemoryKb());
        assertEquals("5", status.getTestCaseResults().getFirst().getActualOutput());
        assertEquals("runner-local", status.getWorkerId());
        assertNull(status.getErrorMessage());
    }

    @Test
    void mapsCompilationErrorToFailedStatusAndStableCategory() {
        CodeExecutionResultDTO result = CodeExecutionResultDTO.builder()
                .submissionId("submission-2")
                .executionId("execution-2")
                .overallStatus(Status.COMPILATON_ERROR)
                .compilationOutput("cannot find symbol")
                .build();

        SubmissionStatusDto status = RunnerSubmissionStatusMapper.terminal(result, "runner-local", 1234L);

        assertEquals("FAILED", status.getStatus());
        assertEquals("COMPILATION_ERROR", status.getErrorMessage());
        assertEquals("cannot find symbol", status.getCompilationOutput());
    }

    @Test
    void keepsRuntimeErrorsTerminalAndJudgeable() {
        CodeExecutionResultDTO result = CodeExecutionResultDTO.builder()
                .submissionId("submission-3")
                .executionId("execution-3")
                .overallStatus(Status.RUNTIME_ERROR)
                .build();

        SubmissionStatusDto status = RunnerSubmissionStatusMapper.terminal(result, "runner-local", 1234L);

        assertEquals("COMPLETED", status.getStatus());
        assertEquals("RUNTIME_ERROR", status.getErrorMessage());
    }

    @Test
    void mapsPlaygroundStdoutWithoutSubmissionVerdict() {
        CodeExecutionResultDTO result = CodeExecutionResultDTO.builder()
                .submissionId("playground-1")
                .executionId("playground-1")
                .overallStatus(Status.SUCCESS)
                .compilationOutput("")
                .testCaseOutputs(List.of(CodeExecutionResultDTO.TestCaseOutput.builder()
                        .testCaseIndex(0)
                        .actualOutput("42\n")
                        .errorMessage("")
                        .executionTimeMs(12)
                        .build()))
                .build();

        SubmissionStatusDto status = RunnerSubmissionStatusMapper.playground(result, "runner-local", 1234L);

        assertEquals("COMPLETED", status.getStatus());
        assertEquals("PLAYGROUND", status.getExecutionMode());
        assertEquals("SUCCESS", status.getRawExecutionStatus());
        assertEquals("42\n", status.getStdout());
        assertEquals("", status.getStderr());
        assertEquals(0, status.getExitCode());
        assertEquals(12, status.getRuntimeMs());
        assertNull(status.getErrorMessage());
    }
}
