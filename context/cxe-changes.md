# Code Execution Engine - Detailed Changes

## Overview

The **CodeExecutionEngine** has been transformed from a **synchronous Maven library** into a **standalone REST microservice** with async execution capabilities.

---

## Architecture Comparison

### Before: Library Pattern

```
┌─────────────────────────────────────────────────────────┐
│                   Submission Service                     │
│  ┌─────────────────┐    ┌────────────────────────────┐  │
│  │  Kafka Consumer │───▶│  CodeExecutionManager      │  │
│  │  (BLOCKED 10s)  │    │  (Maven dependency)        │  │
│  └─────────────────┘    └────────────────────────────┘  │
│                                    │                     │
│                                    ▼                     │
│                         ┌──────────────────┐            │
│                         │  Docker Execution │            │
│                         │  (synchronous)    │            │
│                         └──────────────────┘            │
└─────────────────────────────────────────────────────────┘

Problems:
❌ Kafka consumer thread blocked for 10+ seconds
❌ Cannot process submissions in parallel
❌ No horizontal scaling
❌ Single point of failure
❌ Results not persisted
```

### After: Microservice Pattern

```
┌────────────────────┐      HTTP        ┌─────────────────────────────────┐
│ Submission Service │ ───────────────▶ │  Code Execution Service         │
│                    │                  │  (Port 8081)                    │
│  • User auth       │     202 Accepted │  ┌─────────────────────────┐    │
│  • Rate limiting   │ ◀─────────────── │  │ ExecutionController     │    │
│  • Result checking │                  │  │   POST /submit          │    │
│  • History         │     Poll Status  │  │   GET /status/{id}      │    │
│                    │ ───────────────▶ │  │   GET /results/{id}     │    │
└────────────────────┘                  │  └─────────────────────────┘    │
                                        │              │                  │
                                        │              ▼                  │
                                        │  ┌─────────────────────────┐    │
                                        │  │ Redis Queue             │    │
                                        │  │ "execution:queue"       │    │
                                        │  └─────────────────────────┘    │
                                        │         │ │ │ │ │               │
                                        │         ▼ ▼ ▼ ▼ ▼               │
                                        │  ┌─────────────────────────┐    │
                                        │  │ Worker Pool (1-10)      │    │
                                        │  │ Async execution         │    │
                                        │  └─────────────────────────┘    │
                                        │              │                  │
                                        │              ▼                  │
                                        │  ┌─────────────────────────┐    │
                                        │  │ MySQL (Persistence)     │    │
                                        │  │ submission, metrics     │    │
                                        │  └─────────────────────────┘    │
                                        └─────────────────────────────────┘

Benefits:
✅ Non-blocking async execution
✅ Horizontal scaling (add workers)
✅ Independent deployment
✅ Built-in persistence
✅ Status polling support
✅ Monitoring with Prometheus
```

---

## Files Changed

### New Files Created (17)

| Category | File | Purpose |
|----------|------|---------|
| **Config** | `application.yml` | Server, DB, Redis, worker configuration |
| **Config** | `docker-compose.yml` | Local Redis + MySQL setup |
| **Config** | `RedisConfig.java` | Redis connection and serialization |
| **Config** | `ExecutorConfig.java` | Thread pool for workers |
| **Entity** | `Submission.java` | JPA entity for submission persistence |
| **Entity** | `ExecutionMetrics.java` | Analytics metrics entity |
| **Enum** | `SubmissionStatus.java` | QUEUED, COMPILING, RUNNING, etc. |
| **Enum** | `SubmissionVerdict.java` | ACCEPTED, WRONG_ANSWER, TLE, etc. |
| **Controller** | `ExecutionController.java` | REST API endpoints |
| **DTO** | `ExecutionRequest.java` | Submission input DTO |
| **DTO** | `ExecutionResponse.java` | Immediate response DTO |
| **DTO** | `SubmissionStatusDto.java` | Status/results DTO |
| **Service** | `ExecutionQueueService.java` | Redis queue operations |
| **Service** | `ExecutionWorkerService.java` | Worker processing logic |
| **Service** | `SubmissionStatusService.java` | Status retrieval |
| **Repository** | `SubmissionRepository.java` | Submission queries |
| **Repository** | `ExecutionMetricsRepository.java` | Metrics queries |
| **Bootstrap** | `WorkerBootstrap.java` | Start workers on startup |
| **Migration** | `V1__create_submission_table.sql` | Submission table |
| **Migration** | `V2__create_execution_metrics_table.sql` | Metrics table |

### Modified Files (2)

| File | Changes |
|------|---------|
| `build.gradle` | Removed Maven publishing, added web/redis/JPA/Flyway/Actuator |
| `CodeExecutionEngineApplication.java` | Added `@EnableAsync`, `@EnableScheduling`, entity/repo scans |

---

## REST API Specification

### POST /api/v1/execution/submit

Submit code for asynchronous execution.

**Request:**
```json
{
  "userId": 1,
  "questionId": 1,
  "language": "java",
  "code": "class Solution { ... }",
  "metadata": {
    "fullyQualifiedPackageName": "com.algocrack.solution.q1",
    "functionName": "twoSum",
    "returnType": "int[]",
    "parameters": [
      {"name": "nums", "type": "int[]"},
      {"name": "target", "type": "int"}
    ]
  },
  "testCases": [
    {"input": {"nums": [2,7,11,15], "target": 9}}
  ]
}
```

**Response (202 Accepted):**
```json
{
  "submissionId": "a1b2c3d4-e5f6-...",
  "status": "QUEUED",
  "message": "Submission queued for execution",
  "queuePosition": 3,
  "estimatedWaitTimeMs": 9000,
  "statusUrl": "/api/v1/execution/status/a1b2c3d4-e5f6-...",
  "resultsUrl": "/api/v1/execution/results/a1b2c3d4-e5f6-..."
}
```

### GET /api/v1/execution/status/{submissionId}

Poll for current status.

**Response:**
```json
{
  "submissionId": "a1b2c3d4-e5f6-...",
  "status": "RUNNING",
  "verdict": null,
  "runtimeMs": null,
  "queuedAt": 1705759800000,
  "startedAt": 1705759803000,
  "workerId": "worker-2"
}
```

### GET /api/v1/execution/results/{submissionId}

Get full results after completion.

**Response:**
```json
{
  "submissionId": "a1b2c3d4-e5f6-...",
  "status": "COMPLETED",
  "verdict": "ACCEPTED",
  "runtimeMs": 45,
  "testCaseResults": [
    {
      "index": 0,
      "passed": true,
      "actualOutput": "[0,1]",
      "executionTimeMs": 15
    }
  ],
  "completedAt": 1705759806000,
  "workerId": "worker-2"
}
```

---

## Worker Pool Architecture

```
┌─────────────────────────────────────────────────────────┐
│                    Worker Pool                          │
│                                                         │
│  WorkerBootstrap (on startup)                          │
│       │                                                 │
│       ├── startWorker("worker-1") ──▶ Thread 1         │
│       ├── startWorker("worker-2") ──▶ Thread 2         │
│       ├── startWorker("worker-3") ──▶ Thread 3         │
│       ├── startWorker("worker-4") ──▶ Thread 4         │
│       └── startWorker("worker-5") ──▶ Thread 5         │
│                                                         │
│  Each worker:                                          │
│  ┌─────────────────────────────────────────────────┐   │
│  │  while (!interrupted) {                         │   │
│  │    request = redis.BRPOP("execution:queue", 5s) │   │
│  │    if (request != null) {                       │   │
│  │      updateStatus(COMPILING)                    │   │
│  │      result = codeExecutionManager.run(request) │   │
│  │      updateStatus(COMPLETED, verdict)           │   │
│  │      saveToDatabase(submission, metrics)        │   │
│  │    }                                            │   │
│  │  }                                              │   │
│  └─────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────┘
```

**Configuration (`application.yml`):**
```yaml
execution:
  worker:
    count: 5              # Number of workers
    timeout-seconds: 10   # Execution timeout
    poll-timeout-seconds: 5
```

---

## Database Schema

### submission table

| Column | Type | Description |
|--------|------|-------------|
| `id` | BIGINT | Primary key |
| `submission_id` | VARCHAR(36) | UUID for external reference |
| `user_id` | BIGINT | User who submitted |
| `question_id` | BIGINT | Question being solved |
| `language` | VARCHAR(20) | java, python |
| `code` | TEXT | User's solution code |
| `status` | VARCHAR(20) | QUEUED, COMPILING, RUNNING, COMPLETED, FAILED |
| `verdict` | VARCHAR(30) | ACCEPTED, WRONG_ANSWER, TLE, etc. |
| `runtime_ms` | INT | Execution time |
| `test_results` | JSON | Test case results array |
| `error_message` | TEXT | Error if any |
| `queued_at` | DATETIME | When queued |
| `started_at` | DATETIME | When execution started |
| `completed_at` | DATETIME | When completed |
| `worker_id` | VARCHAR(50) | Worker that processed |

**Indexes:**
- `idx_submission_id` - Fast lookup by UUID
- `idx_user_status` - User's submissions by status
- `idx_status_queued` - Queue monitoring

---

## Redis Data Structures

### Queue: `execution:queue`

Type: **List** (LPUSH/BRPOP)

```
┌───────────────────────────────────────────┐
│  LPUSH (new submissions)                  │
│     ▼                                     │
│  [ Request3, Request2, Request1 ]         │
│                               ▲           │
│                      BRPOP (workers)      │
└───────────────────────────────────────────┘
```

### Status: `execution:status:{submissionId}`

Type: **String** (JSON value)

```json
{
  "submissionId": "abc-123",
  "status": "RUNNING",
  "workerId": "worker-3",
  "startedAt": 1705759803000
}
```

TTL: 1 hour

---

## Status Flow

```
QUEUED ─────────────▶ COMPILING ─────────────▶ RUNNING ─────────────▶ COMPLETED
   │                      │                       │                       │
   │                      │                       │                       ▼
   │                      │                       │                   [verdict]
   │                      │                       │                   ACCEPTED
   │                      │                       │                   WRONG_ANSWER
   │                      │                       │                   TIME_LIMIT_EXCEEDED
   │                      │                       │                   MEMORY_LIMIT_EXCEEDED
   │                      │                       │                   RUNTIME_ERROR
   │                      ▼                       ▼                   COMPILATION_ERROR
   │              FAILED (compile error)    FAILED (runtime)         INTERNAL_ERROR
   ▼
CANCELLED (user cancelled)
```

---

## Monitoring

### Health Endpoint

```bash
GET /api/v1/execution/health
```

```json
{
  "status": "UP",
  "queueSize": 12,
  "activeWorkers": 5,
  "avgExecutionTimeMs": 2340.5
}
```

### Prometheus Metrics

```bash
GET /actuator/prometheus
```

Exposed metrics:
- `submissions_total` - Total submissions processed
- `queue_size` - Current queue depth
- `execution_time_seconds` - Histogram of execution times

---

## Migration from Library

### For SubmissionService

**Before (library):**
```java
@Autowired
private CodeExecutionManager codeExecutionManager;

// Blocks for 10+ seconds
CodeExecutionResultDTO result = codeExecutionManager.runCodeWithTestcases(dto, logConsumer);
```

**After (HTTP):**
```java
@Autowired
private RestTemplate restTemplate;

// Submit (returns immediately)
ExecutionResponse response = restTemplate.postForObject(
    "http://cxe-service:8081/api/v1/execution/submit",
    request,
    ExecutionResponse.class
);

String submissionId = response.getSubmissionId();

// Poll for results
while (true) {
    SubmissionStatusDto status = restTemplate.getForObject(
        "http://cxe-service:8081/api/v1/execution/status/" + submissionId,
        SubmissionStatusDto.class
    );
    if ("COMPLETED".equals(status.getStatus())) {
        // Process results
        break;
    }
    Thread.sleep(500); // Poll every 500ms
}
```

---

## Running the Service

```bash
# 1. Start infrastructure
cd /home/hrishabh/codebases/java/leetcode/CodeExecutionEngine
docker-compose up -d

# 2. Run service
./gradlew bootRun

# 3. Test
curl http://localhost:8081/api/v1/execution/health
```
