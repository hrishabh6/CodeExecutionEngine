package xyz.hrishabhjoshi.codeexecutionengine.complexityprofile.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ComplexityProfileKubernetesJobRunnerTest {

    @Mock
    private ComplexityProfileSandboxJobRunner sandboxJobRunner;

    @InjectMocks
    private ComplexityProfileKubernetesJobRunner runner;

    @Test
    void runOneShotDelegatesToSandboxRunner() {
        when(sandboxJobRunner.runOneShot("exec-1")).thenReturn(0);
        assertEquals(0, runner.runOneShot("exec-1"));
        verify(sandboxJobRunner).runOneShot("exec-1");
    }
}
