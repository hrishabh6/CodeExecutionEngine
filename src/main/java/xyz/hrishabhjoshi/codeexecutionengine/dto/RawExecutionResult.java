package xyz.hrishabhjoshi.codeexecutionengine.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RawExecutionResult {
    RawExecutionStatus status;
    String stdout;
    String stderr;
    String compilerOutput;
    Integer exitCode;
    int runtimeMs;
    Integer memoryKb;
    boolean outputTruncated;
}
