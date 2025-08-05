package com.hrishabh.codeexecutionengine.dto;

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
    }
}