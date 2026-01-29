# Code Execution Engine API Documentation

**Base URL**: `http://localhost:8081/api/v1/execution`

This microservice handles the asynchronous execution of code. It provides endpoints to submit code, poll for status, and retrieve detailed results.

---

## 1. Submit Code
**Endpoint**: `POST /submit`
**Description**: Submits code for execution. Returns immediately with a `submissionId` and initial status.

### Request Body (`ExecutionRequest`)
```json
{
  "submissionId": "optional-uuid-string", // If omitted, server generates one
  "userId": 123,
  "questionId": 456,
  "language": "java", // "java", "python"
  "code": "class Solution { ... }",
  "metadata": {
    "functionName": "twoSum",
    "returnType": "int[]",
    "parameters": [
      {
        "name": "nums",
        "type": "int[]"
      },
      {
        "name": "target",
        "type": "int"
      }
    ],
    "customDataStructures": {
      "ListNode": "ListNode" // Optional map if using custom types
    }
  },
  "testCases": [
    {
      "input": {
        "nums": [2, 7, 11, 15],
        "target": 9
      }
    }
  ],
  "customTestCases": [  // OPTIONAL: User-provided test cases
    {
      "input": {
        "nums": [1, 2, 3],
        "target": 5
      }
    }
  ]
}
```

### Response Body (`ExecutionResponse`) - 202 Accepted
```json
{
  "submissionId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "QUEUED",
  "message": "Submission queued for execution",
  "queuePosition": 1,
  "estimatedWaitTimeMs": 3000,
  "statusUrl": "/api/v1/execution/status/550e8400-e29b-41d4-a716-446655440000",
  "resultsUrl": "/api/v1/execution/results/550e8400-e29b-41d4-a716-446655440000"
}
```

---

## 2. Poll Status
**Endpoint**: `GET /status/{submissionId}`
**Description**: Get the current status of a submission. Use this for polling (e.g., every 1-2 seconds).

### Response Body (`SubmissionStatusDto`)
```json
{
  "submissionId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "COMPLETED", // QUEUED, COMPILING, RUNNING, COMPLETED, FAILED
  "verdict": "ACCEPTED", // only present if COMPLETED
  "runtimeMs": 15,
  "memoryKb": 4200,
  "queuePosition": null,
  "queuedAt": 1700000000000,
  "startedAt": 1700000003000,
  "completedAt": 1700000003050,
  "workerId": "worker-1"
}
```

---

## 3. Get Full Results
**Endpoint**: `GET /results/{submissionId}`
**Description**: Retrieve detailed execution results, including per-test-case outputs.

### Response Body (`SubmissionStatusDto` with `testCaseResults`)
```json
{
  "submissionId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "COMPLETED",
  "verdict": "ACCEPTED", // ACCEPTED, WRONG_ANSWER, TIME_LIMIT_EXCEEDED, RUNTIME_ERROR, COMPILATION_ERROR
  "runtimeMs": 15,
  "memoryKb": 12451, // ✨ NEW: Max memory across all test cases
  "compilationOutput": "", // Contains compiler errors if verdict is COMPILATION_ERROR
  "testCaseResults": [
    {
      "index": 0,
      "passed": true,
      "actualOutput": "[0,1]",
      "executionTimeMs": 2,
      "memoryBytes": 12746752, // ✨ NEW: Memory for this test case
      "isCustom": false, // ✨ NEW: Official test case
      "error": null
    },
    {
      "index": 1,
      "passed": false,
      "actualOutput": "[1,2]",
      "executionTimeMs": 1,
      "memoryBytes": 11534336,
      "isCustom": true, // ✨ NEW: Custom test case (doesn't affect verdict)
      "error": null, // Or error message if runtime error
      "errorType": null
    }
  ]
}
```

---

## 4. Health Check
**Endpoint**: `GET /health`
**Description**: Check service health and queue statistics.

### Response Body
```json
{
  "status": "UP",
  "queueSize": 5,
  "activeWorkers": 3,
  "avgExecutionTimeMs": 145.5
}
```

---

## 5. Cancel Submission
**Endpoint**: `DELETE /cancel/{submissionId}`
**Description**: Cancel a queued submission. Status will update to `CANCELLED`.

### Response Body
```json
{
  "success": true,
  "message": "Submission cancelled",
  "submissionId": "..."
}
```
