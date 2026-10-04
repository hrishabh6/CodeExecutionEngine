package xyz.hrishabhjoshi.codeexecutionengine.complexityprofile.service;

import org.springframework.stereotype.Component;
import xyz.hrishabhjoshi.codeexecutionengine.complexityprofile.dto.ComplexityProfileExecutionDtos.ProfileCaseRequest;
import xyz.hrishabhjoshi.codeexecutionengine.complexityprofile.dto.ComplexityProfileJobPayload;
import xyz.hrishabhjoshi.codeexecutionengine.complexityprofile.dto.ComplexityProfileSandboxDtos.ComplexityProfileSandboxJobPayload;
import xyz.hrishabhjoshi.codeexecutionengine.complexityprofile.dto.ComplexityProfileSandboxDtos.ComplexityProfileTrustedOracleBundle;
import xyz.hrishabhjoshi.codeexecutionengine.complexityprofile.dto.ComplexityProfileSandboxDtos.SandboxProfileCaseRequest;
import xyz.hrishabhjoshi.codeexecutionengine.complexityprofile.dto.ComplexityProfileSandboxDtos.TrustedOracleCaseExpectation;

import java.util.List;

@Component
public class ComplexityProfileSandboxPayloadFactory {

    public ComplexityProfileSandboxJobPayload toSandboxJobPayload(ComplexityProfileJobPayload job) {
        List<SandboxProfileCaseRequest> cases = job.getCases().stream()
                .map(this::stripExpectedOutput)
                .toList();
        return new ComplexityProfileSandboxJobPayload(
                job.getExecutionId(),
                job.getSubmissionId(),
                job.getQuestionId(),
                job.getLanguage(),
                job.getSourceCode(),
                job.getQuestionMetadata(),
                job.getProfileCode(),
                job.getProfileVersion(),
                job.getProfileHash(),
                job.getHarnessVersion(),
                cases);
    }

    public ComplexityProfileTrustedOracleBundle toOracleBundle(ComplexityProfileJobPayload job) {
        List<TrustedOracleCaseExpectation> expectations = job.getCases().stream()
                .map(c -> new TrustedOracleCaseExpectation(c.caseId(), c.expectedOutput()))
                .toList();
        return new ComplexityProfileTrustedOracleBundle(
                job.getExecutionId(), job.getQuestionMetadata(), expectations);
    }

    private SandboxProfileCaseRequest stripExpectedOutput(ProfileCaseRequest profileCase) {
        return new SandboxProfileCaseRequest(
                profileCase.caseId(),
                profileCase.caseIdentity(),
                profileCase.profileCode(),
                profileCase.profileVersion(),
                profileCase.profileHash(),
                profileCase.generatorVersion(),
                profileCase.variant(),
                profileCase.sizeVector(),
                profileCase.seed(),
                profileCase.input(),
                profileCase.inputHash(),
                profileCase.warmups(),
                profileCase.measuredRepeats());
    }
}
