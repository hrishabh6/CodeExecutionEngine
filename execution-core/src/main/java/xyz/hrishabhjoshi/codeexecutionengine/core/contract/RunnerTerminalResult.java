package xyz.hrishabhjoshi.codeexecutionengine.core.contract;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import xyz.hrishabhjoshi.codeexecutionengine.dto.CodeExecutionResultDTO;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RunnerTerminalResult(
        String contractVersion,
        String taskId,
        String attemptId,
        String terminalCategory,
        CodeExecutionResultDTO result) {

    public RunnerTerminalResult {
        if (contractVersion == null || contractVersion.isBlank()) {
            contractVersion = ExecutionContractVersion.V2_RUNNER_QUEUE;
        }
    }
}
