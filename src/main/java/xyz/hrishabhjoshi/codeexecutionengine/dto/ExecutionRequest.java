package xyz.hrishabhjoshi.codeexecutionengine.dto;

import lombok.*;
import java.util.List;
import java.util.Map;

/**
 * Request DTO for code execution submission.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecutionRequest {

    /**
     * Optional submission ID (will be generated if null)
     */
    private String submissionId;

    /**
     * User who is submitting the code
     */
    private Long userId;

    /**
     * Question being solved
     */
    private Long questionId;

    /**
     * Programming language (java, python)
     */
    private String language;

    /**
     * User's solution code
     */
    private String code;

    /**
     * Question metadata for code generation
     */
    private QuestionMetadata metadata;

    /**
     * Test cases to run against
     */
    private List<Map<String, Object>> testCases;

    /**
     * Client IP address (set by server)
     */
    private String ipAddress;

    /**
     * Client user agent (set by server)
     */
    private String userAgent;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QuestionMetadata {
        private String fullyQualifiedPackageName;
        private String functionName;
        private String returnType;
        private List<Parameter> parameters;
        private Map<String, String> customDataStructures;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Parameter {
        private String name;
        private String type;
    }
}
