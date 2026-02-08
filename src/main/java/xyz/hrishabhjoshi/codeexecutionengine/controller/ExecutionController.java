package xyz.hrishabhjoshi.codeexecutionengine.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import xyz.hrishabhjoshi.codeexecutionengine.dto.*;
import xyz.hrishabhjoshi.codeexecutionengine.service.helperservice.ExecutionQueueService;
import xyz.hrishabhjoshi.codeexecutionengine.service.helperservice.SubmissionStatusService;

import java.util.Map;

/**
 * REST API for code execution service.
 * Provides async submission with polling for results.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/execution")
@RequiredArgsConstructor
public class ExecutionController {

        private final ExecutionQueueService queueService;
        private final SubmissionStatusService statusService;

        /**
         * Submit code for execution (async).
         * Returns immediately with submission ID for polling.
         *
         * @param request     The execution request
         * @param httpRequest For capturing client info
         * @return 202 Accepted with submission details
         */
        @PostMapping("/submit")
        public ResponseEntity<ExecutionResponse> submitExecution(
                        @RequestBody ExecutionRequest request,
                        HttpServletRequest httpRequest) {
                log.info("=== [CONTROLLER] POST /submit received ===");
                log.info("[CONTROLLER] submissionId={}, userId={}, questionId={}, language={}",
                                request.getSubmissionId(), request.getUserId(), request.getQuestionId(),
                                request.getLanguage());
                log.info("[CONTROLLER] code length={}, testCases count={}",
                                request.getCode() != null ? request.getCode().length() : 0,
                                request.getTestCases() != null ? request.getTestCases().size() : 0);
                log.info("[CONTROLLER] metadata: functionName={}, returnType={}, params count={}",
                                request.getMetadata() != null ? request.getMetadata().getFunctionName() : "null",
                                request.getMetadata() != null ? request.getMetadata().getReturnType() : "null",
                                request.getMetadata() != null && request.getMetadata().getParameters() != null
                                                ? request.getMetadata().getParameters().size()
                                                : 0);

                // [DEBUG_TRACE] Log raw request details
                try {
                        log.info(">>> [DEBUG_TRACE] Raw Request: submissionId={}, language={}, questionId={}",
                                        request.getSubmissionId(), request.getLanguage(), request.getQuestionId());
                        if (request.getTestCases() != null) {
                                log.info(">>> [DEBUG_TRACE] Request TestCases count: {}",
                                                request.getTestCases().size());
                                if (!request.getTestCases().isEmpty()) {
                                        log.info(">>> [DEBUG_TRACE] First TestCase: {}", request.getTestCases().get(0));
                                }
                        } else {
                                log.info(">>> [DEBUG_TRACE] Request TestCases is NULL");
                        }
                } catch (Exception e) {
                        log.error(">>> [DEBUG_TRACE] Error logging request details", e);
                }

                // Capture client info
                request.setIpAddress(getClientIp(httpRequest));
                request.setUserAgent(httpRequest.getHeader("User-Agent"));

                // Enqueue for async processing
                log.info("[CONTROLLER] Calling queueService.enqueue()...");

                // [DEBUG_TRACE] Log enqueue intent
                log.info(">>> [DEBUG_TRACE] Enqueuing submission: {}", request.getSubmissionId());

                String submissionId = queueService.enqueue(request);
                log.info("[CONTROLLER] Enqueued successfully with submissionId={}", submissionId);

                // Get queue stats
                Long queueSize = queueService.getQueueSize();
                Integer position = queueService.getQueuePosition(submissionId);
                log.info("[CONTROLLER] Queue size={}, position={}", queueSize, position);

                // Build response
                ExecutionResponse response = ExecutionResponse.builder()
                                .submissionId(submissionId)
                                .status("QUEUED")
                                .message("Submission queued for execution")
                                .queuePosition(position)
                                .estimatedWaitTimeMs(queueService.getEstimatedWaitTime())
                                .statusUrl("/api/v1/execution/status/" + submissionId)
                                .resultsUrl("/api/v1/execution/results/" + submissionId)
                                .build();

                log.info("[CONTROLLER] Returning 202 ACCEPTED for submissionId={}", submissionId);
                return ResponseEntity
                                .status(HttpStatus.ACCEPTED)
                                .body(response);
        }

        /**
         * Get current submission status (for polling).
         *
         * @param submissionId The submission ID
         * @return Current status or 404 if not found
         */
        @GetMapping("/status/{submissionId}")
        public ResponseEntity<SubmissionStatusDto> getStatus(
                        @PathVariable String submissionId) {
                log.debug("[CONTROLLER] GET /status/{}", submissionId);
                return statusService.getStatus(submissionId)
                                .map(status -> {
                                        log.debug("[CONTROLLER] Status for {}: {}", submissionId, status.getStatus());
                                        return ResponseEntity.ok(status);
                                })
                                .orElseGet(() -> {
                                        log.warn("[CONTROLLER] Status not found for {}", submissionId);
                                        return ResponseEntity.notFound().build();
                                });
        }

        /**
         * Get full execution results.
         *
         * @param submissionId The submission ID
         * @return Full results including test case outputs
         */
        @GetMapping("/results/{submissionId}")
        public ResponseEntity<SubmissionStatusDto> getResults(
                        @PathVariable String submissionId) {
                return statusService.getFullResults(submissionId)
                                .map(ResponseEntity::ok)
                                .orElse(ResponseEntity.notFound().build());
        }

        /**
         * Health check with queue statistics.
         *
         * @return Health status and metrics
         */
        @GetMapping("/health")
        public ResponseEntity<Map<String, Object>> health() {
                Long queueSize = queueService.getQueueSize();
                int activeWorkers = statusService.getActiveWorkerCount();
                double avgTime = statusService.getAverageExecutionTime();

                log.info("[CONTROLLER] Health check: queueSize={}, activeWorkers={}, avgTime={}ms",
                                queueSize, activeWorkers, avgTime);

                return ResponseEntity.ok(Map.of(
                                "status", "UP",
                                "queueSize", queueSize,
                                "activeWorkers", activeWorkers,
                                "avgExecutionTimeMs", avgTime));
        }

        /**
         * Cancel a pending submission.
         *
         * @param submissionId The submission ID
         * @return Success or failure
         */
        @DeleteMapping("/cancel/{submissionId}")
        public ResponseEntity<Map<String, Object>> cancelSubmission(
                        @PathVariable String submissionId) {
                boolean cancelled = queueService.cancelSubmission(submissionId);

                if (cancelled) {
                        return ResponseEntity.ok(Map.of(
                                        "success", true,
                                        "message", "Submission cancelled",
                                        "submissionId", submissionId));
                } else {
                        return ResponseEntity.badRequest().body(Map.of(
                                        "success", false,
                                        "message", "Cannot cancel - submission not in queue or already processing",
                                        "submissionId", submissionId));
                }
        }

        /**
         * Extract client IP address, considering proxies.
         */
        private String getClientIp(HttpServletRequest request) {
                String xForwardedFor = request.getHeader("X-Forwarded-For");
                if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
                        return xForwardedFor.split(",")[0].trim();
                }
                return request.getRemoteAddr();
        }
}
