package xyz.hrishabhjoshi.codeexecutionengine.service.helperservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import xyz.hrishabhjoshi.codeexecutionengine.CodeExecutionManager;
import xyz.hrishabhjoshi.codeexecutionengine.dto.*;
import xyz.hrishabhjoshi.codeexecutionengine.service.utils.MemoryParser;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Worker service that processes submissions from the queue.
 * CXE is a pure execution engine - it receives code + metadata + test cases,
 * executes them, and returns results via Redis. No DB access, no judging.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExecutionWorkerService {

    private final ExecutionQueueService queueService;
    private final CodeExecutionManager codeExecutionManager;
    private final ObjectMapper objectMapper;

    @Value("${execution.worker.poll-timeout-seconds:5}")
    private long pollTimeoutSeconds;

    // Track active workers
    private static final AtomicInteger activeWorkers = new AtomicInteger(0);

    public static int getActiveWorkerCount() {
        return activeWorkers.get();
    }

    /**
     * Start a worker that continuously polls the queue.
     * Called asynchronously on application startup.
     *
     * @param workerId Unique identifier for this worker
     */
    @Async("executionWorkerExecutor")
    public void startWorker(String workerId) {
        log.info("=== [WORKER] {} starting ===", workerId);
        log.info("[WORKER] {} poll timeout={}s", workerId, pollTimeoutSeconds);
        int workerCount = activeWorkers.incrementAndGet();
        log.info("[WORKER] {} registered. Active workers now: {}", workerId, workerCount);

        int pollCount = 0;
        try {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    pollCount++;
                    if (pollCount % 10 == 1) { // Log every 10th poll to avoid spam
                        log.trace("[WORKER] {} polling (count={})", workerId, pollCount);
                    }

                    // Block waiting for next job
                    ExecutionRequest request = queueService.dequeue(pollTimeoutSeconds);

                    if (request != null) {
                        log.info("[WORKER] {} received job submissionId={}", workerId, request.getSubmissionId());
                        processSubmission(request, workerId);
                    }
                } catch (Exception e) {
                    log.error("[WORKER] {} error during poll/process: {}", workerId, e.getMessage(), e);
                    // Continue processing - don't let one error stop the worker
                }
            }
        } finally {
            int remaining = activeWorkers.decrementAndGet();
            log.info("[WORKER] {} stopped. Active workers now: {}", workerId, remaining);
        }
    }

    /**
     * Process a single submission.
     * CXE just executes and returns output - no judging, no DB writes.
     *
     * STATUS SEMANTICS (CRITICAL - Submission Service relies on this):
     * ┌─────────────────────────────────┬──────────┬─────────────────────────┐
     * │ Situation │ status │ where error lives │
     * ├─────────────────────────────────┼──────────┼─────────────────────────┤
     * │ Compilation error │ FAILED │ compilationOutput │
     * │ Missing metadata │ FAILED │ errorMessage │
     * │ Worker crash / infra error │ FAILED │ errorMessage │
     * │ Runtime exception in user code │ COMPLETED│ testCaseResults[i].error│
     * │ Timeout (per testcase) │ COMPLETED│ testCaseResults[i].error│
     * │ Successful execution │ COMPLETED│ (no error) │
     * └─────────────────────────────────┴──────────┴─────────────────────────┘
     *
     * FAILED = system failure, outputs are NOT judgeable
     * COMPLETED = user code executed (pass/fail), outputs ARE judgeable
     */
    private void processSubmission(ExecutionRequest request, String workerId) {
        String submissionId = request.getSubmissionId();
        log.info("========================================");
        log.info("=== [WORKER] {} START processing submission {} ===", workerId, submissionId);
        log.info("========================================");
        log.info("[WORKER] {} language={}, questionId={}, code length={}",
                workerId, request.getLanguage(), request.getQuestionId(),
                request.getCode() != null ? request.getCode().length() : 0);
        if (request.getMetadata() != null) {
            var meta = request.getMetadata();
            log.info("[WORKER] {} INCOMING metadata: functionName={}, returnType={}, packageName={}",
                    workerId, meta.getFunctionName(), meta.getReturnType(), meta.getFullyQualifiedPackageName());
            if (meta.getParameters() != null) {
                for (int i = 0; i < meta.getParameters().size(); i++) {
                    var p = meta.getParameters().get(i);
                    log.info("[WORKER] {} INCOMING metadata.param[{}]: name={}, type={}", workerId, i, p.getName(),
                            p.getType());
                }
            }
            log.info("[WORKER] {} INCOMING metadata.customDS={}", workerId, meta.getCustomDataStructures());
        }
        if (request.getTestCases() != null) {
            log.info("[WORKER] {} INCOMING testCases count={}", workerId, request.getTestCases().size());
            for (int i = 0; i < request.getTestCases().size(); i++) {
                log.info("[WORKER] {} INCOMING testCase[{}]: {}", workerId, i, request.getTestCases().get(i));
            }
        } else {
            log.warn("[WORKER] {} INCOMING testCases is NULL", workerId);
        }

        long startTime = System.currentTimeMillis();

        try {
            // FAIL EARLY: Metadata is required - CXE does not fetch from database
            if (request.getMetadata() == null) {
                log.error("[WORKER] {} missing metadata for {}", workerId, submissionId);
                updateStatus(submissionId, "FAILED", workerId, "Missing execution metadata", null);
                return;
            }

            // Update status to COMPILING
            log.info("[WORKER] {} updating status to COMPILING in Redis", workerId);
            updateStatus(submissionId, "COMPILING", workerId, null, null);

            // Build CodeSubmissionDTO from request
            log.info("[WORKER] {} building CodeSubmissionDTO", workerId);
            CodeSubmissionDTO codeSubmission = buildCodeSubmission(request);

            // [DEBUG_TRACE] Log built DTO details
            try {
                log.info(">>> [DEBUG_TRACE] CodeSubmissionDTO built for {}", submissionId);
                log.info(">>> [DEBUG_TRACE] Language: {}", codeSubmission.getLanguage());
                log.info(">>> [DEBUG_TRACE] UserCode length: {}",
                        codeSubmission.getUserSolutionCode() != null ? codeSubmission.getUserSolutionCode().length()
                                : 0);
                if (codeSubmission.getTestCases() != null) {
                    log.info(">>> [DEBUG_TRACE] DTO TestCases count: {}", codeSubmission.getTestCases().size());
                    if (!codeSubmission.getTestCases().isEmpty()) {
                        log.info(">>> [DEBUG_TRACE] DTO TestCase[0]: {}", codeSubmission.getTestCases().get(0));
                    }
                }
            } catch (Exception e) {
                log.error(">>> [DEBUG_TRACE] Error logging DTO details", e);
            }

            log.info("[WORKER] {} CodeSubmissionDTO built: testCases={}, functionName={}",
                    workerId,
                    codeSubmission.getTestCases() != null ? codeSubmission.getTestCases().size() : 0,
                    codeSubmission.getQuestionMetadata() != null
                            ? codeSubmission.getQuestionMetadata().getFunctionName()
                            : "null");

            // Execute code
            log.info("[WORKER] {} calling CodeExecutionManager.runCodeWithTestcases()...", workerId);
            CodeExecutionResultDTO result = codeExecutionManager.runCodeWithTestcases(
                    codeSubmission,
                    logLine -> log.info("[CXE:{}] {}", submissionId, logLine));
            log.info("[WORKER] {} execution returned: overallStatus={}, testCaseOutputs count={}",
                    workerId, result.getOverallStatus(),
                    result.getTestCaseOutputs() != null ? result.getTestCaseOutputs().size() : 0);
            if (result.getTestCaseOutputs() != null) {
                for (var tc : result.getTestCaseOutputs()) {
                    log.info(
                            "[WORKER] {} RESULT testCase[{}]: output='{}', timeMs={}, memBytes={}, error={}, errorType={}",
                            workerId, tc.getTestCaseIndex(), tc.getActualOutput(),
                            tc.getExecutionTimeMs(), tc.getMemoryBytes(),
                            tc.getErrorMessage(), tc.getErrorType());
                }
            }
            if (result.getCompilationOutput() != null && !result.getCompilationOutput().isEmpty()) {
                log.info("[WORKER] {} compilationOutput (first 500 chars): {}", workerId,
                        result.getCompilationOutput().substring(0,
                                Math.min(500, result.getCompilationOutput().length())));
            }

            // Calculate actual code runtime from test case execution times
            int actualRuntimeMs = 0;
            if (result.getTestCaseOutputs() != null) {
                actualRuntimeMs = result.getTestCaseOutputs().stream()
                        .mapToInt(tc -> (int) tc.getExecutionTimeMs())
                        .sum();
            }

            // Build test case results (no judging - just pass raw outputs)
            List<SubmissionStatusDto.TestCaseResult> testCaseResults = buildTestCaseResults(
                    result.getTestCaseOutputs());

            // Determine final status based on execution result
            // FAILED = compilation error (system cannot judge)
            // COMPLETED = code ran (even with runtime errors - these are judgeable)
            boolean isCompilationError = result.getOverallStatus() == Status.COMPILATON_ERROR;
            String finalStatus = isCompilationError ? "FAILED" : "COMPLETED";
            String errorCategory = getErrorCategory(result.getOverallStatus());

            // Update final status in Redis
            log.info("[WORKER] {} updating final status to {} in Redis", workerId, finalStatus);
            updateFinalStatus(submissionId, finalStatus, errorCategory, result, testCaseResults,
                    actualRuntimeMs, workerId);

            long wallClockTime = System.currentTimeMillis() - startTime;
            log.info("=== [WORKER] {} {} {} - runtime={}ms, total={}ms ===",
                    workerId, finalStatus, submissionId, actualRuntimeMs, wallClockTime);

        } catch (Exception e) {
            // Infrastructure/worker error - FAILED
            log.error("=== [WORKER] {} FAILED {} ===", workerId, submissionId);
            log.error("[WORKER] {} exception: {}", workerId, e.getMessage(), e);
            updateStatus(submissionId, "FAILED", workerId, e.getMessage(), null);
        }
    }

    /**
     * Build CodeSubmissionDTO from ExecutionRequest.
     * Metadata MUST be provided by the caller (SubmissionService).
     */
    private CodeSubmissionDTO buildCodeSubmission(ExecutionRequest request) {
        log.info("[BUILD_DTO] === Building CodeSubmissionDTO from ExecutionRequest ===");
        ExecutionRequest.QuestionMetadata meta = request.getMetadata();

        log.info("[BUILD_DTO] Input metadata: functionName={}, returnType={}, packageName={}",
                meta.getFunctionName(), meta.getReturnType(), meta.getFullyQualifiedPackageName());
        log.info("[BUILD_DTO] Input metadata.customDS={}", meta.getCustomDataStructures());

        // Convert parameters
        List<ParamInfoDTO> params = new ArrayList<>();
        if (meta.getParameters() != null) {
            params = meta.getParameters().stream()
                    .map(p -> new ParamInfoDTO(p.getName(), p.getType()))
                    .collect(Collectors.toList());
            log.info("[BUILD_DTO] Converted {} parameters:", params.size());
            for (int i = 0; i < params.size(); i++) {
                log.info("[BUILD_DTO]   param[{}]: name={}, type={}", i, params.get(i).getName(),
                        params.get(i).getType());
            }
        } else {
            log.warn("[BUILD_DTO] Parameters list is NULL");
        }

        // Ensure we have a valid package name
        String packageName = meta.getFullyQualifiedPackageName();
        if (packageName == null || packageName.trim().isEmpty()) {
            packageName = "com.algocrack.solution.submission";
            log.warn("[BUILD_DTO] Package name was empty, using default: {}", packageName);
        }

        CodeSubmissionDTO.QuestionMetadata questionMeta = CodeSubmissionDTO.QuestionMetadata.builder()
                .fullyQualifiedPackageName(packageName)
                .functionName(meta.getFunctionName())
                .returnType(meta.getReturnType())
                .parameters(params)
                .customDataStructureNames(meta.getCustomDataStructures())
                .questionType(meta.getQuestionType())
                .build();

        log.info(
                "[BUILD_DTO] Built QuestionMetadata: functionName={}, returnType={}, packageName={}, params={}, customDS={}, mutationTarget={}, serializationStrategy={}",
                questionMeta.getFunctionName(), questionMeta.getReturnType(),
                questionMeta.getFullyQualifiedPackageName(),
                questionMeta.getParameters() != null ? questionMeta.getParameters().size() : 0,
                questionMeta.getCustomDataStructureNames(),
                questionMeta.getMutationTarget(), questionMeta.getSerializationStrategy());

        // Use test cases as-is (no custom test case separation needed at CXE level)
        List<Map<String, Object>> allTestCases = new ArrayList<>();
        if (request.getTestCases() != null) {
            allTestCases.addAll(request.getTestCases());
        }
        log.info("[BUILD_DTO] Test cases mapped: {} total", allTestCases.size());
        for (int i = 0; i < allTestCases.size(); i++) {
            log.info("[BUILD_DTO] testCase[{}]: {}", i, allTestCases.get(i));
        }

        CodeSubmissionDTO dto = CodeSubmissionDTO.builder()
                .submissionId(request.getSubmissionId())
                .language(request.getLanguage())
                .userSolutionCode(request.getCode())
                .questionMetadata(questionMeta)
                .testCases(allTestCases)
                .build();

        log.info("[BUILD_DTO] === CodeSubmissionDTO built successfully ===");
        return dto;
    }

    /**
     * Get error category for non-success executions.
     * Returns null for success. Used for errorMessage field.
     */
    private String getErrorCategory(Status status) {
        return switch (status) {
            case SUCCESS -> null;
            case COMPILATON_ERROR -> "COMPILATION_ERROR";
            case TIMEOUT -> "TIME_LIMIT_EXCEEDED";
            case RUNTIME_ERROR -> "RUNTIME_ERROR";
            default -> "INTERNAL_ERROR";
        };
    }

    /**
     * Build test case results for status DTO.
     * Just returns raw outputs - no pass/fail judgment.
     */
    private List<SubmissionStatusDto.TestCaseResult> buildTestCaseResults(
            List<CodeExecutionResultDTO.TestCaseOutput> outputs) {
        if (outputs == null)
            return new ArrayList<>();

        return outputs.stream()
                .map(tc -> SubmissionStatusDto.TestCaseResult.builder()
                        .index(tc.getTestCaseIndex())
                        .passed(null) // CXE doesn't judge - SubmissionService does
                        .actualOutput(tc.getActualOutput())
                        .executionTimeMs(tc.getExecutionTimeMs())
                        .memoryBytes(tc.getMemoryBytes())
                        .error(tc.getErrorMessage())
                        .errorType(tc.getErrorType())
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * Update submission status in Redis.
     */
    private void updateStatus(String submissionId, String status, String workerId,
            String errorMessage, List<SubmissionStatusDto.TestCaseResult> testCaseResults) {
        SubmissionStatusDto statusDto = SubmissionStatusDto.builder()
                .submissionId(submissionId)
                .status(status)
                .workerId(workerId)
                .errorMessage(errorMessage)
                .testCaseResults(testCaseResults)
                .startedAt("COMPILING".equals(status) ? System.currentTimeMillis() : null)
                .completedAt("COMPLETED".equals(status) || "FAILED".equals(status) ? System.currentTimeMillis() : null)
                .build();
        queueService.setRedisStatus(submissionId, statusDto);
    }

    /**
     * Update final status with all results.
     * CXE sets verdict=null per oracle-based judging architecture.
     * Only SubmissionService determines verdict by comparing with oracle output.
     */
    private void updateFinalStatus(String submissionId, String status, String errorCategory,
            CodeExecutionResultDTO result,
            List<SubmissionStatusDto.TestCaseResult> testCaseResults,
            int runtimeMs, String workerId) {

        // Calculate max memory across all test cases
        Integer maxMemoryKb = null;
        if (result.getTestCaseOutputs() != null) {
            Long maxBytes = result.getTestCaseOutputs().stream()
                    .map(CodeExecutionResultDTO.TestCaseOutput::getMemoryBytes)
                    .filter(mem -> mem != null)
                    .mapToLong(Long::longValue)
                    .max()
                    .orElse(0L);

            if (maxBytes > 0) {
                maxMemoryKb = MemoryParser.bytesToKB(maxBytes);
            }
        }

        SubmissionStatusDto statusDto = SubmissionStatusDto.builder()
                .submissionId(submissionId)
                .status(status) // COMPLETED or FAILED
                .verdict(null) // ALWAYS NULL - SubmissionService determines verdict
                .runtimeMs(runtimeMs)
                .memoryKb(maxMemoryKb)
                .errorMessage(errorCategory) // Error category if execution failed
                .compilationOutput(result.getCompilationOutput())
                .testCaseResults(testCaseResults)
                .completedAt(System.currentTimeMillis())
                .workerId(workerId)
                .build();
        queueService.setRedisStatus(submissionId, statusDto);
    }
}
