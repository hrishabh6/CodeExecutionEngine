package xyz.hrishabhjoshi.codeexecutionengine.service;


import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import xyz.hrishabhjoshi.codeexecutionengine.CodeExecutionManager;
import xyz.hrishabhjoshi.codeexecutionengine.dto.*;
import xyz.hrishabhjoshi.codeexecutionengine.model.*;
import xyz.hrishabhjoshi.codeexecutionengine.repository.ExecutionMetricsRepository;
import xyz.hrishabhjoshi.codeexecutionengine.service.utils.MemoryParser;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;


/**
 * Worker service that processes submissions from the queue.
 * NOTE: CXE does NOT persist to Submission table - that's SubmissionService's responsibility.
 * CXE only tracks execution status in Redis and saves metrics.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExecutionWorkerService {

    private final ExecutionQueueService queueService;
    private final CodeExecutionManager codeExecutionManager;
    private final ExecutionMetricsRepository metricsRepository;
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
                        log.debug("[WORKER] {} polling (count={})", workerId, pollCount);
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
     */
    private void processSubmission(ExecutionRequest request, String workerId) {
        String submissionId = request.getSubmissionId();
        log.info("=== [WORKER] {} processing submission {} ===", workerId, submissionId);
        log.info("[WORKER] {} questionId={}, language={}, code length={}",
                workerId, request.getQuestionId(), request.getLanguage(),
                request.getCode() != null ? request.getCode().length() : 0);

        long startTime = System.currentTimeMillis();

        try {
            // Update status to COMPILING (Redis only)
            log.info("[WORKER] {} updating status to COMPILING in Redis", workerId);
            updateStatus(submissionId, "COMPILING", null, workerId, null, null);

            // Build CodeSubmissionDTO from request
            log.info("[WORKER] {} building CodeSubmissionDTO", workerId);
            CodeSubmissionDTO codeSubmission = buildCodeSubmission(request);
            log.info("[WORKER] {} CodeSubmissionDTO built: testCases={}, functionName={}",
                    workerId,
                    codeSubmission.getTestCases() != null ? codeSubmission.getTestCases().size() : 0,
                    codeSubmission.getQuestionMetadata() != null ? codeSubmission.getQuestionMetadata().getFunctionName() : "null");

            // Execute code
            log.info("[WORKER] {} calling CodeExecutionManager.runCodeWithTestcases()...", workerId);
            CodeExecutionResultDTO result = codeExecutionManager.runCodeWithTestcases(
                    codeSubmission,
                    logLine -> log.debug("[{}] {}", submissionId, logLine)
            );
            log.info("[WORKER] {} execution returned: overallStatus={}, testCaseOutputs count={}",
                    workerId, result.getOverallStatus(),
                    result.getTestCaseOutputs() != null ? result.getTestCaseOutputs().size() : 0);

            long executionTime = System.currentTimeMillis() - startTime;

            // Determine verdict based on execution result (only official test cases)
            SubmissionVerdict verdict = determineVerdict(result, codeSubmission);
            log.info("[WORKER] {} determined verdict={} in {}ms", workerId, verdict, executionTime);

            // Build test case results
            List<SubmissionStatusDto.TestCaseResult> testCaseResults = 
                buildTestCaseResults(result.getTestCaseOutputs(), codeSubmission);

            // Update final status (Redis only)
            log.info("[WORKER] {} updating final status to COMPLETED in Redis", workerId);
            updateFinalStatus(submissionId, verdict, result, testCaseResults, 
                            (int) executionTime, workerId);

            // Save metrics to database
            saveMetrics(submissionId, executionTime, workerId, result);

            log.info("=== [WORKER] {} COMPLETED {} - verdict={}, time={}ms ===", 
                    workerId, submissionId, verdict, executionTime);

        } catch (Exception e) {
            log.error("=== [WORKER] {} FAILED {} ===", workerId, submissionId);
            log.error("[WORKER] {} exception: {}", workerId, e.getMessage(), e);
            updateStatus(submissionId, "FAILED", "INTERNAL_ERROR", workerId, e.getMessage(), null);
        }
    }

    /**
     * Build CodeSubmissionDTO from ExecutionRequest.
     */
    private CodeSubmissionDTO buildCodeSubmission(ExecutionRequest request) {
        ExecutionRequest.QuestionMetadata meta = request.getMetadata();

        List<ParamInfoDTO> params = new ArrayList<>();
        if (meta != null && meta.getParameters() != null) {
            params = meta.getParameters().stream()
                    .map(p -> new ParamInfoDTO(p.getName(), p.getType()))
                    .collect(Collectors.toList());
        }

        CodeSubmissionDTO.QuestionMetadata questionMeta = null;
        if (meta != null) {
            questionMeta = CodeSubmissionDTO.QuestionMetadata.builder()
                    .fullyQualifiedPackageName(meta.getFullyQualifiedPackageName() != null ? 
                            meta.getFullyQualifiedPackageName() : 
                            "com.algocrack.solution.q" + request.getQuestionId())
                    .functionName(meta.getFunctionName())
                    .returnType(meta.getReturnType())
                    .parameters(params)
                    .customDataStructureNames(meta.getCustomDataStructures())
                    .build();
        }

        // Merge official + custom test cases
        List<Map<String, Object>> allTestCases = new ArrayList<>();
        if (request.getTestCases() != null) {
            // Add official test cases with isCustom = false
            for (Map<String, Object> tc : request.getTestCases()) {
                Map<String, Object> tcWithFlag = new java.util.HashMap<>(tc);
                tcWithFlag.put("isCustom", false);
                allTestCases.add(tcWithFlag);
            }
        }
        if (request.getCustomTestCases() != null) {
            // Add custom test cases with isCustom = true
            for (Map<String, Object> tc : request.getCustomTestCases()) {
                Map<String, Object> tcWithFlag = new java.util.HashMap<>(tc);
                tcWithFlag.put("isCustom", true);
                allTestCases.add(tcWithFlag);
            }
        }

        return CodeSubmissionDTO.builder()
                .submissionId(request.getSubmissionId())
                .language(request.getLanguage())
                .userSolutionCode(request.getCode())
                .questionMetadata(questionMeta)
                .testCases(allTestCases)
                .build();
    }

    /**
     * Determine verdict from execution result.
     * NOTE: This is a preliminary verdict based on execution status.
     * SubmissionService will do the final output comparison.
     * Only considers OFFICIAL test cases (ignores custom test cases).
     */
    private SubmissionVerdict determineVerdict(CodeExecutionResultDTO result, CodeSubmissionDTO submission) {
        return switch (result.getOverallStatus()) {
            case SUCCESS -> {
                // Check if any OFFICIAL test case has errors (ignore custom test cases)
                boolean hasOfficialErrors = result.getTestCaseOutputs() != null &&
                        result.getTestCaseOutputs().stream()
                                .filter(tc -> {
                                    // Get the isCustom flag from the test case
                                    Map<String, Object> testCase = submission.getTestCases().get(tc.getTestCaseIndex());
                                    Boolean isCustom = (Boolean) testCase.get("isCustom");
                                    return isCustom == null || !isCustom; // Only check official test cases
                                })
                                .anyMatch(tc -> tc.getErrorMessage() != null);
                yield hasOfficialErrors ? SubmissionVerdict.RUNTIME_ERROR : SubmissionVerdict.ACCEPTED;
            }
            case COMPILATON_ERROR -> SubmissionVerdict.COMPILATION_ERROR;
            case TIMEOUT -> SubmissionVerdict.TIME_LIMIT_EXCEEDED;
            case RUNTIME_ERROR -> SubmissionVerdict.RUNTIME_ERROR;
            default -> SubmissionVerdict.INTERNAL_ERROR;
        };
    }

    /**
     * Build test case results for status DTO.
     */
    private List<SubmissionStatusDto.TestCaseResult> buildTestCaseResults(
            List<CodeExecutionResultDTO.TestCaseOutput> outputs, CodeSubmissionDTO submission) {
        if (outputs == null) return new ArrayList<>();
        
        return outputs.stream()
                .map(tc -> {
                    // Get the isCustom flag from the test case
                    Map<String, Object> testCase = submission.getTestCases().get(tc.getTestCaseIndex());
                    Boolean isCustom = (Boolean) testCase.get("isCustom");
                    
                    return SubmissionStatusDto.TestCaseResult.builder()
                            .index(tc.getTestCaseIndex())
                            .passed(tc.getErrorMessage() == null)
                            .actualOutput(tc.getActualOutput())
                            .executionTimeMs(tc.getExecutionTimeMs())
                            .memoryBytes(tc.getMemoryBytes())
                            .error(tc.getErrorMessage())
                            .errorType(tc.getErrorType())
                            .isCustom(isCustom != null ? isCustom : false)
                            .build();
                })
                .collect(Collectors.toList());
    }

    /**
     * Update submission status in Redis only.
     * CXE does NOT update the submission table - that's SubmissionService's job.
     */
    private void updateStatus(String submissionId, String status, String verdict, 
                             String workerId, String errorMessage,
                             List<SubmissionStatusDto.TestCaseResult> testCaseResults) {
        SubmissionStatusDto statusDto = SubmissionStatusDto.builder()
                .submissionId(submissionId)
                .status(status)
                .verdict(verdict)
                .workerId(workerId)
                .errorMessage(errorMessage)
                .testCaseResults(testCaseResults)
                .startedAt("COMPILING".equals(status) ? System.currentTimeMillis() : null)
                .completedAt("COMPLETED".equals(status) || "FAILED".equals(status) ? System.currentTimeMillis() : null)
                .build();
        queueService.setRedisStatus(submissionId, statusDto);
    }

    /**
     * Update final status with all results (Redis only).
     */
    private void updateFinalStatus(String submissionId, SubmissionVerdict verdict,
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
                .status("COMPLETED")
                .verdict(verdict.name())
                .runtimeMs(runtimeMs)
                .memoryKb(maxMemoryKb)
                .compilationOutput(result.getCompilationOutput())
                .testCaseResults(testCaseResults)
                .completedAt(System.currentTimeMillis())
                .workerId(workerId)
                .build();
        queueService.setRedisStatus(submissionId, statusDto);
    }

    /**
     * Save execution metrics to database.
     * This is CXE's own table, not shared with SubmissionService.
     */
    private void saveMetrics(String submissionId, long executionTime,
                            String workerId, CodeExecutionResultDTO result) {
        try {
            ExecutionMetrics metrics = ExecutionMetrics.builder()
                    .submissionId(submissionId)
                    .executionMs((int) executionTime)
                    .totalMs((int) executionTime)
                    .workerId(workerId)
                    .usedCache(false)
                    .build();
            metricsRepository.save(metrics);
        } catch (Exception e) {
            log.error("Failed to save metrics for {}: {}", submissionId, e.getMessage());
            // Non-fatal - don't fail the submission
        }
    }
}

