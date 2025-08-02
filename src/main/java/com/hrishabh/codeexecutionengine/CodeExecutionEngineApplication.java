package com.hrishabh.codeexecutionengine;

import com.hrishabh.codeexecutionengine.dto.CodeExecutionResultDTO;
import com.hrishabh.codeexecutionengine.dto.CodeSubmissionDTO;
import com.hrishabh.codeexecutionengine.service.codeexecutionservice.CodeExecutorService;
import com.hrishabh.codeexecutionengine.service.factory.FileGeneratorFactory;
import com.hrishabh.codeexecutionengine.service.filehandlingservice.FileGenerator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

@SpringBootApplication
public class CodeExecutionEngineApplication implements CommandLineRunner {

    @Autowired
    private CodeExecutorService codeExecutionService;

    @Autowired
    private FileGeneratorFactory fileGeneratorFactory;

    public static void main(String[] args) {
        SpringApplication.run(CodeExecutionEngineApplication.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        String submissionId = UUID.randomUUID().toString();
        Path tempRootPath = null;

        CodeSubmissionDTO.QuestionMetadata metadata = CodeSubmissionDTO.QuestionMetadata.builder()
                .fullyQualifiedPackageName("com.dynamic.submission.q" + submissionId.split("-")[0])
                .functionName("add")
                .returnType("int")
                .paramTypes(List.of("int", "int"))
                .build();

        String userSolutionCode = """
            class Solution {
                public int add(int a, int b) {
                    return a + b;
                }
            }
        """;

        List<Map<String, Object>> testCases = List.of(
                Map.of("input", Map.of("a", 1, "b", 2), "expectedOutput", 3)
        );

        CodeSubmissionDTO submissionDto = CodeSubmissionDTO.builder()
                .submissionId(submissionId)
                .language("java")
                .userSolutionCode(userSolutionCode)
                .questionMetadata(metadata)
                .testCases(testCases)
                .build();

        System.out.println("--- Starting integrated execution pipeline for submission: " + submissionId + " ---");

        try {
            System.out.println("Generating source files from CodeSubmissionDTO...");

            FileGenerator fileGenerator = fileGeneratorFactory.getFileGenerator(submissionDto.getLanguage());

            // 💡 The key change is here:
            // Get the project's root directory
            Path projectRootDir = Paths.get("").toAbsolutePath();
            // Create a temporary directory inside the project root
            tempRootPath = Files.createTempDirectory(projectRootDir, "submission-" + submissionId);

            fileGenerator.generateFiles(submissionDto, tempRootPath);

            System.out.println("Files generated at: " + tempRootPath.toAbsolutePath());

            System.out.println("\n--- Executing generated code in Docker ---");
            String fullyQualifiedMainClass = metadata.getFullyQualifiedPackageName() + ".Main";

            CodeExecutionResultDTO result = codeExecutionService.executeCode(
                    submissionDto, // 💡 Pass the CodeSubmissionDTO here
                    submissionId,
                    tempRootPath,
                    fullyQualifiedMainClass,
                    "java",
                    System.out::println
            );

            System.out.println("\n--- Final Execution Result ---");
            System.out.println("Overall Status: " + result.getOverallStatus());
            System.out.println("Compilation Output:\n" + result.getCompilationOutput());

            if (result.getTestCaseOutputs() != null && !result.getTestCaseOutputs().isEmpty()) {
                System.out.println("\n--- Raw Test Case Outputs ---");
                result.getTestCaseOutputs().forEach(tcOutput -> {
                    System.out.printf("  Test Case %d:\n", tcOutput.getTestCaseIndex());
                    System.out.printf("    Actual Output: '%s'\n", tcOutput.getActualOutput());
                    System.out.printf("    Execution Time: %dms\n", tcOutput.getExecutionTimeMs());
                    if (tcOutput.getErrorMessage() != null) {
                        System.out.printf("    Error Type: %s\n", tcOutput.getErrorType());
                        System.out.printf("    Error Message: %s\n", tcOutput.getErrorMessage());
                    }
                });
            } else {
                System.out.println("No individual test case outputs were returned.");
            }

        } catch (Exception e) {
            System.err.println("An error occurred during the execution pipeline: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (tempRootPath != null && Files.exists(tempRootPath)) {
                System.out.println("\n--- Cleaning up temporary files ---");
                try (Stream<Path> walk = Files.walk(tempRootPath)) {
                    walk.sorted(Comparator.reverseOrder())
                            .map(Path::toFile)
                            .forEach(file -> {
                                if (!file.delete()) {
                                    System.err.println("Failed to delete file: " + file);
                                }
                            });
                    System.out.println("Cleanup complete.");
                } catch (IOException e) {
                    System.err.println("Error during cleanup: " + e.getMessage());
                }
            }
        }
    }
}