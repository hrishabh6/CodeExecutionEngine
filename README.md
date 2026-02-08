# 🚀 Code Execution Engine (CXE)

A high-performance, production-ready microservice for securely compiling and executing user-submitted code against test cases. Built for LeetCode-style competitive programming platforms.

[![Java](https://img.shields.io/badge/Java-17+-orange.svg)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-green.svg)](https://spring.io/projects/spring-boot)
[![Docker](https://img.shields.io/badge/Docker-Required-blue.svg)](https://www.docker.com/)
[![Redis](https://img.shields.io/badge/Redis-7+-red.svg)](https://redis.io/)

## 📋 Overview

CXE is a standalone microservice that handles the complete lifecycle of code execution:

1. **Receives** code submissions via REST API
2. **Generates** test harness code dynamically
3. **Compiles** user code securely
4. **Executes** in isolated Docker containers
5. **Returns** execution results with runtime/memory metrics

### Key Features

- 🔒 **Secure Sandbox Execution** - Docker-based isolation with resource limits
- ⚡ **Async Queue Processing** - Redis-backed job queue with worker pool
- 🧠 **Memory Tracking** - Real-time memory usage measurement
- 🎯 **Multi-Language Support** - Java & Python (extensible)
- 📊 **Per-Test-Case Metrics** - Individual timing and output for each test
- 🔄 **LeetCode-Compatible** - Automatic imports, custom data structures (ListNode, TreeNode, Node)

## 🏗️ Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    Submission Service                        │
│                   (External Consumer)                        │
└─────────────────────────┬───────────────────────────────────┘
                          │ HTTP POST /submit
                          ▼
┌─────────────────────────────────────────────────────────────┐
│                  Code Execution Engine                       │
│  ┌───────────────┐    ┌───────────────┐    ┌─────────────┐  │
│  │  Controller   │───▶│  Redis Queue  │───▶│   Workers   │  │
│  │  (REST API)   │    │  (Job Queue)  │    │   (Pool)    │  │
│  └───────────────┘    └───────────────┘    └──────┬──────┘  │
│                                                    │         │
│  ┌───────────────────────────────────────────────┐│         │
│  │              File Generators                   ││         │
│  │  ┌─────────────┐  ┌─────────────────────────┐ ││         │
│  │  │ Main.java   │  │ Solution.java           │ ││         │
│  │  │ (Harness)   │  │ (User Code + DS)        │ ││         │
│  │  └─────────────┘  └─────────────────────────┘ ││         │
│  └───────────────────────────────────────────────┘│         │
│                                                    ▼         │
│  ┌─────────────────────────────────────────────────────────┐│
│  │              Docker Container Execution                  ││
│  │  • Compile (javac / python syntax check)                ││
│  │  • Run with timeout and memory limits                   ││
│  │  • Parse TEST_CASE_RESULT output                        ││
│  └─────────────────────────────────────────────────────────┘│
└─────────────────────────────────────────────────────────────┘
```

## 🚦 Quick Start

### Prerequisites

- **Java 17+**
- **Docker** (with `docker` command accessible)
- **Redis** (or use docker-compose)

### 1. Pull the Runtime Image

```bash
docker pull hrishabhjoshi/my-java-runtime:17
```

### 2. Start Dependencies

```bash
docker-compose up -d redis
```

### 3. Run the Application

```bash
./gradlew bootRun
```

The service starts on `http://localhost:8081`

## 📡 API Reference

### Submit Code for Execution

```http
POST /submit
Content-Type: application/json
```

**Request Body:**
```json
{
  "submissionId": "run-12345",
  "userId": 1,
  "questionId": 42,
  "language": "JAVA",
  "code": "class Solution {\n    public int[] twoSum(int[] nums, int target) {\n        // solution\n    }\n}",
  "metadata": {
    "functionName": "twoSum",
    "returnType": "int[]",
    "parameters": [
      { "name": "nums", "type": "int[]" },
      { "name": "target", "type": "int" }
    ]
  },
  "testCases": [
    { "input": [[2,7,11,15], 9] },
    { "input": [[3,2,4], 6] }
  ]
}
```

**Response (202 Accepted):**
```json
{
  "submissionId": "run-12345",
  "status": "QUEUED",
  "message": "Submission queued for execution"
}
```

### Poll Execution Status

```http
GET /status/{submissionId}
```

**Response:**
```json
{
  "submissionId": "run-12345",
  "status": "COMPLETED",
  "runtimeMs": 15,
  "memoryKb": 21340,
  "testCaseResults": [
    {
      "index": 0,
      "passed": null,
      "actualOutput": "[0,1]",
      "executionTimeMs": 3
    }
  ]
}
```

### Get Full Results

```http
GET /result/{submissionId}
```

## 🔧 Configuration

Key settings in `application.yml`:

```yaml
cxe:
  worker:
    count: 4                    # Number of parallel workers
  execution:
    timeout-seconds: 10         # Max execution time per submission
    memory-limit-mb: 256        # Container memory limit
  docker:
    image: hrishabhjoshi/my-java-runtime:17
```

## 📁 Project Structure

```
src/main/java/xyz/hrishabhjoshi/codeexecutionengine/
├── controller/
│   └── ExecutionController.java      # REST API endpoints
├── service/
│   ├── helperservice/
│   │   ├── ExecutionQueueService.java    # Redis queue operations
│   │   └── ExecutionWorkerService.java   # Worker thread pool
│   └── filehandlingservice/
│       ├── java/
│       │   ├── JavaFileGenerator.java         # Main + Solution file gen
│       │   ├── JavaMainClassGenerator.java    # Test harness generator
│       │   ├── JavaSolutionClassGenerator.java # User code wrapper
│       │   ├── InputVariableGenerator.java    # Input parsing
│       │   └── ValueDeclarationGenerator.java # Type-specific codegen
│       └── python/
│           └── PythonFileGenerator.java
├── dto/
│   ├── ExecutionRequest.java        # Incoming request DTO
│   └── ExecutionStatus.java         # Status response DTO
└── CodeExecutionManager.java        # Core execution orchestrator
```

## 🧩 Supported Types

### Java Types

| Type | Example Input | Generated Code |
|------|--------------|----------------|
| `int` | `42` | `int x = 42;` |
| `int[]` | `[1,2,3]` | `int[] x = new int[]{1, 2, 3};` |
| `int[][]` | `[[1,2],[3,4]]` | `int[][] x = new int[][]{{1,2},{3,4}};` |
| `String` | `"hello"` | `String x = "hello";` |
| `String[]` | `["a","b"]` | `String[] x = new String[]{"a", "b"};` |
| `char[][]` | `[["a","b"]]` | `char[][] x = new char[][]{{'a','b'}};` |
| `ListNode` | `[1,2,3]` | Auto-generates linked list construction |
| `TreeNode` | `[1,2,3,null,4]` | Auto-generates binary tree construction |
| `Node` | `[[2,4],[1,3]]` | Auto-generates graph construction |

### Python Types

| Type | Example Input |
|------|--------------|
| `int` | `42` |
| `List[int]` | `[1,2,3]` |
| `List[List[int]]` | `[[1,2],[3,4]]` |
| `str` | `"hello"` |
| `ListNode` | `[1,2,3]` |
| `TreeNode` | `[1,2,3,null,4]` |

## 🔒 Security

- **Docker Isolation**: Each execution runs in a disposable container
- **Resource Limits**: Memory and CPU constraints prevent abuse
- **Timeout Enforcement**: Hard kill after timeout to prevent infinite loops
- **No Network Access**: Containers run without network by default
- **Temp Cleanup**: All generated files are deleted after execution

## 🧪 Development

### Run Tests

```bash
./gradlew test
```

### Build

```bash
./gradlew clean build
```

### Local Development with All Dependencies

```bash
docker-compose up -d
./gradlew bootRun
```

## 📊 Monitoring

- **Redis Commander**: `http://localhost:8085` (enable with `--profile dev-tools`)
- **Application Logs**: See `application.yml` for log level configuration

## 🤝 Integration

CXE is designed to be consumed by a **Submission Service** which handles:
- User authentication
- Oracle (reference solution) execution
- Result comparison and verdict determination
- Database persistence

See `context/frontend-integration-guide.md` for full integration details.

## 📄 License

This project is licensed under the Apache License 2.0 - see the [LICENSE](LICENSE) file for details.

---

**Part of the AlgoCrack Platform** - A modern competitive programming platform.
