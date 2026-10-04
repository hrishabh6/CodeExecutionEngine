package xyz.hrishabhjoshi.codeexecutionengine.complexityprofile.harness;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import xyz.hrishabhjoshi.codeexecutionengine.complexityprofile.dto.ComplexityProfileJobPayload;
import xyz.hrishabhjoshi.codeexecutionengine.config.ComplexityProfileExecutionProperties;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Runs profiling in a killable child JVM so non-terminating candidate code cannot permanently
 * occupy CXE profile worker threads (hard timeout via {@link Process#destroyForcibly()}).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ComplexityProfileChildJvmRunner {

    private final ObjectMapper objectMapper;
    private final ComplexityProfileExecutionProperties profileProperties;

    public ComplexityProfileJavaHarness.HarnessRunResult execute(ComplexityProfileJobPayload job) {
        Path workspace = null;
        try {
            workspace = Files.createTempDirectory("cxe-profile-child-");
            Path jobFile = workspace.resolve("job.json");
            Path resultFile = workspace.resolve("result.json");
            Path propsFile = workspace.resolve("props.json");
            objectMapper.writeValue(jobFile.toFile(), job);
            ObjectNode propsNode = (ObjectNode) objectMapper.valueToTree(profileProperties);
            ObjectNode harnessNode = propsNode.with("harness");
            harnessNode.put("useChildJvm", false);
            harnessNode.put("cooperativeInProcessTimeouts", true);
            objectMapper.writeValue(propsFile.toFile(), propsNode);

            List<String> command = new ArrayList<>();
            command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
            command.add("-cp");
            command.add(System.getProperty("java.class.path"));
            command.add(ComplexityProfileChildMain.class.getName());
            command.add(jobFile.toAbsolutePath().toString());
            command.add(resultFile.toAbsolutePath().toString());
            command.add(propsFile.toAbsolutePath().toString());

            long timeoutMs = profileProperties.getLimits().getMaxTotalProfileMs()
                    + profileProperties.getHarness().getChildJvmGraceMs();

            ProcessBuilder builder = new ProcessBuilder(command);
            builder.directory(workspace.toFile());
            builder.redirectErrorStream(true);
            Process process = builder.start();
            String output = readProcessOutput(process);
            boolean finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
            if (!finished) {
                destroyProcessTree(process);
                process.waitFor(2, TimeUnit.SECONDS);
                log.warn("[PROFILE-CHILD] executionId={} forcibly terminated after {} ms", job.getExecutionId(), timeoutMs);
                return ComplexityProfileJavaHarness.HarnessRunResult.childTimedOut();
            }
            if (Files.exists(resultFile)) {
                return objectMapper.readValue(resultFile.toFile(), ComplexityProfileJavaHarness.HarnessRunResult.class);
            }
            if (process.exitValue() != 0) {
                log.warn("[PROFILE-CHILD] executionId={} exit={} outputLen={}",
                        job.getExecutionId(), process.exitValue(), output.length());
                return ComplexityProfileJavaHarness.HarnessRunResult.internalFailed("CHILD_JVM_FAILED");
            }
            return ComplexityProfileJavaHarness.HarnessRunResult.internalFailed("CHILD_JVM_NO_RESULT");
        } catch (Exception e) {
            log.warn("[PROFILE-CHILD] executionId={} error: {}", job.getExecutionId(), e.getMessage());
            return ComplexityProfileJavaHarness.HarnessRunResult.internalFailed(e.getMessage());
        } finally {
            if (workspace != null) {
                ComplexityProfileMeasurementEngine.deleteQuietly(workspace);
            }
        }
    }

    private static String readProcessOutput(Process process) throws IOException {
        return new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    }

    private static void destroyProcessTree(Process process) {
        process.destroyForcibly();
        ProcessHandle.of(process.pid()).ifPresent(handle -> handle.descendants().forEach(ph -> ph.destroyForcibly()));
    }
}
