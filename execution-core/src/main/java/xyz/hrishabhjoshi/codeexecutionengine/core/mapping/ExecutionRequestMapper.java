package xyz.hrishabhjoshi.codeexecutionengine.core.mapping;

import xyz.hrishabhjoshi.codeexecutionengine.dto.CodeSubmissionDTO;
import xyz.hrishabhjoshi.codeexecutionengine.dto.ExecutionRequest;
import xyz.hrishabhjoshi.codeexecutionengine.dto.ParamInfoDTO;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class ExecutionRequestMapper {

    private ExecutionRequestMapper() {
    }

    public static CodeSubmissionDTO toSubmission(ExecutionRequest request) {
        ExecutionRequest.QuestionMetadata meta = request.getMetadata();
        if (meta == null) {
            throw new IllegalArgumentException("QuestionMetadata is required");
        }

        List<ParamInfoDTO> params = meta.getParameters() == null ? List.of() :
                meta.getParameters().stream()
                        .map(p -> new ParamInfoDTO(p.getName(), p.getType()))
                        .collect(Collectors.toList());

        String packageName = meta.getFullyQualifiedPackageName();
        if (packageName == null || packageName.isBlank()) {
            packageName = "com.algocrack.solution.submission";
        }

        CodeSubmissionDTO.QuestionMetadata questionMeta = CodeSubmissionDTO.QuestionMetadata.builder()
                .fullyQualifiedPackageName(packageName)
                .functionName(meta.getFunctionName())
                .returnType(meta.getReturnType())
                .parameters(params)
                .customDataStructureNames(meta.getCustomDataStructures())
                .questionType(meta.getQuestionType())
                .build();

        List<Map<String, Object>> testCases = new ArrayList<>();
        if (request.getTestCases() != null) {
            testCases.addAll(request.getTestCases());
        }

        return CodeSubmissionDTO.builder()
                .submissionId(request.getSubmissionId())
                .executionId(request.getExecutionId())
                .executionMode(request.getExecutionMode())
                .stdin(request.getStdin())
                .language(request.getLanguage())
                .userSolutionCode(request.getCode())
                .questionMetadata(questionMeta)
                .testCases(testCases)
                .build();
    }
}
