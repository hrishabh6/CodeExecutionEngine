package xyz.hrishabhjoshi.codeexecutionengine.dto;

import lombok.*;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CodeSubmissionDTO {

    private String submissionId;
    private String language;
    private String userSolutionCode;
    private QuestionMetadata questionMetadata;
    private List<Map<String, Object>> testCases;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QuestionMetadata {

        private String fullyQualifiedPackageName;
        private String functionName;
        private String returnType;

        // 💡 This single list replaces paramNames and paramTypes
        private List<ParamInfoDTO> parameters;

        // 💡 This map handles custom data structure class names
        private Map<String, String> customDataStructureNames;

        // For void return types: which parameter index is mutated (0-indexed)
        private String mutationTarget;

        // For void return types: how to serialize the mutated parameter
        private String serializationStrategy;

        // Question type: "ALGORITHM" (default) or "DESIGN_CLASS"
        private String questionType;
    }
}