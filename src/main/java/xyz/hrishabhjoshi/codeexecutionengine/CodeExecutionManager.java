package xyz.hrishabhjoshi.codeexecutionengine;

import xyz.hrishabhjoshi.codeexecutionengine.dto.CodeExecutionResultDTO;
import xyz.hrishabhjoshi.codeexecutionengine.dto.CodeSubmissionDTO;
import xyz.hrishabhjoshi.codeexecutionengine.dto.Status;
import xyz.hrishabhjoshi.codeexecutionengine.service.codeexecutionservice.CodeExecutorService;
import xyz.hrishabhjoshi.codeexecutionengine.service.factory.FileGeneratorFactory;
import xyz.hrishabhjoshi.codeexecutionengine.service.filehandlingservice.FileGenerator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.*;
import java.util.Comparator;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;

@Slf4j
@Service
public class CodeExecutionManager {

    @Autowired
    private CodeExecutorService codeExecutionService;

    @Autowired
    private FileGeneratorFactory fileGeneratorFactory;

    public CodeExecutionResultDTO runCodeWithTestcases(CodeSubmissionDTO submissionDto, Consumer<String> logConsumer) {
        String submissionId = submissionDto.getSubmissionId() != null ? submissionDto.getSubmissionId()
                : UUID.randomUUID().toString();

        log.info("[EXEC_MANAGER] === START runCodeWithTestcases for {} ===", submissionId);
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

            log.info("[EXEC_MANAGER] Getting file generator for language: {}", submissionDto.getLanguage());
            FileGenerator fileGenerator = fileGeneratorFactory.getFileGenerator(submissionDto.getLanguage());

            // Use system temp directory instead of project root to avoid leftover files
            Path systemTempDir = Paths.get(System.getProperty("java.io.tmpdir"));
            tempRootPath = Files.createTempDirectory(systemTempDir, "cxe-submission-" + submissionId);
            log.info("[EXEC_MANAGER] Created temp directory: {}", tempRootPath);

            log.info("[EXEC_MANAGER] Calling fileGenerator.generateFiles()...");
            fileGenerator.generateFiles(submissionDto, tempRootPath);
            log.info("[EXEC_MANAGER] File generation complete");

            logConsumer.accept("Files generated at: " + tempRootPath.toAbsolutePath());

            String fullyQualifiedMainClass = submissionDto.getQuestionMetadata().getFullyQualifiedPackageName()
                    + ".Main";
            log.info("[EXEC_MANAGER] Main class: {}", fullyQualifiedMainClass);

            log.info("[EXEC_MANAGER] Calling codeExecutionService.executeCode()...");
            CodeExecutionResultDTO result = codeExecutionService.executeCode(
                    submissionDto,
                    submissionId,
                    tempRootPath,
                    fullyQualifiedMainClass,
                    submissionDto.getLanguage(),
                    logConsumer);

            log.info("[EXEC_MANAGER] executeCode returned: status={}, testCaseOutputs={}",
                    result.getOverallStatus(),
                    result.getTestCaseOutputs() != null ? result.getTestCaseOutputs().size() : 0);
            log.info("[EXEC_MANAGER] === END runCodeWithTestcases for {} ===", submissionId);

            return result;

        } catch (Exception e) {
            logConsumer.accept("An error occurred: " + e.getMessage());
            log.error("Execution error for submission {}: {}", submissionId, e.getMessage(), e);
            return CodeExecutionResultDTO.builder()
                    .overallStatus(Status.INTERNAL_ERROR)
                    .compilationOutput("Error: " + e.getMessage())
                    .build();
        } finally {
            // CRITICAL: Always clean up temp files
            cleanupTempDirectory(tempRootPath, logConsumer);
        }
    }

    /**
     * Recursively delete the temp directory.
     * Logs failures but doesn't throw - cleanup should never break execution flow.
     */
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
                            // Try again with File.delete() as fallback
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
