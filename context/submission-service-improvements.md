# Submission Service - Improvement Plan

## Overview

With the **CodeExecutionEngine now a standalone microservice**, the SubmissionService needs to be refactored to:

1. **Communicate via HTTP** instead of Maven dependency
2. **Focus on its core responsibilities** (user interaction, result validation)
3. **Remove execution logic** (no longer its concern)
4. **Add new entities** for tracking and analytics

---

## Current vs Target Architecture

### Current State (From context.md)

```
┌─────────────────────────────────────────────────────────┐
│                   Submission Service                     │
│                                                         │
│  ┌──────────────────┐                                   │
│  │ SubmissionController │──▶ POST /submit               │
│  └──────────────────┘                                   │
│           │                                             │
│           ▼                                             │
│  ┌──────────────────┐                                   │
│  │ SubmissionProducer│──▶ Kafka "submission-queue"      │
│  └──────────────────┘                                   │
│           │                                             │
│           ▼                                             │
│  ┌──────────────────┐    ┌────────────────────────┐    │
│  │ SubmissionConsumer│──▶│ CodeRunnerService      │    │
│  │ (BLOCKS 10s)      │    │  - Fetches metadata    │    │
│  └──────────────────┘    │  - Fetches test cases  │    │
│                          │  - Calls CXE (library) │    │
│                          │  - Logs to Kafka       │    │
│                          └────────────────────────┘    │
│                                                         │
│  Issues:                                               │
│  ❌ Kafka consumer blocked during execution             │
│  ❌ No result persistence                               │
│  ❌ No polling support                                  │
│  ❌ Mixes concerns (execution vs user management)       │
└─────────────────────────────────────────────────────────┘
```

### Target State

```
┌─────────────────────────────────────────────────────────┐
│                   Submission Service                     │
│                   (Port 8080)                           │
│                                                         │
│  ┌──────────────────────────────────────────────────┐   │
│  │ SubmissionController                              │   │
│  │   POST /api/v1/submissions                       │   │
│  │   GET  /api/v1/submissions/{id}                  │   │
│  │   GET  /api/v1/submissions/user/{userId}         │   │
│  └──────────────────────────────────────────────────┘   │
│           │                                             │
│           ▼                                             │
│  ┌──────────────────────────────────────────────────┐   │
│  │ SubmissionService                                 │   │
│  │   - Validate user & rate limit                   │   │
│  │   - Create submission record (PENDING)           │   │
│  │   - Call CXE via HTTP (async)                    │   │
│  │   - Poll CXE for results                         │   │
│  │   - Compare actual vs expected outputs           │   │
│  │   - Update submission verdict                    │   │
│  │   - Update question statistics                   │   │
│  └──────────────────────────────────────────────────┘   │
│           │                                             │
│           ▼                                             │
│  ┌──────────────────────────────────────────────────┐   │
│  │ WebSocket Service                                 │   │
│  │   - Push real-time status updates to client      │   │
│  └──────────────────────────────────────────────────┘   │
│                                                         │
│  Responsibilities:                                     │
│  ✅ User authentication & authorization                │
│  ✅ Rate limiting per user                             │
│  ✅ Submission history & persistence                   │
│  ✅ Result validation (compare outputs)                │
│  ✅ Question statistics (acceptance rate, avg runtime) │
│  ✅ Real-time updates via WebSocket                    │
└─────────────────────────────────────────────────────────┘
          │
          │ HTTP
          ▼
┌─────────────────────────────────────────────────────────┐
│               Code Execution Service                     │
│               (Port 8081)                               │
│                                                         │
│  POST /api/v1/execution/submit                         │
│  GET  /api/v1/execution/status/{id}                    │
│  GET  /api/v1/execution/results/{id}                   │
│                                                         │
│  Responsibilities:                                     │
│  ✅ Code execution in Docker containers                │
│  ✅ Output capture (actual results)                    │
│  ✅ Queue management                                   │
│  ✅ Worker pool scaling                                │
└─────────────────────────────────────────────────────────┘
```

---

## Key Changes Required

### 1. Remove Direct CXE Dependency

**Remove from build.gradle:**
```groovy
// DELETE THIS
implementation 'xyz.hrishabhjoshi:CodeExecutionEngine:1.0.2'

// Also remove ComponentScan for CXE package
```

**Remove ComponentScan:**
```java
// BEFORE
@ComponentScan(basePackages = {
    "com.hrishabh.algocracksubmissionservice",
    "com.hrishabh.codeexecutionengine"  // ❌ Remove
})

// AFTER
@ComponentScan(basePackages = {
    "com.hrishabh.algocracksubmissionservice"
})
```

---

### 2. Add HTTP Client for CXE

**New Service: `CodeExecutionClientService.java`**

```java
@Service
@RequiredArgsConstructor
public class CodeExecutionClientService {

    private final RestTemplate restTemplate;

    @Value("${cxe.service.url:http://localhost:8081}")
    private String cxeServiceUrl;

    /**
     * Submit code for execution (async, returns immediately)
     */
    public ExecutionResponse submitCode(ExecutionRequest request) {
        return restTemplate.postForObject(
            cxeServiceUrl + "/api/v1/execution/submit",
            request,
            ExecutionResponse.class
        );
    }

    /**
     * Poll for execution status
     */
    public SubmissionStatusDto getStatus(String submissionId) {
        return restTemplate.getForObject(
            cxeServiceUrl + "/api/v1/execution/status/" + submissionId,
            SubmissionStatusDto.class
        );
    }

    /**
     * Get full results
     */
    public SubmissionStatusDto getResults(String submissionId) {
        return restTemplate.getForObject(
            cxeServiceUrl + "/api/v1/execution/results/" + submissionId,
            SubmissionStatusDto.class
        );
    }
}
```

---

### 3. Add Result Validation Logic

The **SubmissionService is now responsible for comparing actual vs expected outputs**.

**New Service: `ResultValidationService.java`**

```java
@Service
@RequiredArgsConstructor
public class ResultValidationService {

    private final ObjectMapper objectMapper;

    /**
     * Compare actual output with expected output for all test cases.
     * Returns final verdict.
     */
    public SubmissionVerdict validateResults(
        List<TestCaseResult> actualResults,
        List<TestCase> expectedTestCases
    ) {
        if (actualResults == null || actualResults.isEmpty()) {
            return SubmissionVerdict.INTERNAL_ERROR;
        }

        for (int i = 0; i < actualResults.size(); i++) {
            TestCaseResult actual = actualResults.get(i);
            TestCase expected = expectedTestCases.get(i);

            // Check for runtime errors
            if (actual.getError() != null) {
                return SubmissionVerdict.RUNTIME_ERROR;
            }

            // Compare outputs (normalize JSON)
            if (!outputsMatch(actual.getActualOutput(), expected.getExpectedOutput())) {
                return SubmissionVerdict.WRONG_ANSWER;
            }
        }

        return SubmissionVerdict.ACCEPTED;
    }

    private boolean outputsMatch(String actual, String expected) {
        try {
            // Parse both as JSON and compare
            JsonNode actualNode = objectMapper.readTree(actual);
            JsonNode expectedNode = objectMapper.readTree(expected);
            return actualNode.equals(expectedNode);
        } catch (Exception e) {
            // Fallback to string comparison (trimmed)
            return actual.trim().equals(expected.trim());
        }
    }
}
```

---

### 4. Add New Entities (from entity-improvement-plan.md)

**Submission Entity** (add to EntityService)

```java
@Entity
@Table(name = "submission")
public class Submission extends BaseModel {
    
    @Column(unique = true, nullable = false)
    private String submissionId;  // UUID
    
    @ManyToOne(fetch = FetchType.LAZY)
    private User user;
    
    @ManyToOne(fetch = FetchType.LAZY)
    private Question question;
    
    @Column(nullable = false)
    private String language;
    
    @Column(columnDefinition = "TEXT")
    private String code;
    
    @Enumerated(EnumType.STRING)
    private SubmissionStatus status;  // PENDING, EXECUTING, COMPLETED, FAILED
    
    @Enumerated(EnumType.STRING)
    private SubmissionVerdict verdict;  // ACCEPTED, WRONG_ANSWER, TLE, etc.
    
    private Integer runtimeMs;
    private Integer memoryKb;
    
    @Column(columnDefinition = "JSON")
    private String testCaseResults;  // JSON array of results
    
    private LocalDateTime submittedAt;
    private LocalDateTime completedAt;
    
    // Calculated fields
    private Integer passedTestCases;
    private Integer totalTestCases;
}
```

**QuestionStatistics Entity** (add to EntityService)

```java
@Entity
@Table(name = "question_statistics")
public class QuestionStatistics extends BaseModel {
    
    @OneToOne(fetch = FetchType.LAZY)
    private Question question;
    
    private Integer totalSubmissions = 0;
    private Integer acceptedSubmissions = 0;
    
    private Integer avgRuntimeMs;
    private Integer avgMemoryKb;
    
    private LocalDateTime lastSubmissionAt;
    
    // Calculated
    public Double getAcceptanceRate() {
        if (totalSubmissions == 0) return 0.0;
        return (acceptedSubmissions * 100.0) / totalSubmissions;
    }
}
```

---

### 5. Refactor CodeRunnerService

**Rename to `SubmissionProcessingService.java`**

```java
@Service
@RequiredArgsConstructor
public class SubmissionProcessingService {

    private final CodeExecutionClientService cxeClient;
    private final ResultValidationService validationService;
    private final SubmissionRepository submissionRepository;
    private final QuestionStatisticsRepository statsRepository;
    private final QuestionService questionService;
    private final WebSocketService webSocketService;

    /**
     * Process submission asynchronously
     */
    @Async
    public void processSubmission(Submission submission) {
        try {
            // 1. Update status to EXECUTING
            submission.setStatus(SubmissionStatus.EXECUTING);
            submissionRepository.save(submission);
            webSocketService.sendStatus(submission);

            // 2. Build execution request
            ExecutionRequest request = buildExecutionRequest(submission);

            // 3. Submit to CXE (async)
            ExecutionResponse response = cxeClient.submitCode(request);
            String cxeSubmissionId = response.getSubmissionId();

            // 4. Poll for results
            SubmissionStatusDto result = pollForCompletion(cxeSubmissionId);

            // 5. Validate results (compare actual vs expected)
            List<TestCase> expectedTestCases = questionService.getTestCases(submission.getQuestion().getId());
            SubmissionVerdict verdict = validationService.validateResults(
                result.getTestCaseResults(),
                expectedTestCases
            );

            // 6. Update submission
            submission.setStatus(SubmissionStatus.COMPLETED);
            submission.setVerdict(verdict);
            submission.setRuntimeMs(result.getRuntimeMs());
            submission.setCompletedAt(LocalDateTime.now());
            submission.setPassedTestCases(countPassed(result.getTestCaseResults()));
            submission.setTotalTestCases(expectedTestCases.size());
            submissionRepository.save(submission);

            // 7. Update question statistics
            updateQuestionStatistics(submission);

            // 8. Notify client
            webSocketService.sendResult(submission);

        } catch (Exception e) {
            submission.setStatus(SubmissionStatus.FAILED);
            submission.setVerdict(SubmissionVerdict.INTERNAL_ERROR);
            submissionRepository.save(submission);
            webSocketService.sendError(submission, e.getMessage());
        }
    }

    private SubmissionStatusDto pollForCompletion(String submissionId) {
        int maxAttempts = 60;  // 30 seconds max
        for (int i = 0; i < maxAttempts; i++) {
            SubmissionStatusDto status = cxeClient.getStatus(submissionId);
            if ("COMPLETED".equals(status.getStatus()) || "FAILED".equals(status.getStatus())) {
                return cxeClient.getResults(submissionId);
            }
            try {
                Thread.sleep(500);  // Poll every 500ms
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Polling interrupted");
            }
        }
        throw new RuntimeException("Execution timeout");
    }
}
```

---

### 6. Update REST API

**Refactored SubmissionController**

```java
@RestController
@RequestMapping("/api/v1/submissions")
@RequiredArgsConstructor
public class SubmissionController {

    private final SubmissionService submissionService;

    /**
     * Submit code for execution
     */
    @PostMapping
    public ResponseEntity<SubmissionResponseDto> submit(
        @RequestBody SubmissionRequestDto request,
        @AuthenticationPrincipal User user
    ) {
        Submission submission = submissionService.createAndProcess(request, user);
        
        return ResponseEntity.accepted().body(SubmissionResponseDto.builder()
            .submissionId(submission.getSubmissionId())
            .status(submission.getStatus().name())
            .message("Submission queued for processing")
            .build());
    }

    /**
     * Get submission status/result
     */
    @GetMapping("/{submissionId}")
    public ResponseEntity<SubmissionDetailDto> getSubmission(
        @PathVariable String submissionId
    ) {
        return submissionService.getBySubmissionId(submissionId)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get user's submission history
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<SubmissionSummaryDto>> getUserSubmissions(
        @PathVariable Long userId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(
            submissionService.getUserSubmissions(userId, page, size)
        );
    }

    /**
     * Get submissions for a question
     */
    @GetMapping("/question/{questionId}")
    public ResponseEntity<QuestionSubmissionsDto> getQuestionSubmissions(
        @PathVariable Long questionId
    ) {
        return ResponseEntity.ok(
            submissionService.getQuestionSubmissions(questionId)
        );
    }
}
```

---

### 7. Remove Kafka (Optional)

Since CXE now handles async processing, Kafka is no longer needed for submission queuing.

**Remove:**
- `SubmissionProducer.java`
- `SubmissionConsumer.java`
- `LogsProducer.java`
- `LogsConsumer.java`
- `KafkaConfiguration.java`

**Keep Kafka only for:**
- Real-time log streaming to WebSocket (optional)
- Event sourcing (if needed later)

---

### 8. Add WebSocket for Real-time Updates

```java
@Service
@RequiredArgsConstructor
public class WebSocketService {

    private final SimpMessagingTemplate messagingTemplate;

    public void sendStatus(Submission submission) {
        messagingTemplate.convertAndSend(
            "/topic/submission/" + submission.getSubmissionId(),
            Map.of(
                "submissionId", submission.getSubmissionId(),
                "status", submission.getStatus().name()
            )
        );
    }

    public void sendResult(Submission submission) {
        messagingTemplate.convertAndSend(
            "/topic/submission/" + submission.getSubmissionId(),
            Map.of(
                "submissionId", submission.getSubmissionId(),
                "status", "COMPLETED",
                "verdict", submission.getVerdict().name(),
                "runtimeMs", submission.getRuntimeMs(),
                "passedTestCases", submission.getPassedTestCases(),
                "totalTestCases", submission.getTotalTestCases()
            )
        );
    }
}
```

---

## Summary of Changes

| Component | Current | Target |
|-----------|---------|--------|
| CXE Integration | Maven library | HTTP client |
| Execution | Synchronous | Async with polling |
| Result validation | In CXE | In SubmissionService |
| Submission storage | No | Yes (new entity) |
| Question stats | No | Yes (new entity) |
| Real-time updates | Kafka logs | WebSocket |
| Kafka | Required | Optional |

---

## Implementation Order

1. **Add new entities to EntityService** (Submission, QuestionStatistics)
2. **Add HTTP client** for CXE communication
3. **Add ResultValidationService** for output comparison
4. **Refactor SubmissionProcessingService** to use HTTP polling
5. **Update REST endpoints** with new structure
6. **Add WebSocket support** for real-time updates
7. **Remove/reduce Kafka** dependency
8. **Add rate limiting** (Redis-based)
9. **Add caching** for question metadata
