# Frontend Integration Guide

> **Version:** 2.0  
> **Updated:** 2026-02-02  
> **Architecture:** Oracle-Based Judging

---

## ⚠️ Important: Frontend Does NOT Call CXE Directly

In the new architecture, **Frontend calls Submission Service**, which orchestrates CXE calls internally.

```
Frontend → Submission Service → CXE (internal)
                  ↓
              Oracle Execution
                  ↓
         Compare & Determine Verdict
                  ↓
              Return to Frontend
```

---

## Key Changes from v1.0

| Aspect | v1.0 (Old) | v2.0 (New) |
|--------|-----------|-----------|
| **Frontend calls** | CXE directly | **Submission Service** |
| **Custom testcases** | Separate `customTestCases` field | Editable DEFAULT testcases |
| **`isCustom` field** | Present in response | **Removed** |
| **Verdict source** | CXE determined | Submission Service determines |
| **Expected output** | Stored in DB | Computed by oracle at runtime |

---

## Testcase Model (New)

### No More "Custom" Testcases
- **DEFAULT testcases**: Visible in UI, user-editable
- **HIDDEN testcases**: Backend-only, used for SUBMIT
- **"Custom"**: Just edited DEFAULT testcases sent from frontend

### Frontend Responsibilities
1. Display DEFAULT testcases from Problem Service
2. Allow users to edit testcases in-place
3. Send edited testcases to Submission Service on RUN
4. Use `nodeType` for tree/graph visualization

---

## CXE Response Contract (For Submission Service)

CXE returns **raw outputs only**. Verdict is determined by Submission Service.

```json
{
  "status": "COMPLETED",
  "verdict": null,
  "testCaseResults": [
    {
      "index": 0,
      "passed": null,
      "actualOutput": "[0,1]",
      "expectedOutput": null,
      "executionTimeMs": 12,
      "memoryBytes": 12746752,
      "error": null
    }
  ]
}
```

| Field | Value | Notes |
|-------|-------|-------|
| `verdict` | `null` | **Always null** - Submission Service determines verdict |
| `passed` | `null` | **Always null** - CXE does not judge |
| `expectedOutput` | `null` | **Always null** - Not CXE's concern |
| `actualOutput` | String | **Required** - Raw output from execution |

---

## For Frontend Developers

### If You Need to Integrate with CXE
**Don't.** Call Submission Service instead. CXE is an internal service.

### If You're Building the Testcase Panel
1. Fetch DEFAULT testcases from Problem Service
2. Render them as editable inputs
3. Send edited testcases in RUN request to Submission Service
4. Submission Service handles oracle comparison and returns verdict

### If You Need Tree/Graph Visualization
Problem Service returns `nodeType`:
```json
{ "nodeType": "TREE_NODE" | "GRAPH_NODE" | "LIST_NODE" | null }
```
Use this to render appropriate visualizations.

---

## Reference Documents

- [cxe-reference.md](file:///home/hrishabh/codebases/java/leetcode/CodeExecutionEngine/context/cxe-reference.md) - Full CXE API contract
- [entity-changes-v2.md](file:///home/hrishabh/codebases/java/leetcode/CodeExecutionEngine/context/entity-changes-v2.md) - Entity Service changes
