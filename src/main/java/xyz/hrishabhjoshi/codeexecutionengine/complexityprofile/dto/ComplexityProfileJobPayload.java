package xyz.hrishabhjoshi.codeexecutionengine.complexityprofile.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComplexityProfileJobPayload {

    private String executionId;
    private String submissionId;
    private Long questionId;
    private String language;
    private String sourceCode;
    private ComplexityProfileExecutionDtos.QuestionMetadataDto questionMetadata;
    private String profileCode;
    private String profileVersion;
    private String profileHash;
    private String harnessVersion;
    private List<ComplexityProfileExecutionDtos.ProfileCaseRequest> cases;
    private long enqueuedAtMs;
}
