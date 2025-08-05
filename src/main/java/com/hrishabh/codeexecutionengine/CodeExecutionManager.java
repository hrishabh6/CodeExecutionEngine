package com.hrishabh.codeexecutionengine;

import com.hrishabh.codeexecutionengine.dto.CodeExecutionResultDTO;
import com.hrishabh.codeexecutionengine.dto.CodeSubmissionDTO;
import com.hrishabh.codeexecutionengine.dto.Status;
import com.hrishabh.codeexecutionengine.service.codeexecutionservice.CodeExecutorService;
import com.hrishabh.codeexecutionengine.service.factory.FileGeneratorFactory;
import com.hrishabh.codeexecutionengine.service.filehandlingservice.FileGenerator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.util.Comparator;
import java.util.UUID;
import java.util.stream.Stream;
import java.util.function.Consumer;

@Service
public class CodeExecutionManager {

    @Autowired
    private CodeExecutorService codeExecutionService;

    @Autowired
    private FileGeneratorFactory fileGeneratorFactory;

    public CodeExecutionResultDTO runCodeWithTestcases(CodeSubmissionDTO submissionDto, Consumer<String> logConsumer) {
        String submissionId = submissionDto.getSubmissionId() != null ?
                submissionDto.getSubmissionId() : UUID.randomUUID().toString();

        Path tempRootPath = null;

        try {
            logConsumer.accept("Generating source files...");

            FileGenerator fileGenerator = fileGeneratorFactory.getFileGenerator(submissionDto.getLanguage());

            Path projectRootDir = Paths.get("").toAbsolutePath();
            tempRootPath = Files.createTempDirectory(projectRootDir, "submission-" + submissionId);

            fileGenerator.generateFiles(submissionDto, tempRootPath);

            logConsumer.accept("Files generated at: " + tempRootPath.toAbsolutePath());

            String fullyQualifiedMainClass = submissionDto.getQuestionMetadata().getFullyQualifiedPackageName() + ".Main";

            CodeExecutionResultDTO result = codeExecutionService.executeCode(
                    submissionDto,
                    submissionId,
                    tempRootPath,
                    fullyQualifiedMainClass,
                    submissionDto.getLanguage(),
                    logConsumer
            );

            return result;

        } catch (Exception e) {
            logConsumer.accept("An error occurred: " + e.getMessage());
            e.printStackTrace();
            return CodeExecutionResultDTO.builder()
                    .overallStatus(Status.INTERNAL_ERROR)
                    .compilationOutput("Error: " + e.getMessage())
                    .build();
        } finally {
//            if (tempRootPath != null && Files.exists(tempRootPath)) {
//                logConsumer.accept("Cleaning up temporary files...");
//                try (Stream<Path> walk = Files.walk(tempRootPath)) {
//                    walk.sorted(Comparator.reverseOrder())
//                            .map(Path::toFile)
//                            .forEach(file -> {
//                                if (!file.delete()) {
//                                    logConsumer.accept("Failed to delete file: " + file);
//                                }
//                            });
//                    logConsumer.accept("Cleanup complete.");
//                } catch (IOException e) {
//                    logConsumer.accept("Error during cleanup: " + e.getMessage());
//                }
//            }
        }
    }
}
