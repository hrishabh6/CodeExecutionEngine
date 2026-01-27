# Code Execution Engine - Detailed Improvements

## Critical Transformation: Library → Microservice

### Current Architecture (WRONG ❌)

```
┌─────────────────────┐
│ Submission Service  │
│                     │
│  @Autowired         │
│  CodeExecutionMgr ◄─┼──── Spring autowires library bean
│         │           │
│         ▼           │
│  executeSync()      │──┐
│    [BLOCKS 10s]     │  │ Kafka consumer thread blocked
│         │           │  │
│         ▼           │  │
│  Return result      │◄─┘
└─────────────────────┘

Problems:
1. Synchronous execution blocks Kafka consumer
2. Cannot scale workers independently
3. No load balancing
4. Single point of failure
5. No health checks
```

### Target Architecture (CORRECT ✅)

```
┌─────────────────┐      ┌────────────────────┐
│ Submission Svc  │──────▶│  Redis Queue       │
│                 │ LPUSH │  "pending:queue"   │
└─────────────────┘      └────────────────────┘
                                   │
                    ┌──────────────┼──────────────┐
                    ▼              ▼              ▼
            ┌───────────┐  ┌───────────┐  ┌───────────┐
            │ Worker 1  │  │ Worker 2  │  │ Worker N  │
            │           │  │           │  │           │
            │ BRPOP     │  │ BRPOP     │  │ BRPOP     │
            │ Execute   │  │ Execute   │  │ Execute   │
            │ Update DB │  │ Update DB │  │ Update DB │
            └───────────┘  └───────────┘  └───────────┘
                    │              │              │
                    └──────────────┴──────────────┘
                                   ▼
                          ┌─────────────────┐
                          │   PostgreSQL    │
                          │   submissions   │
                          └─────────────────┘

Benefits:
1. Async execution (non-blocking)
2. Horizontal scaling (add more workers)
3. Load balancing (Redis queue)
4. Fault tolerance (worker failures don't block queue)
5. Health monitoring per worker
```

---

## Step 1: Convert to REST Microservice

### New Project Structure

```
code-execution-service/
├── src/main/java/com/hrishabh/codeexecutionservice/
│   ├── CodeExecutionServiceApplication.java
│   ├── controller/
│   │   └── ExecutionController.java       # REST API
│   ├── service/
│   │   ├── ExecutionQueueService.java     # Redis queue management
│   │   ├── ExecutionWorkerService.java    # Worker that processes jobs
│   │   ├── SubmissionStatusService.java   # Track submission status
│   │   └── execution/                     # Move existing execution logic here
│   ├── dto/
│   │   ├── ExecutionRequest.java
│   │   ├── ExecutionResponse.java
│   │   └── SubmissionStatusDto.java
│   ├── config/
│   │   ├── RedisConfig.java
│   │   └── ExecutorConfig.java
│   └── worker/
│       └── CodeExecutionWorker.java       # Background job processor
├── build.gradle
└── docker-compose.yml                     # Local development setup
```

### 1.1 Create REST Controller

```java
package com.hrishabh.codeexecutionservice.controller;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/execution")
@RequiredArgsConstructor
public class ExecutionController {

    private final ExecutionQueueService queueService;
    private final SubmissionStatusService statusService;

    /**
     * Submit code for execution (async)
     * Returns immediately with submission ID
     */
    @PostMapping("/submit")
    public ResponseEntity<ExecutionResponse> submitExecution(
        @RequestBody ExecutionRequest request
    ) {
        String submissionId = queueService.enqueue(request);
        
        return ResponseEntity
            .status(HttpStatus.ACCEPTED) // 202 Accepted
            .body(ExecutionResponse.builder()
                .submissionId(submissionId)
                .status("QUEUED")
                .message("Submission queued for execution")
                .queuePosition(queueService.getQueuePosition(submissionId))
                .estimatedWaitTimeMs(queueService.getEstimatedWaitTime())
                .build());
    }

    /**
     * Get submission status (for polling)
     */
    @GetMapping("/status/{submissionId}")
    public ResponseEntity<SubmissionStatusDto> getStatus(
        @PathVariable String submissionId
    ) {
        return statusService.getStatus(submissionId)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get full execution results
     */
    @GetMapping("/results/{submissionId}")
    public ResponseEntity<CodeExecutionResultDTO> getResults(
        @PathVariable String submissionId
    ) {
        return statusService.getFullResults(submissionId)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Health check
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
            "status", "UP",
            "queueSize", queueService.getQueueSize(),
            "activeWorkers", statusService.getActiveWorkerCount(),
            "avgExecutionTimeMs", statusService.getAverageExecutionTime()
        ));
    }
}
```

### 1.2 DTOs

```java
package com.hrishabh.codeexecutionservice.dto;

import lombok.*;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecutionRequest {
    private String submissionId; // Optional, will generate if null
    private Long userId;
    private Long questionId;
    private String language;
    private String code;
    private QuestionMetadata metadata;
    private List<Map<String, Object>> testCases;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QuestionMetadata {
        private String functionName;
        private String returnType;
        private List<Parameter> parameters;
        private List<String> customDataStructures;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Parameter {
        private String name;
        private String type;
    }
}

@Data
@Builder
public class ExecutionResponse {
    private String submissionId;
    private String status; // QUEUED, COMPILING, RUNNING, COMPLETED
    private String message;
    private Integer queuePosition;
    private Long estimatedWaitTimeMs;
    private String statusUrl; // /api/v1/execution/status/{submissionId}
}

@Data
@Builder
public class SubmissionStatusDto {
    private String submissionId;
    private String status;
    private String verdict; // ACCEPTED, WRONG_ANSWER, etc. (if completed)
    private Integer runtimeMs;
    private Integer memoryKb;
    private String errorMessage;
    private List<TestCaseResult> testCaseResults;
    private Long queuedAt;
    private Long startedAt;
    private Long completedAt;
    
    @Data
    @Builder
    public static class TestCaseResult {
        private Integer index;
        private Boolean passed;
        private String actualOutput;
        private String expectedOutput;
        private Long executionTimeMs;
        private String error;
    }
}
```

---

## Step 2: Implement Redis Queue

### 2.1 Redis Configuration

```java
package com.hrishabh.codeexecutionservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.*;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.*;

@Configuration
public class RedisConfig {

    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        return new LettuceConnectionFactory("localhost", 6379);
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);
        
        // Use Jackson for JSON serialization
        Jackson2JsonRedisSerializer<Object> serializer = 
            new Jackson2JsonRedisSerializer<>(Object.class);
        
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(serializer);
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(serializer);
        
        return template;
    }
}
```

### 2.2 Queue Service

```java
package com.hrishabh.codeexecutionservice.service;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class ExecutionQueueService {

    private final RedisTemplate<String, Object> redisTemplate;
    
    private static final String QUEUE_KEY = "execution:queue";
    private static final String STATUS_PREFIX = "execution:status:";
    private static final long STATUS_TTL = 3600; // 1 hour

    /**
     * Add submission to queue
     */
    public String enqueue(ExecutionRequest request) {
        // Generate submission ID if not provided
        String submissionId = request.getSubmissionId();
        if (submissionId == null || submissionId.isEmpty()) {
            submissionId = UUID.randomUUID().toString();
            request.setSubmissionId(submissionId);
        }

        // Set initial status
        setStatus(submissionId, SubmissionStatusDto.builder()
            .submissionId(submissionId)
            .status("QUEUED")
            .queuedAt(System.currentTimeMillis())
            .build());

        // Add to queue (LPUSH = left push, workers BRPOP from right)
        redisTemplate.opsForList().leftPush(QUEUE_KEY, request);

        return submissionId;
    }

    /**
     * Dequeue submission (blocking, for workers)
     */
    public ExecutionRequest dequeue(long timeoutSeconds) {
        Object result = redisTemplate.opsForList()
            .rightPop(QUEUE_KEY, timeoutSeconds, TimeUnit.SECONDS);
        
        if (result == null) return null;
        
        return (ExecutionRequest) result;
    }

    /**
     * Get queue size
     */
    public Long getQueueSize() {
        return redisTemplate.opsForList().size(QUEUE_KEY);
    }

    /**
     * Get position in queue
     */
    public Integer getQueuePosition(String submissionId) {
        List<Object> queue = redisTemplate.opsForList()
            .range(QUEUE_KEY, 0, -1);
        
        for (int i = 0; i < queue.size(); i++) {
            ExecutionRequest req = (ExecutionRequest) queue.get(i);
            if (req.getSubmissionId().equals(submissionId)) {
                return i + 1;
            }
        }
        return null; // Not in queue (already processing or completed)
    }

    /**
     * Estimate wait time based on queue size and avg execution time
     */
    public Long getEstimatedWaitTime() {
        Long queueSize = getQueueSize();
        if (queueSize == 0) return 0L;
        
        // Assuming average 2 seconds per submission
        return queueSize * 2000;
    }

    /**
     * Update submission status
     */
    public void setStatus(String submissionId, SubmissionStatusDto status) {
        String key = STATUS_PREFIX + submissionId;
        redisTemplate.opsForValue().set(key, status, STATUS_TTL, TimeUnit.SECONDS);
    }

    /**
     * Get submission status
     */
    public SubmissionStatusDto getStatus(String submissionId) {
        String key = STATUS_PREFIX + submissionId;
        Object status = redisTemplate.opsForValue().get(key);
        return (SubmissionStatusDto) status;
    }
}
```

---

## Step 3: Implement Worker Service

### 3.1 Worker Configuration

```java
package com.hrishabh.codeexecutionservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import java.util.concurrent.Executor;

@Configuration
public class ExecutorConfig {

    @Bean(name = "executionWorkerExecutor")
    public Executor executionWorkerExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5); // 5 worker threads
        executor.setMaxPoolSize(10); // Up to 10 during peak
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("execution-worker-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }
}
```

### 3.2 Worker Service

```java
package com.hrishabh.codeexecutionservice.service;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExecutionWorkerService {

    private final ExecutionQueueService queueService;
    private final CodeExecutorService codeExecutor;
    private final SubmissionRepository submissionRepository;

    /**
     * Start worker thread that polls queue
     */
    @Async("executionWorkerExecutor")
    public void startWorker(String workerId) {
        log.info("Worker {} started", workerId);
        
        while (!Thread.currentThread().isInterrupted()) {
            try {
                // Block for 5 seconds waiting for job
                ExecutionRequest request = queueService.dequeue(5);
                
                if (request != null) {
                    processSubmission(request, workerId);
                }
            } catch (Exception e) {
                log.error("Worker {} error: {}", workerId, e.getMessage(), e);
                // Continue processing next job
            }
        }
        
        log.info("Worker {} stopped", workerId);
    }

    /**
     * Process a single submission
     */
    private void processSubmission(ExecutionRequest request, String workerId) {
        String submissionId = request.getSubmissionId();
        log.info("Worker {} processing {}", workerId, submissionId);

        try {
            // Update status to COMPILING
            updateStatus(submissionId, "COMPILING", null);

            // Execute code
            long startTime = System.currentTimeMillis();
            CodeExecutionResultDTO result = codeExecutor.execute(request);
            long executionTime = System.currentTimeMillis() - startTime;

            // Update status to COMPLETED
            String verdict = determineVerdict(result);
            updateStatus(submissionId, "COMPLETED", verdict);

            // Save to database
            saveResults(submissionId, result, verdict, executionTime, workerId);

            log.info("Worker {} completed {} in {}ms - {}", 
                workerId, submissionId, executionTime, verdict);

        } catch (Exception e) {
            log.error("Worker {} failed {}: {}", workerId, submissionId, e.getMessage(), e);
            updateStatus(submissionId, "FAILED", "INTERNAL_ERROR");
            saveError(submissionId, e.getMessage(), workerId);
        }
    }

    private void updateStatus(String submissionId, String status, String verdict) {
        SubmissionStatusDto statusDto = SubmissionStatusDto.builder()
            .submissionId(submissionId)
            .status(status)
            .verdict(verdict)
            .build();
        
        if (status.equals("COMPILING")) {
            statusDto.setStartedAt(System.currentTimeMillis());
        } else if (status.equals("COMPLETED") || status.equals("FAILED")) {
            statusDto.setCompletedAt(System.currentTimeMillis());
        }
        
        queueService.setStatus(submissionId, statusDto);
    }

    private String determineVerdict(CodeExecutionResultDTO result) {
        if (result.getOverallStatus().equals("SUCCESS")) {
            // Check if all test cases passed
            boolean allPassed = result.getTestCaseOutputs().stream()
                .allMatch(tc -> tc.getErrorMessage() == null);
            return allPassed ? "ACCEPTED" : "WRONG_ANSWER";
        } else if (result.getOverallStatus().equals("COMPILATON_ERROR")) {
            return "COMPILATION_ERROR";
        } else if (result.getOverallStatus().equals("TIMEOUT")) {
            return "TIME_LIMIT_EXCEEDED";
        } else if (result.getOverallStatus().equals("RUNTIME_ERROR")) {
            return "RUNTIME_ERROR";
        }
        return "INTERNAL_ERROR";
    }

    private void saveResults(String submissionId, CodeExecutionResultDTO result, 
                            String verdict, long executionTime, String workerId) {
        Submission submission = submissionRepository
            .findBySubmissionId(submissionId)
            .orElseThrow(() -> new RuntimeException("Submission not found: " + submissionId));

        submission.setStatus(SubmissionStatus.COMPLETED);
        submission.setVerdict(SubmissionVerdict.valueOf(verdict));
        submission.setCompletedAt(LocalDateTime.now());
        submission.setRuntimeMs((int) executionTime);
        // ... set other fields

        submissionRepository.save(submission);
    }

    private void saveError(String submissionId, String error, String workerId) {
        Submission submission = submissionRepository
            .findBySubmissionId(submissionId)
            .orElse(null);
        
        if (submission != null) {
            submission.setStatus(SubmissionStatus.FAILED);
            submission.setErrorMessage(error);
            submission.setCompletedAt(LocalDateTime.now());
            submissionRepository.save(submission);
        }
    }
}
```

### 3.3 Worker Lifecycle Management

```java
package com.hrishabh.codeexecutionservice;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class WorkerBootstrap implements CommandLineRunner {

    private final ExecutionWorkerService workerService;

    @Override
    public void run(String... args) {
        // Start 5 workers on application startup
        int workerCount = Integer.parseInt(
            System.getenv().getOrDefault("WORKER_COUNT", "5")
        );

        for (int i = 1; i <= workerCount; i++) {
            String workerId = "worker-" + i;
            workerService.startWorker(workerId);
        }
    }
}
```

---

## Step 4: Replace Docker with Lightweight Isolation

### Option A: Integrate Judge0 (RECOMMENDED)

**Why Judge0:**
- Production-tested by thousands of companies
- 75+ languages supported
- Active maintenance and security updates
- Shows you can integrate complex systems
- Docker-based but highly optimized

**Setup:**

```yaml
# docker-compose.yml for local development
version: '3.8'

services:
  judge0-server:
    image: judge0/judge0:latest
    ports:
      - "2358:2358"
    environment:
      - REDIS_HOST=judge0-redis
      - POSTGRES_HOST=judge0-db
      - POSTGRES_USER=judge0
      - POSTGRES_PASSWORD=judge0pass
      - POSTGRES_DB=judge0
    depends_on:
      - judge0-redis
      - judge0-db

  judge0-redis:
    image: redis:7-alpine
    ports:
      - "6380:6379"

  judge0-db:
    image: postgres:15-alpine
    environment:
      - POSTGRES_USER=judge0
      - POSTGRES_PASSWORD=judge0pass
      - POSTGRES_DB=judge0
    ports:
      - "5433:5432"
```

**Integration Service:**

```java
package com.hrishabh.codeexecutionservice.service;

import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class Judge0Service {

    private final RestTemplate restTemplate;
    private static final String JUDGE0_URL = "http://localhost:2358";

    public String submitToJudge0(ExecutionRequest request) {
        // Map to Judge0 submission format
        Map<String, Object> submission = Map.of(
            "source_code", request.getCode(),
            "language_id", getLanguageId(request.getLanguage()),
            "stdin", prepareStdin(request.getTestCases()),
            "expected_output", prepareExpectedOutput(request.getTestCases()),
            "cpu_time_limit", "2", // 2 seconds
            "memory_limit", "256000" // 256 MB in KB
        );

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(submission);
        ResponseEntity<Map> response = restTemplate.postForEntity(
            JUDGE0_URL + "/submissions?wait=false",
            entity,
            Map.class
        );

        return (String) response.getBody().get("token");
    }

    public Judge0Result getResult(String token) {
        ResponseEntity<Judge0Result> response = restTemplate.getForEntity(
            JUDGE0_URL + "/submissions/" + token,
            Judge0Result.class
        );
        return response.getBody();
    }

    private int getLanguageId(String language) {
        return switch (language.toLowerCase()) {
            case "java" -> 62;   // Java (OpenJDK 13.0.1)
            case "python" -> 71; // Python (3.8.1)
            case "cpp" -> 54;    // C++ (GCC 9.2.0)
            case "javascript" -> 63; // JavaScript (Node.js 12.14.0)
            default -> throw new IllegalArgumentException("Unsupported language: " + language);
        };
    }

    @Data
    public static class Judge0Result {
        private String token;
        private Integer status_id;
        private String status_description;
        private String stdout;
        private String stderr;
        private String compile_output;
        private Double time;
        private Integer memory;
    }
}
```

### Option B: Use gVisor (For Learning)

**Install gVisor:**

```bash
# Install runsc (gVisor runtime)
(
  set -e
  ARCH=$(uname -m)
  URL=https://storage.googleapis.com/gvisor/releases/release/latest/${ARCH}
  wget ${URL}/runsc ${URL}/runsc.sha512
  sha512sum -c runsc.sha512
  chmod a+x runsc
  sudo mv runsc /usr/local/bin
)

# Configure Docker to use runsc
sudo mkdir -p /etc/docker
echo '{
  "runtimes": {
    "runsc": {
      "path": "/usr/local/bin/runsc"
    }
  }
}' | sudo tee /etc/docker/daemon.json

sudo systemctl restart docker
```

**Use in Code:**

```java
// Instead of
ProcessBuilder pb = new ProcessBuilder("docker", "run", "--rm", ...);

// Use
ProcessBuilder pb = new ProcessBuilder(
    "docker", "run", 
    "--runtime=runsc",  // Use gVisor
    "--rm",
    "--network=none",   // No network access
    "--memory=256m",
    "--cpus=0.5",
    image, command
);
```

---

## Step 5: Optimize File Generation

### Current Problem

```java
// You generate files for EVERY submission
JavaMainClassGenerator.generate() → writes Main.java
JavaSolutionClassGenerator.generate() → writes Solution.java
// Then compile both files
```

### Better Approach: Pre-compiled Harness

**Strategy:**
1. Pre-compile a generic test harness once
2. Only compile user's Solution.java
3. Load Solution class dynamically

**Implementation:**

```java
package com.hrishabh.codeexecutionservice.templates;

/**
 * Pre-compiled test harness (compile once on startup)
 */
public class TestHarness {
    
    public static void main(String[] args) throws Exception {
        // Read test cases from stdin (JSON)
        String testCasesJson = readStdin();
        
        // Load user's Solution class dynamically
        ClassLoader classLoader = TestHarness.class.getClassLoader();
        Class<?> solutionClass = classLoader.loadClass("Solution");
        Object solutionInstance = solutionClass.getDeclaredConstructor().newInstance();
        
        // Get the solution method
        Method method = solutionClass.getMethod(
            System.getenv("FUNCTION_NAME"),
            getParameterTypes()
        );
        
        // Execute against each test case
        List<TestCase> testCases = parseTestCases(testCasesJson);
        for (int i = 0; i < testCases.size(); i++) {
            TestCase tc = testCases.get(i);
            
            long start = System.nanoTime();
            try {
                Object result = method.invoke(solutionInstance, tc.getInputs());
                long duration = (System.nanoTime() - start) / 1_000_000; // ms
                
                System.out.println("TEST_CASE_RESULT: " + i + "," + 
                    toJson(result) + "," + duration + ",");
            } catch (Exception e) {
                long duration = (System.nanoTime() - start) / 1_000_000;
                System.out.println("TEST_CASE_RESULT: " + i + ",," + 
                    duration + "," + e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        }
    }
}
```

**Workflow:**

```
1. Startup: Compile TestHarness.java once
2. Per submission:
   a. Write user's Solution.java
   b. Compile ONLY Solution.java (fast!)
   c. Execute: java -cp . TestHarness
   d. TestHarness loads Solution.class dynamically
```

This reduces compilation time from ~500ms to ~200ms (60% faster).

---

## Step 6: Add Performance Monitoring

### 6.1 Prometheus Metrics

```java
package com.hrishabh.codeexecutionservice.config;

import io.micrometer.core.instrument.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MetricsConfig {

    @Bean
    public Counter submissionCounter(MeterRegistry registry) {
        return Counter.builder("submissions.total")
            .description("Total submissions processed")
            .tag("service", "code-execution")
            .register(registry);
    }

    @Bean
    public Timer executionTimer(MeterRegistry registry) {
        return Timer.builder("execution.time")
            .description("Code execution time")
            .tag("service", "code-execution")
            .register(registry);
    }

    @Bean
    public Gauge queueSizeGauge(ExecutionQueueService queueService, MeterRegistry registry) {
        return Gauge.builder("queue.size", queueService::getQueueSize)
            .description("Current queue size")
            .register(registry);
    }
}

// Usage in worker service
@Timed(value = "execution.time", description = "Time to execute code")
private void processSubmission(ExecutionRequest request, String workerId) {
    submissionCounter.increment();
    // ... rest of code
}
```

### 6.2 Add to application.yml

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,metrics,prometheus
  metrics:
    export:
      prometheus:
        enabled: true
```

### 6.3 Grafana Dashboard

Create dashboard with panels:
- Submissions per minute
- Average execution time
- Queue size over time
- Worker utilization
- Error rate by verdict

---

## Step 7: Add Caching

### Result Caching

```java
@Service
@RequiredArgsConstructor
public class ExecutionCacheService {

    private final RedisTemplate<String, Object> redisTemplate;
    private static final String CACHE_PREFIX = "execution:cache:";
    private static final long CACHE_TTL = 3600; // 1 hour

    public String getCacheKey(ExecutionRequest request) {
        // Hash: language + code + test cases
        String data = request.getLanguage() + 
                     request.getCode() + 
                     request.getTestCases().toString();
        return DigestUtils.sha256Hex(data);
    }

    public Optional<CodeExecutionResultDTO> getCached(String cacheKey) {
        Object cached = redisTemplate.opsForValue()
            .get(CACHE_PREFIX + cacheKey);
        return Optional.ofNullable((CodeExecutionResultDTO) cached);
    }

    public void cache(String cacheKey, CodeExecutionResultDTO result) {
        redisTemplate.opsForValue()
            .set(CACHE_PREFIX + cacheKey, result, CACHE_TTL, TimeUnit.SECONDS);
    }
}

// Usage in worker
private void processSubmission(ExecutionRequest request, String workerId) {
    String cacheKey = cacheService.getCacheKey(request);
    
    Optional<CodeExecutionResultDTO> cached = cacheService.getCached(cacheKey);
    if (cached.isPresent()) {
        log.info("Cache hit for {}", request.getSubmissionId());
        saveResults(request.getSubmissionId(), cached.get(), ...);
        return;
    }
    
    // Execute code...
    CodeExecutionResultDTO result = codeExecutor.execute(request);
    cacheService.cache(cacheKey, result);
}
```

---

