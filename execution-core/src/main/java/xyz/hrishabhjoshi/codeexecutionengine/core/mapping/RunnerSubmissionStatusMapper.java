package xyz.hrishabhjoshi.codeexecutionengine.core.mapping;

import xyz.hrishabhjoshi.codeexecutionengine.dto.CodeExecutionResultDTO;
import xyz.hrishabhjoshi.codeexecutionengine.dto.ExecutionMode;
import xyz.hrishabhjoshi.codeexecutionengine.dto.Status;
import xyz.hrishabhjoshi.codeexecutionengine.dto.SubmissionStatusDto;

import java.util.List;

/** Maps a runner terminal result to the public CXE polling contract. */
public final class RunnerSubmissionStatusMapper {

    private RunnerSubmissionStatusMapper() {
    }

    public static SubmissionStatusDto terminal(
            CodeExecutionResultDTO result,
            String workerId,
            long completedAt) {
        Status overallStatus = result.getOverallStatus();
        boolean infrastructureFailure = overallStatus == Status.COMPILATON_ERROR
                || overallStatus == Status.INTERNAL_ERROR
                || overallStatus == Status.UNKNOWN;
        List<SubmissionStatusDto.TestCaseResult> testCases = result.getTestCaseOutputs() == null
                ? List.of()
                : result.getTestCaseOutputs().stream()
                        .map(RunnerSubmissionStatusMapper::mapTestCase)
                        .toList();

        long runtimeMs = result.getTestCaseOutputs() == null
                ? 0L
                : result.getTestCaseOutputs().stream()
                        .mapToLong(CodeExecutionResultDTO.TestCaseOutput::getExecutionTimeMs)
                        .sum();

        return SubmissionStatusDto.builder()
                .submissionId(result.getSubmissionId())
                .executionId(result.getExecutionId())
                .status(infrastructureFailure ? "FAILED" : "COMPLETED")
                .runtimeMs(runtimeMs > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) runtimeMs)
                .memoryKb(maxMemoryKb(result.getTestCaseOutputs()))
                .errorMessage(errorCategory(overallStatus))
                .compilationOutput(result.getCompilationOutput())
                .testCaseResults(testCases)
                .completedAt(completedAt)
                .workerId(workerId)
                .executionMode(ExecutionMode.SUBMISSION.name())
                .build();
    }

    private static SubmissionStatusDto.TestCaseResult mapTestCase(CodeExecutionResultDTO.TestCaseOutput output) {
        return SubmissionStatusDto.TestCaseResult.builder()
                .index(output.getTestCaseIndex())
                .passed(null)
                .actualOutput(output.getActualOutput())
                .executionTimeMs(output.getExecutionTimeMs())
                .memoryBytes(output.getMemoryBytes())
                .error(output.getErrorMessage())
                .errorType(output.getErrorType())
                .build();
    }

    private static Integer maxMemoryKb(List<CodeExecutionResultDTO.TestCaseOutput> outputs) {
        if (outputs == null) {
            return null;
        }
        long maxBytes = outputs.stream()
                .map(CodeExecutionResultDTO.TestCaseOutput::getMemoryBytes)
                .filter(value -> value != null && value > 0)
                .mapToLong(Long::longValue)
                .max()
                .orElse(0L);
        if (maxBytes == 0L) {
            return null;
        }
        long kilobytes = (maxBytes + 1023L) / 1024L;
        return kilobytes > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) kilobytes;
    }

    private static String errorCategory(Status status) {
        if (status == null) {
            return "INTERNAL_ERROR";
        }
        return switch (status) {
            case SUCCESS -> null;
            case COMPILATON_ERROR -> "COMPILATION_ERROR";
            case TIMEOUT -> "TIME_LIMIT_EXCEEDED";
            case RUNTIME_ERROR -> "RUNTIME_ERROR";
            case INTERNAL_ERROR, UNKNOWN -> "INTERNAL_ERROR";
        };
    }
}
