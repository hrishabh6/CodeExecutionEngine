package xyz.hrishabhjoshi.codeexecutionengine.complexityprofile.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ComplexityProfileKubernetesJobRunner {

    private final ComplexityProfileSandboxJobRunner sandboxJobRunner;

    public int runOneShot(String executionId) {
        log.info("[PROFILE-JOB-RUNNER] sandbox executionId={}", executionId);
        return sandboxJobRunner.runOneShot(executionId);
    }
}
