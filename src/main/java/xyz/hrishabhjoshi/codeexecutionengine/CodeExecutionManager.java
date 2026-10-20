package xyz.hrishabhjoshi.codeexecutionengine;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import xyz.hrishabhjoshi.codeexecutionengine.core.generation.SourceWorkspaceGenerator;
import xyz.hrishabhjoshi.codeexecutionengine.dto.CodeExecutionResultDTO;
import xyz.hrishabhjoshi.codeexecutionengine.dto.CodeSubmissionDTO;
import xyz.hrishabhjoshi.codeexecutionengine.dto.Status;
import xyz.hrishabhjoshi.codeexecutionengine.service.codeexecutionservice.CodeExecutor;

import java.io.IOException;
import java.nio.file.*;
import java.util.Comparator;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class CodeExecutionManager {

    private final CodeExecutor codeExecutor;
    private final SourceWorkspaceGenerator sourceWorkspaceGenerator;

    public CodeExecutionResultDTO runCodeWithTestcases(CodeSubmissionDTO submissionDto, Consumer<String> logConsumer) {
        String submissionId = submissionDto.getSubmissionId() != null ? submissionDto.getSubmissionId()
                : UUID.randomUUID().toString();
        String executionId = submissionDto.getExecutionId() != null ? submissionDto.getExecutionId()
                : UUID.randomUUID().toString();

        log.info("[EXEC_MANAGER] === START runCodeWithTestcases submissionId={} executionId={} ===", submissionId, executionId);
        log.info("[EXEC_MANAGER] language={}, testCases={}", submissionDto.getLanguage(),
                submissionDto.getTestCases() != null ? submissionDto.getTestCases().size() : 0);
        if (submissionDto.getQuestionMetadata() != null) {
            var meta = submissionDto.getQuestionMetadata();
            log.info("[EXEC_MANAGER] metadata: functionName={}, returnType={}, packageName={}, params={}, customDS={}, mutationTarget={}, serializationStrategy={}",
                    meta.getFunctionName(), meta.getReturnType(), meta.getFullyQualifiedPackageName(),
                    meta.getParameters() != null ? meta.getParameters().size() : 0,
                    meta.getCustomDataStructureNames(),
                    meta.getMutationTarget(), meta.getSerializationStrategy());
        }

        Path tempRootPath = null;

        try {
            logConsumer.accept("Generating source files...");

            Path systemTempDir = Paths.get(System.getProperty("java.io.tmpdir"));
            tempRootPath = Files.createTempDirectory(systemTempDir, "cxe-exec-" + executionId + "-");
            log.info("[EXEC_MANAGER] Created temp directory: {}", tempRootPath);

            log.info("[EXEC_MANAGER] Calling sourceWorkspaceGenerator.generateIntoWorkspace()...");
            sourceWorkspaceGenerator.generateIntoWorkspace(submissionDto, tempRootPath);
            log.info("[EXEC_MANAGER] File generation complete");

            logConsumer.accept("Files generated at: " + tempRootPath.toAbsolutePath());

            String fullyQualifiedMainClass = submissionDto.getQuestionMetadata().getFullyQualifiedPackageName()
                    + ".Main";
            log.info("[EXEC_MANAGER] Main class: {}", fullyQualifiedMainClass);

            log.info("[EXEC_MANAGER] Calling codeExecutor.execute()...");
            CodeExecutionResultDTO result = codeExecutor.execute(
                    submissionDto,
                    submissionId,
                    executionId,
                    tempRootPath,
                    fullyQualifiedMainClass,
                    submissionDto.getLanguage(),
                    logConsumer);

            log.info("[EXEC_MANAGER] executeCode returned: status={}, testCaseOutputs={}",
                    result.getOverallStatus(),
                    result.getTestCaseOutputs() != null ? result.getTestCaseOutputs().size() : 0);
            log.info("[EXEC_MANAGER] === END runCodeWithTestcases submissionId={} executionId={} ===", submissionId, executionId);

            return result;

        } catch (Exception e) {
            logConsumer.accept("An error occurred: " + e.getMessage());
            log.error("Execution error for submission {} / {}: {}", submissionId, executionId, e.getMessage(), e);
            return CodeExecutionResultDTO.builder()
                    .submissionId(submissionId)
                    .executionId(executionId)
                    .overallStatus(Status.INTERNAL_ERROR)
                    .compilationOutput("Error: " + e.getMessage())
                    .build();
        } finally {
            cleanupTempDirectory(tempRootPath, logConsumer);
        }
    }

    private void cleanupTempDirectory(Path tempRootPath, Consumer<String> logConsumer) {
        if (tempRootPath == null || !Files.exists(tempRootPath)) {
            return;
        }

        logConsumer.accept("Cleaning up temporary files...");
        log.debug("Cleaning up temp directory: {}", tempRootPath);

        try (Stream<Path> walk = Files.walk(tempRootPath)) {
            walk.sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException e) {
                            log.warn("Failed to delete {}: {}", path, e.getMessage());
                            if (!path.toFile().delete()) {
                                log.error("Cleanup failed for: {} - may require manual cleanup", path);
                            }
                        }
                    });
            logConsumer.accept("Cleanup complete.");
            log.debug("Cleanup complete for: {}", tempRootPath);
        } catch (IOException e) {
            log.error("Error during cleanup of {}: {}", tempRootPath, e.getMessage());
            logConsumer.accept("Error during cleanup: " + e.getMessage());
        }
    }
}
