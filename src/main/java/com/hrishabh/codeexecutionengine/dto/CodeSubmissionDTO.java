package com.hrishabh.codeexecutionengine.dto;


import lombok.*;

import java.util.List;
import java.util.Map;

/**
 * DTO representing a user's code submission and associated metadata.
 * This is the input for the file generation and code execution pipeline.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CodeSubmissionDTO {
    private String submissionId;
    private String language; // e.g., "java"
    private String userSolutionCode;
    private QuestionMetadata questionMetadata;
    private List<Map<String, Object>> testCases; // A list of maps, each containing "input" and "expectedOutput"

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QuestionMetadata {
        private String fullyQualifiedPackageName; // e.g., "com.hrishabh.solutions.q123"
        private String functionName;     // e.g., "addTwoNumbers"
        private String returnType;       // e.g., "int", "String", "List<Integer>"
        private List<String> paramTypes; // e.g., ["int", "int"], ["String"]
        // Add more metadata fields as needed (e.g., constraints, class name)
    }
}