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
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@Slf4j
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

        log.info("[CODE_EXECUTOR] === START executeCode for {} ===", submissionId);
        log.info("[CODE_EXECUTOR] language={}, submissionPath={}, mainClass={}",
                language, submissionRootPath, fullyQualifiedMainClass);

        // Validate submission path
        File submissionDir = submissionRootPath.toFile();
        if (!submissionDir.exists() || !submissionDir.isDirectory()) {
            log.error("[CODE_EXECUTOR] Submission directory not found: {}", submissionRootPath);
            return CodeExecutionResultDTO.builder()
                    .submissionId(submissionId)
                    .overallStatus(Status.INTERNAL_ERROR)
                    .compilationOutput("Submission directory not found or not a directory: " + submissionRootPath)
                    .testCaseOutputs(List.of())
                    .build();
        }
        log.info("[CODE_EXECUTOR] Submission directory validated: {}", submissionRootPath);

        String overallCompilationOutput = "";
        CompilationService compilationService = compilationFactory.getService(language);
        ExecutionService executionService = executionFactory.getService(language);
        log.info("[CODE_EXECUTOR] Using compilationService={}, executionService={}",
                compilationService.getClass().getSimpleName(), executionService.getClass().getSimpleName());

        List<CodeExecutionResultDTO.TestCaseOutput> finalTestCaseOutputs = new ArrayList<>();

        try {
            // --- Step 1: Compile Code ---
            String fullyQualifiedPackageName = submissionDto.getQuestionMetadata().getFullyQualifiedPackageName();
            log.info("[CODE_EXECUTOR] === STEP 1: COMPILATION === package={}", fullyQualifiedPackageName);

            CompilationResult compileResult = compilationService.compile(
                    submissionId,
                    fullyQualifiedPackageName,
                    submissionRootPath,
                    logConsumer
            );

            overallCompilationOutput = compileResult.getOutput();
            log.info("[CODE_EXECUTOR] Compilation result: success={}, output length={}",
                    compileResult.isSuccess(), overallCompilationOutput != null ? overallCompilationOutput.length() : 0);

            if (!compileResult.isSuccess()) {
                log.error("[CODE_EXECUTOR] COMPILATION FAILED! Output:\n{}", overallCompilationOutput);
                return CodeExecutionResultDTO.builder()
                        .submissionId(submissionId)
                        .overallStatus(Status.COMPILATON_ERROR)
                        .compilationOutput(overallCompilationOutput)
                        .testCaseOutputs(List.of())
                        .build();
            }
            log.info("[CODE_EXECUTOR] Compilation PASSED");

            // --- Step 2: Execute Code ---
            log.info("[CODE_EXECUTOR] === STEP 2: EXECUTION === mainClass={}", fullyQualifiedMainClass);
            ExecutionResult runResult = executionService.run(submissionId, submissionRootPath, fullyQualifiedMainClass, logConsumer);
            log.info("[CODE_EXECUTOR] Execution complete: timedOut={}, exitCode={}, testCaseOutputs={}",
                    runResult.isTimedOut(), runResult.getExitCode(),
                    runResult.getTestCaseOutputs() != null ? runResult.getTestCaseOutputs().size() : 0);

            Status overallStatus;
            if (runResult.isTimedOut()) {
                overallStatus = Status.TIMEOUT;
            } else if (runResult.getExitCode() != 0) {
                overallStatus = Status.RUNTIME_ERROR;
            } else {
                overallStatus = Status.SUCCESS;
            }
            log.info("[CODE_EXECUTOR] Determined overallStatus={}", overallStatus);

            // Map test case outputs
            log.info("[CODE_EXECUTOR] === STEP 3: MAPPING TEST CASE RESULTS ===");
            for (ExecutionResult.TestCaseOutput tcOutput : runResult.getTestCaseOutputs()) {
                log.info("[CODE_EXECUTOR] Mapping testCase[{}]: output='{}', timeMs={}, memBytes={}, error={}",
                        tcOutput.getTestCaseIndex(), tcOutput.getActualOutput(),
                        tcOutput.getExecutionTimeMs(), tcOutput.getMemoryBytes(), tcOutput.getErrorMessage());
                finalTestCaseOutputs.add(CodeExecutionResultDTO.TestCaseOutput.builder()
                        .testCaseIndex(tcOutput.getTestCaseIndex())
                        .actualOutput(tcOutput.getActualOutput())
                        .executionTimeMs(tcOutput.getExecutionTimeMs())
                        .errorMessage(tcOutput.getErrorMessage())
                        .errorType(tcOutput.getErrorType())
                        .memoryBytes(tcOutput.getMemoryBytes())
                        .build());
            }

            String combinedOutput = overallCompilationOutput + "\n" + runResult.getRawOutput();
            log.info("[CODE_EXECUTOR] === END executeCode for {} === status={}", submissionId, overallStatus);

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