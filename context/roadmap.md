# CodeExecutionEngine Enhancement Roadmap

## Current State Analysis

### ✅ **What's Working**
- ✅ Async execution with Redis queue
- ✅ Worker pool pattern (5 workers)
- ✅ Docker isolation for security
- ✅ Custom data structures (ListNode, TreeNode, Node)
- ✅ Multi-language support (Java, Python)
- ✅ Per-test-case timing

### ❌ **Critical Issues**
1. **Memory tracking not implemented** - `memoryKb` always null
2. **Slow execution** - Creates temp dirs, no caching, full Docker lifecycle per submission
3. **No complexity analysis** - Missing Big-O analysis
4. **No percentile tracking** - Cannot compare "your code beats X%"
5. **WebSocket overhead** - Polling should be sufficient

---

## Phased Enhancement Plan

### **Phase 1: Fix Fundamentals (Week 1-2)** 🔴 **CRITICAL**

**Goal**: Get accurate metrics, reduce overhead, basic reliability

#### 1.1 Accurate Memory Tracking
**Priority**: 🔴 P0 (Immediate)
**Effort**: 2 days

**Changes**:
- Integrate Docker stats API (`docker stats --no-stream`)
- Track peak memory per container
- Add `memoryBytes` field to `ExecutionResult.TestCaseOutput`
- Update `SubmissionStatusDto` to aggregate max memory across test cases

**Files to modify**:
- `JavaExecutionService.java` - Add `docker stats` parsing
- `PythonExecutionService.java` - Same for Python
- `ExecutionResult.java` - Add memory fields
- `CodeExecutionResultDTO.java` - Propagate memory data
- `ExecutionWorkerService.java` - Set `memoryKb` in final status

**Implementation approach**:
```java
// Run container with --name flag
docker run --name exec-{submissionId}-{tcIndex} ...

// After execution, get stats
docker stats --no-stream exec-{submissionId}-{tcIndex} --format "{{.MemUsage}}"
// Parse: "12.5MiB / 256MiB" -> extract peak usage
```

---

#### 1.2 Performance Optimizations
**Priority**: 🔴 P0
**Effort**: 3 days

**Sub-tasks**:

**A. Persistent Compilation Cache**
- Use Redis to cache compiled `.class` files by code hash
- Skip compilation if code unchanged
- **Impact**: 2-3x faster for repeated submissions

**B. Reusable Temp Directories**
- Create submission dirs in `/tmp/cxe-pool/`
- Reuse directories after cleanup
- Reduce filesystem I/O by 50%

**C. Docker Container Reuse** (Advanced)
- Keep warm containers in pool
- Reset state between runs
- **Impact**: Eliminate Docker startup overhead (~500ms per run)

**Files**:
- New: `CompilationCacheService.java`
- Modify: `CodeExecutionManager.java`
- Modify: `JavaExecutionService.java`, `PythonExecutionService.java`

---

#### 1.3 Custom Test Cases Support
**Priority**: 🟡 P1
**Effort**: 1 day

**Changes**:
- Accept `customTestCases` in `ExecutionRequest`
- Merge with provided test cases
- Mark which are custom (don't affect verdict)

**Files**:
- `ExecutionRequest.java` - Add field
- `CodeExecutorService.java` - Merge logic
- Frontend integration

---

### **Phase 2: Essential Features (Week 3-4)** 🟡

**Goal**: Make it feel like a real competitive platform

#### 2.1 Complexity Analysis
**Priority**: 🟡 P1
**Effort**: 5 days

**Approach**:
1. **Time Complexity Detection**:
   - Run with increasing input sizes: N=10, 100, 1000, 10000
   - Measure runtime growth
   - Curve fitting to detect O(n), O(n log n), O(n²), etc.

2. **Space Complexity Detection**:
   - Track memory growth with input size
   - Detect O(1), O(n), O(n²) space patterns

**Implementation**:
- New: `ComplexityAnalyzer.java`
- New: `BenchmarkTestGenerator.java` (generates large inputs)
- Modify: `ExecutionRequest.java` - Add `analyzeComplexity` flag
- Return: `{ timeComplexity: "O(n log n)", spaceComplexity: "O(n)" }`

**Algorithm**:
```java
// Run with N = [10, 100, 1000]
// Measure times: [1ms, 15ms, 200ms]
// Ratio: 15/1 = 15x, 200/15 = 13.3x
// If ratio ~ N: O(n)
// If ratio ~ N*log(N): O(n log n)
// If ratio ~ N²: O(n²)
```

---

#### 2.2 Percentile Ranking ("Beats X%")
**Priority**: 🟡 P1
**Effort**: 3 days

**Approach**:
1. Store all submission runtimes/memory per question in DB
2. On new submission, calculate percentile
3. Return: `{ runtimePercentile: 85.3, memoryPercentile: 92.1 }`

**Schema**:
```sql
CREATE TABLE submission_benchmarks (
    question_id BIGINT,
    runtime_ms INT,
    memory_kb INT,
    language VARCHAR(20),
    verdict VARCHAR(50),
    created_at TIMESTAMP,
    INDEX idx_question_runtime (question_id, runtime_ms),
    INDEX idx_question_memory (question_id, memory_kb)
);
```

**Files**:
- New: `SubmissionBenchmarkRepository.java`
- New: `PercentileCalculationService.java`
- Modify: `ExecutionWorkerService.java` - Save benchmarks, calculate percentile
- Return in `SubmissionStatusDto`

---

### **Phase 3: Advanced Features (Week 5-6)** 🟢

#### 3.1 Test Case Quality Validation
**Priority**: 🟢 P2
**Effort**: 3 days

**Features**:
- Validate test cases cover edge cases (empty input, max size, negatives)
- Flag weak test case sets
- Suggest missing edge cases

**Files**:
- New: `TestCaseValidator.java`

---

#### 3.2 Code Quality Metrics
**Priority**: 🟢 P2
**Effort**: 2 days

**Features**:
- Cyclomatic complexity
- Code length
- Use of standard library vs custom implementation

---

### **Phase 4: Infrastructure & Optimization (Week 7)** 🟢

#### 4.1 Remove WebSocket
**Priority**: 🟢 P2
**Effort**: 1 day

**Rationale**: Polling every 1-2s is sufficient, simpler to maintain

**Changes**:
- Remove WebSocket dependencies
- Document polling best practices in API docs

---

#### 4.2 Enhanced Error Messages
**Priority**: 🟢 P2
**Effort**: 2 days

**Features**:
- Parse compilation errors → highlight line numbers
- Provide hints for common mistakes
- Link to documentation

---

## Implementation Priority Matrix

| Task | Impact | Effort | Priority | Week |
|------|--------|--------|----------|------|
| Memory Tracking | 🔴 High | 2d | P0 | 1 |
| Compilation Cache | 🔴 High | 2d | P0 | 1 |
| Temp Dir Reuse | 🟡 Med | 1d | P0 | 1 |
| Custom Test Cases | 🟡 Med | 1d | P1 | 2 |
| Complexity Analysis | 🔴 High | 5d | P1 | 3 |
| Percentile Ranking | 🔴 High | 3d | P1 | 4 |
| Container Reuse | 🟡 Med | 3d | P2 | 5 |
| Remove WebSocket | 🟢 Low | 1d | P2 | 6 |
| Test Quality Check | 🟢 Low | 3d | P2 | 7 |

---

## Recommended Starting Order

### **Sprint 1: Core Metrics (Days 1-4)**
1. ✅ Day 1-2: Implement memory tracking
2. ✅ Day 3-4: Add compilation caching

### **Sprint 2: Performance (Days 5-7)**
3. ✅ Day 5: Temp directory pooling
4. ✅ Day 6-7: Custom test case support

### **Sprint 3: Analytics (Days 8-14)**
5. ✅ Day 8-12: Complexity analysis
6. ✅ Day 13-14: Percentile ranking

### **Sprint 4: Polish (Days 15+)**
7. Remove WebSocket
8. Enhanced error messages
9. Container pooling (advanced)

---

## Technical Debt to Address

1. **No DB caching** - Repeated submissions recompile
2. **Docker overhead** - ~500ms startup per container
3. **No monitoring** - Add Prometheus metrics
4. **No rate limiting** - Add per-user submission throttling
5. **Temp file leaks** - Ensure cleanup on crashes

---

## Success Metrics

**Phase 1 Completion**:
- ✅ `memoryKb` field populated in 100% of responses
- ✅ Average execution time < 2 seconds (from current 5-10s)
- ✅ Custom test cases working

**Phase 2 Completion**:
- ✅ Complexity analysis accurate for O(n), O(n log n), O(n²)
- ✅ Percentile calculation within 5% accuracy
- ✅ "Beats X%" displayed on frontend

**Full Roadmap Completion**:
- ✅ Execution time < 1.5s average
- ✅ 99th percentile < 3s
- ✅ Zero memory tracking failures
- ✅ Code complexity detection for 90%+ submissions

---

## Files Overview

### New Files (Phase 1)
- `service/CompilationCacheService.java`
- `service/DirectoryPoolService.java`

### New Files (Phase 2)
- `service/ComplexityAnalyzer.java`
- `service/BenchmarkTestGenerator.java`
- `service/PercentileCalculationService.java`
- `repository/SubmissionBenchmarkRepository.java`
- `model/SubmissionBenchmark.java`

### Modified Files (Phase 1)
- `JavaExecutionService.java` - Memory tracking
- `PythonExecutionService.java` - Memory tracking
- `ExecutionResult.java` - Add memory fields
- `CodeExecutionResultDTO.java` - Propagate memory
- `SubmissionStatusDto.java` - Display memory
- `ExecutionWorkerService.java` - Use cache, set memory
- `CodeExecutionManager.java` - Use directory pool
- `ExecutionRequest.java` - Custom test cases

---

## Next Steps

**Immediate Action**: Start with **Phase 1.1 (Memory Tracking)** as it's:
- High impact (fixes immediate bug)
- Low risk (isolated change)
- Foundation for percentile tracking

Would you like me to begin implementing memory tracking?
