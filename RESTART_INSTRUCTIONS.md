# Service Restart Instructions

## Problem
The service is running the old code (PID 172748) from **before** the memory tracking changes were made.

## How to Fix

### Step 1: Stop the Current Service
```bash
# Find the process
jps -l | grep CodeExecutionEngine
# Output: 172748 xyz.hrishabhjoshi.codeexecutionengine.CodeExecutionEngineApplication

# Kill it
kill 172748

# Or if running in a terminal, press Ctrl+C
```

### Step 2: Rebuild and Restart
```bash
cd /home/hrishabh/codebases/java/leetcode/CodeExecutionEngine

# Build with new changes
./gradlew build -x test

# Start the service
./gradlew bootRun
```

### Step 3: Verify New Code is Running

After restart, submit a test and look for these **new** log messages:

```
MEMORY_STATS: 12.45MiB / 256MiB
MEMORY_PARSED: 12746752 bytes (12451 KB)
CLEANUP: Removed container exec-...
```

If you see these logs, memory tracking is working!

### Step 4: Test Again

Submit your code again and check `/results` endpoint - `memoryKb` should now be populated.

---

## Expected Logs (After Restart)

**Old code** (what you have now):
```
EXECUTION_SERVICE: Docker execution process started.
EXECUTION_SERVICE: Execution completed with exit code: 0
```

**New code** (what you should see):
```
EXECUTION_SERVICE: Docker execution process started.
EXECUTION_SERVICE: Execution completed with exit code: 0
MEMORY_STATS: 12.45MiB / 256MiB          ← NEW
MEMORY_PARSED: 12746752 bytes (12451 KB) ← NEW
CLEANUP: Removed container exec-...      ← NEW
```

---

## Quick Test Script

After restarting, run this to test:

```bash
# Submit code
SUBMISSION_ID=$(curl -s -X POST http://localhost:8081/api/v1/execution/submit \
  -H "Content-Type: application/json" \
  -d '{
    "userId": 1,
    "questionId": 1,
    "language": "java",
    "code": "class Solution { public int[] twoSum(int[] nums, int target) { return new int[]{0,1}; } }",
    "metadata": {
      "functionName": "twoSum",
      "returnType": "int[]",
      "parameters": [
        {"name": "nums", "type": "int[]"},
        {"name": "target", "type": "int"}
      ]
    },
    "testCases": [{"input": {"nums": [2,7,11,15], "target": 9}}]
  }' | jq -r '.submissionId')

echo "Submission ID: $SUBMISSION_ID"

# Wait 3 seconds
sleep 3

# Get results
curl -s "http://localhost:8081/api/v1/execution/results/$SUBMISSION_ID" | jq '.memoryKb, .testCaseResults[0].memoryBytes'
```

Expected output:
```
12451         ← memoryKb (was null before)
12746752      ← memoryBytes (was null before)
```
