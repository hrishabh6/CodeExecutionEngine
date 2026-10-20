package xyz.hrishabhjoshi.codeexecutionengine.runner.support;

import xyz.hrishabhjoshi.codeexecutionengine.dto.CodeSubmissionDTO;
import xyz.hrishabhjoshi.codeexecutionengine.dto.ParamInfoDTO;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class RunnerGoldenFixtures {

    private RunnerGoldenFixtures() {
    }

    public static CodeSubmissionDTO javaAddSubmission() {
        CodeSubmissionDTO.QuestionMetadata metadata = CodeSubmissionDTO.QuestionMetadata.builder()
                .fullyQualifiedPackageName("q.add")
                .functionName("add")
                .returnType("int")
                .questionType("ALGORITHM")
                .parameters(List.of(new ParamInfoDTO("a", "int"), new ParamInfoDTO("b", "int")))
                .build();
        return CodeSubmissionDTO.builder()
                .submissionId("sub-runner-add")
                .executionId("exec-runner-add")
                .language("java")
                .userSolutionCode(
                        "class Solution { public int add(int a, int b) { return a + b; } }")
                .questionMetadata(metadata)
                .testCases(List.of(Map.of("input", Map.of("a", 2, "b", 3))))
                .build();
    }

    public static CodeSubmissionDTO javaCompileErrorSubmission() {
        CodeSubmissionDTO.QuestionMetadata metadata = CodeSubmissionDTO.QuestionMetadata.builder()
                .fullyQualifiedPackageName("q.add")
                .functionName("add")
                .returnType("int")
                .questionType("ALGORITHM")
                .parameters(List.of(new ParamInfoDTO("a", "int"), new ParamInfoDTO("b", "int")))
                .build();
        return CodeSubmissionDTO.builder()
                .submissionId("sub-runner-compile-error")
                .executionId("exec-runner-compile-error")
                .language("java")
                .userSolutionCode("class Solution { public int add(int a, int b) { return } }")
                .questionMetadata(metadata)
                .testCases(List.of(Map.of("input", Map.of("a", 1, "b", 1))))
                .build();
    }

    public static xyz.hrishabhjoshi.codeexecutionengine.core.contract.RunnerTaskEnvelope envelopeFor(
            CodeSubmissionDTO submission, String payloadBase64) {
        return new xyz.hrishabhjoshi.codeexecutionengine.core.contract.RunnerTaskEnvelope(
                xyz.hrishabhjoshi.codeexecutionengine.core.contract.ExecutionContractVersion.V2_RUNNER_QUEUE,
                submission.getExecutionId(),
                "0",
                submission.getSubmissionId(),
                submission.getExecutionId(),
                "RUN",
                submission.getLanguage(),
                payloadBase64,
                Instant.now().plusSeconds(3600),
                "default");
    }
}
