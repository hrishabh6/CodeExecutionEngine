package xyz.hrishabhjoshi.codeexecutionengine.service.codeexecutionservice;

import xyz.hrishabhjoshi.codeexecutionengine.dto.CodeExecutionResultDTO;
import xyz.hrishabhjoshi.codeexecutionengine.dto.CodeSubmissionDTO;
import xyz.hrishabhjoshi.codeexecutionengine.dto.CompilationResult;
import xyz.hrishabhjoshi.codeexecutionengine.dto.ExecutionResult;
import xyz.hrishabhjoshi.codeexecutionengine.dto.Status;
import xyz.hrishabhjoshi.codeexecutionengine.service.compilation.CompilationService;
import xyz.hrishabhjoshi.codeexecutionengine.service.execution.ExecutionService;
import xyz.hrishabhjoshi.codeexecutionengine.service.factory.CompilationServiceFactory;
import xyz.hrishabhjoshi.codeexecutionengine.service.factory.ExecutionServiceFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@Service
public class CodeExecutorService {

    private final CompilationServiceFactory compilationFactory;
    private final ExecutionServiceFactory executionFactory;

    public CodeExecutorService(
            CompilationServiceFactory compilationFactory,
            ExecutionServiceFactory executionFactory
    ) {
        this.compilationFactory = compilationFactory;
        this.executionFactory = executionFactory;
    }

    public CodeExecutionResultDTO executeCode(
            CodeSubmissionDTO submissionDto,
            String submissionId,
            Path submissionRootPath,
            String fullyQualifiedMainClass,
            String language,
            Consumer<String> logConsumer) {

        // Validate submission path
        File submissionDir = submissionRootPath.toFile();
        if (!submissionDir.exists() || !submissionDir.isDirectory()) {
            return CodeExecutionResultDTO.builder()
                    .submissionId(submissionId)
                    .overallStatus(Status.INTERNAL_ERROR)
                    .compilationOutput("Submission directory not found or not a directory: " + submissionRootPath)
                    .testCaseOutputs(List.of())
                    .build();
        }

        String overallCompilationOutput = "";
        CompilationService compilationService = compilationFactory.getService(language);
        ExecutionService executionService = executionFactory.getService(language);

        List<CodeExecutionResultDTO.TestCaseOutput> finalTestCaseOutputs = new ArrayList<>();

        try {
            // --- Step 1: Compile Code ---
            String fullyQualifiedPackageName = submissionDto.getQuestionMetadata().getFullyQualifiedPackageName();

            CompilationResult compileResult = compilationService.compile(
                    submissionId,
                    fullyQualifiedPackageName,
                    submissionRootPath,
                    logConsumer
            );

            overallCompilationOutput = compileResult.getOutput();

            if (!compileResult.isSuccess()) {
                return CodeExecutionResultDTO.builder()
                        .submissionId(submissionId)
                        .overallStatus(Status.COMPILATON_ERROR)
                        .compilationOutput(overallCompilationOutput)
                        .testCaseOutputs(List.of())
                        .build();
            }

            // --- Step 2: Execute Code ---
            ExecutionResult runResult = executionService.run(submissionId, submissionRootPath, fullyQualifiedMainClass, logConsumer);

            Status overallStatus;
            if (runResult.isTimedOut()) {
                overallStatus = Status.TIMEOUT;
            } else if (runResult.getExitCode() != 0) {
                overallStatus = Status.RUNTIME_ERROR;
            } else {
                overallStatus = Status.SUCCESS;
            }

            // ✅ FIX: Map memoryBytes from ExecutionResult to CodeExecutionResultDTO
            for (ExecutionResult.TestCaseOutput tcOutput : runResult.getTestCaseOutputs()) {
                finalTestCaseOutputs.add(CodeExecutionResultDTO.TestCaseOutput.builder()
                        .testCaseIndex(tcOutput.getTestCaseIndex())
                        .actualOutput(tcOutput.getActualOutput())
                        .executionTimeMs(tcOutput.getExecutionTimeMs())
                        .errorMessage(tcOutput.getErrorMessage())
                        .errorType(tcOutput.getErrorType())
                        .memoryBytes(tcOutput.getMemoryBytes()) // ✅ CRITICAL FIX: Add this line!
                        .build());
            }

            String combinedOutput = overallCompilationOutput + "\n" + runResult.getRawOutput();

            return CodeExecutionResultDTO.builder()
                    .submissionId(submissionId)
                    .overallStatus(overallStatus)
                    .compilationOutput(combinedOutput)
                    .testCaseOutputs(finalTestCaseOutputs)
                    .build();

        } catch (IOException | InterruptedException e) {
            logConsumer.accept("ORCHESTRATION_SERVICE_ERROR: Internal error during code execution: " + e.getMessage());
            e.printStackTrace();
            return CodeExecutionResultDTO.builder()
                    .submissionId(submissionId)
                    .overallStatus(Status.INTERNAL_ERROR)
                    .compilationOutput(overallCompilationOutput + "\n" + "Orchestration/Docker communication failed: " + e.getMessage())
                    .testCaseOutputs(List.of())
                    .build();
        }
    }
}