package com.hrishabh.codeexecutionengine;

import com.hrishabh.codeexecutionengine.dto.CodeExecutionResultDTO;
import com.hrishabh.codeexecutionengine.service.codeexecutionservice.CodeExecutorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan; // Ensure Spring finds your services

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

@SpringBootApplication
@ComponentScan(basePackages = "com.hrishabh.codeexecutionengine.service") // Ensure Spring finds your services
public class CodeExecutionEngineApplication implements CommandLineRunner {

    @Autowired
    private CodeExecutorService codeExecutionService;

    public static void main(String[] args) {
        SpringApplication.run(CodeExecutionEngineApplication.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        // --- 1. Define Fixed Test Scenario and File Paths ---
        String submissionId = "sb123"; // Using your fixed ID

        // This is the FIXED path where you already have Main.java and Solution.java
        // Adjust this path if your actual directory structure is different on your system.
        // Make sure this path is accessible by your Java application and Docker.
        Path fixedSubmissionPath = Paths.get("D:", "Java Codes", "Springboot", "Leetcode", "CodeExecutionEngine", "src", "main", "java", "com", "hrishabh", "codeexecutionengine", "temp", "submissions", "sb123");

        // The fully qualified package name for Main.java and Solution.java
        // This must EXACTLY match the 'package' declaration in those files.
        String fullyQualifiedMainClass = "com.hrishabh.codeexecutionengine.temp.submissions.sb123.Main";

        // Define expected outputs for manual comparison in this test runner
        // This simulates what your external consumer would do.
        // Ensure this maps to the test cases hardcoded in your existing Main.java
        Map<Integer, String> expectedOutputs = Map.of(
                0, "3",             // Expected output for the 'add' test case
                1, "Error Expected" // Expected for the 'divide by zero' test case
        );

        // --- 2. Execute the Code using your Service ---
        System.out.println("\n--- Initiating Code Execution for pre-existing files ---");
        CodeExecutionResultDTO result = codeExecutionService.executeCode(
                submissionId,
                fixedSubmissionPath,
                fullyQualifiedMainClass,
                "java", // Required language parameter
                System.out::println
        );


        // --- 3. Process and Display Results (as an external consumer would) ---
        System.out.println("\n--- Final Execution Result for Submission " + submissionId + " ---");
        System.out.println("Overall Status: " + result.getOverallStatus());
        System.out.println("Compilation Output:\n" + result.getCompilationOutput());

        // Iterate through the raw test case outputs and perform comparison here
        if (result.getTestCaseOutputs() != null && !result.getTestCaseOutputs().isEmpty()) {
            System.out.println("\n--- Detailed Test Case Results (Consumer's Perspective) ---");
            result.getTestCaseOutputs().forEach(tcOutput -> {
                String expected = expectedOutputs.get(tcOutput.getTestCaseIndex());
                String status = "UNKNOWN"; // Default status

                // Determine status based on the raw output from your library
                if (tcOutput.getErrorMessage() != null && !tcOutput.getErrorMessage().isEmpty()) {
                    status = "RUNTIME_ERROR";
                } else if (tcOutput.getActualOutput() != null && tcOutput.getActualOutput().equals(expected)) {
                    status = "PASSED";
                } else {
                    status = "FAILED";
                }

                System.out.printf("  Test Case %d:\n", tcOutput.getTestCaseIndex());
                System.out.printf("    Status: %s\n", status);
                System.out.printf("    Actual Output: '%s'\n", tcOutput.getActualOutput());
                System.out.printf("    Expected Output: '%s'\n", expected); // Expected output comes from this test runner
                System.out.printf("    Execution Time: %dms\n", tcOutput.getExecutionTimeMs());
                if (tcOutput.getErrorMessage() != null) {
                    System.out.printf("    Error Type: %s\n", tcOutput.getErrorType());
                    System.out.printf("    Error Message: %s\n", tcOutput.getErrorMessage());
                }
            });
        } else {
            System.out.println("No individual test case outputs were returned (e.g., due to compilation error or JVM crash).");
        }

        // No file cleanup needed as files are pre-existing and not created by this test.
    }
}