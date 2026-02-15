# Code Execution Engine (CXE) - Integration Reference

> **Document Version:** 1.0  
> **Last Updated:** 2026-02-02  
> **Status:** Production Ready  
> **Author:** Submission Service Team  
> **Target Audience:** CXE Team

---

## Table of Contents

1. [Executive Summary](#1-executive-summary)
2. [Architectural Context](#2-architectural-context)
3. [What Changed in Submission Service v2.0](#3-what-changed-in-submission-service-v20)
4. [Current API Contract (What We Use)](#4-current-api-contract-what-we-use)
5. [Required CXE Changes](#5-required-cxe-changes)
6. [Integration Flows](#6-integration-flows)
7. [Error Handling Contract](#7-error-handling-contract)
8. [Performance Requirements](#8-performance-requirements)
9. [Testing Checklist](#9-testing-checklist)
10. [Appendix: Related Documents](#10-appendix-related-documents)

---

## 1. Executive Summary

The Submission Service has been upgraded to v2.0 with **oracle-based judging**. This document details:

1. **What CXE MUST do** to remain compatible
2. **What CXE MUST NOT do** (removed responsibilities)
3. **Exact API contracts** we depend on

### Critical Changes at a Glance

| Aspect | v1.0 (Old) | v2.0 (New) | CXE Impact |
|--------|-----------|-----------|------------|
| Judging | CXE compared outputs | Submission Service compares | **CXE MUST NOT judge** |
| Expected Output | Stored in DB | Computed by oracle | CXE ignores `expectedOutput` |
| Verdict | CXE determined | Submission Service determines | CXE returns raw outputs only |
| Testcases | CXE fetched from DB | Submission Service provides | CXE MUST NOT fetch from DB |

### Core Principle

> **CXE is a pure execution engine. It receives code + testcases, executes them, and returns raw outputs. Nothing more.**

---

## 2. Architectural Context

### 2.1 System Boundaries

```
┌────────────────────────────────────────────────────────────────┐
│                      Submission Service                        │
│  ┌────────────────┐    ┌────────────────┐    ┌──────────────┐ │
│  │  /run endpoint │    │ /submit        │    │ Oracle Svc   │ │
│  │  (synchronous) │    │ (async)        │    │              │ │
│  └───────┬────────┘    └───────┬────────┘    └──────┬───────┘ │
│          │                     │                     │         │
│          └─────────────────────┴─────────────────────┘         │
│                              │                                  │
│                    ┌─────────▼─────────┐                       │
│                    │  CxeExecutionAdapter│                      │
│                    │  (Abstraction Layer)│                      │
│                    └─────────┬──────────┘                      │
└──────────────────────────────┼──────────────────────────────────┘
                               │ HTTP
                    ┌──────────▼──────────┐
                    │         CXE         │
                    │  (Pure Execution)   │
                    │                     │
                    │  Responsibilities:  │
                    │  - Compile code     │
                    │  - Execute code     │
                    │  - Capture output   │
                    │  - Measure metrics  │
                    │                     │
                    │  NOT Responsible:   │
                    │  - Fetching tests   │
                    │  - Judging output   │
                    │  - DB access        │
                    └─────────────────────┘
```

### 2.2 Communication Flow

```
Submission Service                             CXE
     │                                          │
     │  POST /api/v1/execution/submit          │
     │  {code, testcases, metadata}            │
     │────────────────────────────────────────▶│
     │                                          │
     │  202 Accepted                            │
     │  {submissionId, statusUrl}              │
     │◀────────────────────────────────────────│
     │                                          │
     │  GET /api/v1/execution/status/{id}      │
     │────────────────────────────────────────▶│ (poll)
     │  {status: "RUNNING"}                    │
     │◀────────────────────────────────────────│
     │                                          │
     │  GET /api/v1/execution/results/{id}     │
     │────────────────────────────────────────▶│
     │                                          │
     │  {testCaseResults: [{actualOutput}]}    │
     │◀────────────────────────────────────────│
     │                                          │
     ▼ Submission Service compares outputs     │
       using oracle results                     │
```

---

## 3. What Changed in Submission Service v2.0

### 3.1 New Components

| Component | Purpose |
|-----------|---------|
| `ExecutionAdapter` | Abstraction interface for CXE |
| `CxeExecutionAdapter` | CXE-specific implementation |
| `OracleExecutionService` | Executes reference solution |
| `UnifiedExecutionService` | Handles /run endpoint |
| `RunGuardService` | Rate limiting + validation |

### 3.2 Judging is Now Our Responsibility

**Before (v1.0):**
```
CXE returned: {passed: true, verdict: "ACCEPTED"}
```

**After (v2.0):**
```
CXE returns: {actualOutput: "[0,1]"}
Submission Service computes: verdict = compare(actualOutput, oracleOutput)
```

### 3.3 We Now Make 2 CXE Calls per User Execution

For every RUN or SUBMIT:
1. **User code execution** → Get user's output
2. **Oracle code execution** → Get expected output

> [!IMPORTANT]
> Both are batched (all testcases in one call). Total = **2 CXE calls** per user action.

---

## 4. Current API Contract (What We Use)

### 4.1 Endpoint: Submit Code

**URL:** `POST /api/v1/execution/submit`

**Request Body (JSON):**
```json
{
  "submissionId": "run-550e8400-e29b-41d4-a716-446655440000",
  "userId": 12345,
  "questionId": 100,
  "language": "JAVA",
  "code": "public class Solution {\n    public int[] twoSum(int[] nums, int target) {\n        // user code\n    }\n}",
  "metadata": {
    "fullyQualifiedPackageName": "com.algocrack.solution.q100",
    "functionName": "twoSum",
    "returnType": "int[]",
    "parameters": [
      { "name": "nums", "type": "int[]" },
      { "name": "target", "type": "int" }
    ],
    "customDataStructureNames": []
  },
  "testCases": [
    {
      "input": {"nums": [2, 7, 11, 15], "target": 9},
      "expectedOutput": null
    },
    {
      "input": {"nums": [3, 2, 4], "target": 6},
      "expectedOutput": null
    }
  ]
}
```

#### Field Specifications

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `submissionId` | string | Yes | Unique ID for tracking (UUID format, may start with `run-` or `oracle-`) |
| `userId` | long | No | User ID (0 for oracle execution) |
| `questionId` | long | Yes | Question being executed |
| `language` | string | Yes | `JAVA`, `PYTHON`, `CPP`, `JAVASCRIPT` |
| `code` | string | Yes | Source code to execute |
| `metadata` | object | Yes | Function signature information |
| `testCases` | array | Yes | Array of testcase objects |
| `testCases[].input` | object | Yes | Parsed JSON input (not string) |
| `testCases[].expectedOutput` | any | **IGNORE** | Always `null` in v2.0, CXE MUST ignore |

#### Metadata Specifications

| Field | Type | Description |
|-------|------|-------------|
| `fullyQualifiedPackageName` | string | Java package name for compilation |
| `functionName` | string | Entry point function name |
| `returnType` | string | Return type of the function |
| `parameters` | array | Parameter definitions |
| `parameters[].name` | string | Parameter name |
| `parameters[].type` | string | Parameter type (language-specific) |
| `customDataStructureNames` | array | Custom types (e.g., `TreeNode`, `ListNode`) |

**Response (202 Accepted):**
```json
{
  "submissionId": "run-550e8400-e29b-41d4-a716-446655440000",
  "status": "QUEUED",
  "message": "Execution queued",
  "queuePosition": 3,
  "estimatedWaitTimeMs": 1500,
  "statusUrl": "/api/v1/execution/status/run-550e8400-e29b-41d4-a716-446655440000",
  "resultsUrl": "/api/v1/execution/results/run-550e8400-e29b-41d4-a716-446655440000"
}
```

---

### 4.2 Endpoint: Get Status (Polling)

**URL:** `GET /api/v1/execution/status/{submissionId}`

**Response (200 OK):**
```json
{
  "submissionId": "run-550e8400-e29b-41d4-a716-446655440000",
  "status": "RUNNING"
}
```

| Status Value | Meaning | Action |
|-------------|---------|--------|
| `QUEUED` | Waiting in queue | Continue polling |
| `COMPILING` | Compilation in progress | Continue polling |
| `RUNNING` | Execution in progress | Continue polling |
| `COMPLETED` | Execution finished | Call `/results` endpoint |
| `FAILED` | Execution failed | Call `/results` for error details |

---

### 4.3 Endpoint: Get Results

**URL:** `GET /api/v1/execution/results/{submissionId}`

**Response (200 OK) - Success:**
```json
{
  "submissionId": "run-550e8400-e29b-41d4-a716-446655440000",
  "status": "COMPLETED",
  "verdict": null,
  "runtimeMs": 145,
  "memoryKb": 25600,
  "errorMessage": null,
  "compilationOutput": null,
  "testCaseResults": [
    {
      "index": 0,
      "passed": null,
      "actualOutput": "[0,1]",
      "expectedOutput": null,
      "executionTimeMs": 12,
      "error": null
    },
    {
      "index": 1,
      "passed": null,
      "actualOutput": "[1,2]",
      "executionTimeMs": 8,
      "error": null
    }
  ],
  "queuedAt": 1706832000000,
  "startedAt": 1706832001000,
  "completedAt": 1706832002000,
  "workerId": "worker-1"
}
```

**Response (200 OK) - Compilation Error:**
```json
{
  "submissionId": "run-550e8400-e29b-41d4-a716-446655440000",
  "status": "FAILED",
  "verdict": null,
  "runtimeMs": null,
  "memoryKb": null,
  "errorMessage": "Compilation failed",
  "compilationOutput": "Solution.java:5: error: cannot find symbol\n    return nums[target];\n           ^\n  symbol:   variable nums\n  location: class Solution",
  "testCaseResults": [],
  "workerId": "worker-1"
}
```

**Response (200 OK) - Runtime Error:**
```json
{
  "submissionId": "run-550e8400-e29b-41d4-a716-446655440000",
  "status": "COMPLETED",
  "runtimeMs": 45,
  "memoryKb": 18000,
  "testCaseResults": [
    {
      "index": 0,
      "actualOutput": null,
      "executionTimeMs": 45,
      "error": "java.lang.ArrayIndexOutOfBoundsException: Index 5 out of bounds for length 4"
    }
  ]
}
```

#### TestCaseResult Field Specifications

| Field | Type | Description |
|-------|------|-------------|
| `index` | int | 0-indexed position matching request order |
| `passed` | boolean | **MUST BE NULL** - CXE does not judge |
| `actualOutput` | string | **REQUIRED** - Raw stdout/return value |
| `expectedOutput` | string | **MUST BE NULL** - Not CXE's concern |
| `executionTimeMs` | long | Execution time for this testcase |
| `error` | string | Error message if execution failed |

---

## 5. Required CXE Changes

### 5.1 MUST DO ✅

| Requirement | Description | Priority |
|-------------|-------------|----------|
| Return `actualOutput` | Every testcase result MUST have `actualOutput` field populated | **CRITICAL** |
| Return `null` for `passed` | CXE MUST NOT compute passed/failed status | **CRITICAL** |
| Return `null` for `verdict` | CXE MUST NOT return a verdict | **CRITICAL** |
| Ignore `expectedOutput` in request | Request may contain `expectedOutput: null`, ignore it | REQUIRED |
| Return per-testcase `executionTimeMs` | Needed for performance analytics | REQUIRED |
| Return per-testcase `error` | Needed for error localization | REQUIRED |
| Preserve testcase order | Results MUST match request order by index | REQUIRED |

### 5.2 MUST NOT DO ❌

| Anti-Requirement | Reason |
|------------------|--------|
| Fetch testcases from DB | Submission Service provides all testcases |
| Compare outputs | Submission Service handles judging |
| Return verdict | Only Submission Service determines verdict |
| Access Entity Service | CXE should have ZERO database dependencies |
| Store execution results | CXE is stateless (may cache briefly for polling) |

### 5.3 SHOULD DO ⚠️

| Recommendation | Benefit |
|----------------|---------|
| Support synchronous mode | Reduces latency for `/run` endpoint |
| Return `workerId` for debugging | Helps trace execution issues |
| Include queue position | Enables wait time estimation |

---

## 6. Integration Flows

### 6.1 RUN Flow (Synchronous User Testing)

```
User clicks "Run" in Frontend
              │
              ▼
      Submission Service
              │
    ┌─────────┴─────────┐
    │                   │
    ▼                   ▼
  CXE Call #1        CXE Call #2
  (User Code)       (Oracle Code)
    │                   │
    ▼                   ▼
  userOutput       oracleOutput
    │                   │
    └─────────┬─────────┘
              │
              ▼
        Compare Outputs
              │
              ▼
    Return RunResponseDto
    {
      verdict: PASSED_RUN | FAILED_RUN,
      testCaseResults: [{
        actualOutput: userOutput,
        expectedOutput: oracleOutput,
        passed: userOutput == oracleOutput
      }]
    }
```

### 6.2 SUBMIT Flow (Async Official Judging)

```
User clicks "Submit" in Frontend
              │
              ▼
      Submission Service
              │
              ├── Create Submission (PENDING)
              │
    ┌─────────┴─────────┐
    │                   │
    ▼                   ▼
  CXE Call #1        CXE Call #2  
  (User Code)       (Oracle Code)
  [HIDDEN tests]    [HIDDEN tests]
    │                   │
    ▼                   ▼
 Poll for results   Poll for results
    │                   │
    ▼                   ▼
  userOutputs      oracleOutputs
    │                   │
    └─────────┬─────────┘
              │
              ▼
        Compare All Outputs
              │
              ▼
        Determine Verdict
        (ACCEPTED | WRONG_ANSWER | etc.)
              │
              ▼
        Update Submission in DB
              │
              ▼
        WebSocket → Frontend
```

### 6.3 Oracle Execution Details

Oracle executions are identified by:
- `submissionId` starting with `oracle-`
- `userId = 0`

```json
{
  "submissionId": "oracle-550e8400-e29b-41d4-a716-446655440000",
  "userId": 0,
  "questionId": 100,
  "language": "JAVA",
  "code": "// Reference solution from ReferenceSolution entity",
  "testCases": [...]
}
```

> [!NOTE]
> CXE should treat oracle executions identically to user executions. The only difference is for logging/analytics purposes.

---

## 7. Error Handling Contract

### 7.1 Error Categories

| Category | CXE Response | Submission Service Action |
|----------|--------------|---------------------------|
| Compilation Error | `compilationOutput` populated, `status: FAILED` | Return `COMPILATION_ERROR` verdict |
| Runtime Error | `testCaseResults[].error` populated | Return `RUNTIME_ERROR` verdict |
| Timeout | `errorMessage: "Timeout"` or `testCaseResults[].error: "Timeout"` | Return `TLE` verdict |
| Memory Limit | `errorMessage` contains "memory" | Return `MLE` verdict |
| Internal Error | `status: FAILED`, generic error | Return `INTERNAL_ERROR`, log for investigation |

### 7.2 HTTP Status Codes

| Status | Meaning | Submission Service Response |
|--------|---------|----------------------------|
| 202 | Accepted, queued | Normal flow, proceed to polling |
| 400 | Bad request | Validation error, return 400 to client |
| 404 | Submission not found | Log error, return 500 to client |
| 429 | Rate limited | Retry with backoff |
| 500 | Internal error | Log error, return 500 to client |
| 503 | Service unavailable | Retry with backoff |

### 7.3 Timeout Handling

| Component | Timeout | Action on Timeout |
|-----------|---------|-------------------|
| HTTP connection | 5s | Retry once |
| Polling | 30s total (60 attempts × 500ms) | Fail with timeout error |
| Single testcase | Configurable per question | Return timeout in `testCaseResults[].error` |

---

## 8. Performance Requirements

### 8.1 Latency SLAs

| Operation | p50 | p95 | p99 |
|-----------|-----|-----|-----|
| Submit (queue) | < 50ms | < 100ms | < 200ms |
| Poll (status) | < 20ms | < 50ms | < 100ms |
| Execution (simple) | < 500ms | < 1s | < 2s |
| Execution (complex) | < 2s | < 5s | < 10s |

### 8.2 Throughput Requirements

| Metric | Minimum | Target |
|--------|---------|--------|
| Concurrent executions | 50 | 200 |
| Requests per second (submit) | 20 | 100 |
| Requests per second (poll) | 200 | 1000 |

### 8.3 Resource Limits

| Resource | Limit per Execution |
|----------|---------------------|
| CPU time | 10s default, configurable |
| Memory | 256MB default, configurable |
| Disk | 0 (no disk access) |
| Network | 0 (no network access) |

---

## 9. Testing Checklist

### 9.1 Pre-Integration Tests (CXE Team)

| Test Case | Expected Result |
|-----------|-----------------|
| Submit with `expectedOutput: null` | Should not fail, ignore field |
| All testcases return `actualOutput` | Non-null for successful execution |
| `passed` field is `null` | CXE does not judge |
| `verdict` field is `null` | CXE does not judge |
| Compilation error returns proper output | `compilationOutput` populated |
| Runtime error returns per-testcase error | `testCaseResults[].error` populated |
| Timeout returns timeout error | `error` contains "timeout" |
| Multiple testcases in batch | All executed, results returned in order |

### 9.2 Integration Tests (Joint Testing)

| Test Case | Steps | Expected |
|-----------|-------|----------|
| Happy path - RUN | Submit valid code, 3 testcases | 3 actualOutputs returned |
| Happy path - Oracle | Submit oracle code, 3 testcases | 3 identical outputs |
| Compilation error | Submit invalid syntax | `compilationOutput` returned |
| Partial failure | 2/3 testcases pass | actualOutput for passed, error for failed |
| Timeout | Infinite loop code | Timeout error after limit |
| Large batch | 50 testcases | All 50 results returned |

### 9.3 Load Tests

| Test | Parameters | Success Criteria |
|------|------------|------------------|
| Sustained load | 20 rps for 10 min | No errors, p99 < 5s |
| Burst | 100 concurrent submissions | Queue properly, no drops |
| Oracle parallel | 50 concurrent oracle executions | Isolated, no interference |

---

## 10. Appendix: Related Documents

### 10.1 Entity Service Changes

See [entity-changes-v2.md](file:///home/hrishabh/codebases/java/leetcode/AlgoCrack-SubmissionService/context/entity-changes-v2.md) for:
- `TestCase` entity changes (removed `expectedOutput`)
- `ReferenceSolution` entity (oracle source code)
- `TestCaseType` enum (`DEFAULT` | `HIDDEN`)

### 10.2 Submission Service Internal Architecture

| File | Purpose |
|------|---------|
| `CxeExecutionAdapter.java` | CXE-specific translation layer |
| `OracleExecutionService.java` | Oracle execution orchestration |
| `ResultValidationService.java` | Output comparison logic |
| `UnifiedExecutionService.java` | /run endpoint handler |

### 10.3 API Documentation

See `frontend-reference.md` for client-facing API documentation.

---

## Document History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2026-02-02 | Submission Service Team | Initial release |

---

## Contact

For questions or clarifications:
- **Submission Service Team** - Integration issues
- **Entity Service Team** - Entity definition questions
- **CXE Team** - Execution engine internals

---

> [!CAUTION]
> **Breaking Change Warning:** CXE MUST NOT return non-null `passed` or `verdict` fields. The Submission Service explicitly ignores these fields and will fail validation if CXE attempts to judge outputs.
