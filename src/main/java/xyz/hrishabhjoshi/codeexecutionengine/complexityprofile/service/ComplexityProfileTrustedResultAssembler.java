package xyz.hrishabhjoshi.codeexecutionengine.complexityprofile.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import xyz.hrishabhjoshi.codeexecutionengine.complexityprofile.dto.ComplexityProfileExecutionDtos.ComplexityProfilePollResponse;
import xyz.hrishabhjoshi.codeexecutionengine.complexityprofile.dto.ComplexityProfileExecutionDtos.ProfileCaseResult;
import xyz.hrishabhjoshi.codeexecutionengine.complexityprofile.dto.ComplexityProfileExecutionDtos.QuestionMetadataDto;
import xyz.hrishabhjoshi.codeexecutionengine.complexityprofile.dto.ComplexityProfileSandboxDtos.ComplexityProfileSandboxMeasurementResult;
import xyz.hrishabhjoshi.codeexecutionengine.complexityprofile.dto.ComplexityProfileSandboxDtos.ComplexityProfileTrustedOracleBundle;
import xyz.hrishabhjoshi.codeexecutionengine.complexityprofile.dto.ComplexityProfileSandboxDtos.SandboxCaseMeasurement;
import xyz.hrishabhjoshi.codeexecutionengine.complexityprofile.dto.ComplexityProfileSandboxDtos.TrustedOracleCaseExpectation;
import xyz.hrishabhjoshi.codeexecutionengine.complexityprofile.harness.ComplexityProfileOutputValidator;
import xyz.hrishabhjoshi.codeexecutionengine.complexityprofile.support.ComplexityProfileEnvironmentFingerprint;
import xyz.hrishabhjoshi.codeexecutionengine.config.ComplexityProfileExecutionProperties;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class ComplexityProfileTrustedResultAssembler {

    private final ObjectMapper objectMapper;
    private final ComplexityProfileExecutionProperties properties;
    private final ComplexityProfileOutputValidator outputValidator;

    public ComplexityProfileTrustedResultAssembler(
            ObjectMapper objectMapper,
            ComplexityProfileExecutionProperties properties) {
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.outputValidator = new ComplexityProfileOutputValidator(objectMapper);
    }

    public ComplexityProfilePollResponse assemble(
            ComplexityProfileSandboxMeasurementResult measurement,
            ComplexityProfileTrustedOracleBundle oracle) {
        QuestionMetadataDto metadata = oracle.questionMetadata();
        Map<String, TrustedOracleCaseExpectation> expectedByCaseId = oracle.cases().stream()
                .collect(Collectors.toMap(TrustedOracleCaseExpectation::caseId, e -> e, (a, b) -> a));
        List<ProfileCaseResult> trustedCases = new ArrayList<>();
        for (SandboxCaseMeasurement sandboxCase : measurement.cases()) {
            trustedCases.add(toTrustedCase(sandboxCase, expectedByCaseId.get(sandboxCase.caseId()), metadata));
        }
        String compileStatus = measurement.compileStatus() == null ? "FAILED" : measurement.compileStatus();
        return new ComplexityProfilePollResponse(
                measurement.executionId(),
                "COMPLETED",
                compileStatus,
                System.getProperty("java.version"),
                properties.getHarnessVersion(),
                properties.getMeasurementPolicyVersion(),
                "cxe-kubernetes-job-v1",
                ComplexityProfileEnvironmentFingerprint.build(properties),
                trustedCases,
                compileStatus.equals("SUCCESS") ? null : "COMPILE_FAILED",
                measurement.errorMessage());
    }

    private ProfileCaseResult toTrustedCase(
            SandboxCaseMeasurement sandboxCase,
            TrustedOracleCaseExpectation oracleCase,
            QuestionMetadataDto metadata) {
        if (!"SUCCESS".equals(sandboxCase.outcome()) || oracleCase == null) {
            return new ProfileCaseResult(
                    sandboxCase.caseId(),
                    sandboxCase.caseIdentity(),
                    sandboxCase.profileCode(),
                    sandboxCase.profileVersion(),
                    sandboxCase.profileHash(),
                    sandboxCase.generatorVersion(),
                    sandboxCase.variant(),
                    sandboxCase.sizeVector(),
                    sandboxCase.outcome(),
                    false,
                    sandboxCase.warmupCount(),
                    sandboxCase.sampleCount(),
                    sandboxCase.medianElapsedNs(),
                    sandboxCase.madElapsedNs(),
                    sandboxCase.minElapsedNs(),
                    sandboxCase.maxElapsedNs(),
                    sandboxCase.errorCode());
        }
        boolean validated = sandboxCase.actualOutput() != null
                && outputValidator.matchesExpected(
                        sandboxCase.actualOutput(),
                        oracleCase.expectedOutput(),
                        metadata);
        return new ProfileCaseResult(
                sandboxCase.caseId(),
                sandboxCase.caseIdentity(),
                sandboxCase.profileCode(),
                sandboxCase.profileVersion(),
                sandboxCase.profileHash(),
                sandboxCase.generatorVersion(),
                sandboxCase.variant(),
                sandboxCase.sizeVector(),
                validated ? "SUCCESS" : "OUTPUT_MISMATCH",
                validated,
                sandboxCase.warmupCount(),
                sandboxCase.sampleCount(),
                sandboxCase.medianElapsedNs(),
                sandboxCase.madElapsedNs(),
                sandboxCase.minElapsedNs(),
                sandboxCase.maxElapsedNs(),
                validated ? null : "OUTPUT_MISMATCH");
    }
}
