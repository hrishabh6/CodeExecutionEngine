# Entity Service v2.0 - Architecture Changes

> **Document Version:** 1.0  
> **Last Updated:** 2026-02-02  
> **Status:** Production Ready  
> **Target Audience:** Submission Service Team, Problem Service Team, Frontend Team

---

## Table of Contents

1. [Executive Summary](#1-executive-summary)
2. [Architectural Context](#2-architectural-context)
3. [Entity Changes Summary](#3-entity-changes-summary)
4. [Detailed Entity Specifications](#4-detailed-entity-specifications)
5. [New Enums Reference](#5-new-enums-reference)
6. [Database Schema Changes](#6-database-schema-changes)
7. [Impact on Dependent Services](#7-impact-on-dependent-services)
8. [Migration Strategy](#8-migration-strategy)
9. [Appendix: Complete Entity Definitions](#9-appendix-complete-entity-definitions)

---

## 1. Executive Summary

The Entity Service has undergone a significant architectural restructuring to support **oracle-based judging**. This document details all changes made to the entity layer, the rationale behind each decision, and the implications for dependent services.

### Key Changes at a Glance

| Entity | Change Type | Summary |
|--------|-------------|---------|
| `TestCase` | **Modified** | Removed `expectedOutput`, `isHidden`, `orderIndex`; added `type` enum |
| `ReferenceSolution` | **New** | Oracle entity with 1:1 relationship to Question |
| `Question` | **Modified** | Added `referenceSolution`, `nodeType`; renamed `correctAnswer` |
| `TestCaseType` | **New** | Enum: `DEFAULT`, `HIDDEN` |
| `NodeType` | **New** | Enum: `TREE_NODE`, `GRAPH_NODE`, `LIST_NODE` |

### Core Principle

> **Expected output is never stored. It is computed dynamically using the oracle (reference solution).**

---

## 2. Architectural Context

### 2.1 Previous Architecture (Deprecated)

```
TestCase:
  - input: String
  - expectedOutput: String  ← Stored statically
  - isHidden: Boolean       ← Boolean flag for visibility
  - orderIndex: Integer     ← Presentation state in domain

Judging:
  User Output vs Stored Expected Output
```

**Problems with this approach:**
1. Expected output could become stale if problem logic changed
2. Boolean `isHidden` created dual truth sources when combined with visibility logic
3. `orderIndex` conflated domain state with presentation state
4. No single source of truth for correctness

### 2.2 New Architecture (Oracle-Based)

```
TestCase:
  - input: String
  - type: TestCaseType (DEFAULT | HIDDEN)

ReferenceSolution (Oracle):
  - language: Language
  - sourceCode: String

Judging:
  Execute User Code → Get User Output
  Execute Oracle Code → Get Expected Output
  Compare Outputs → Determine Verdict
```

**Benefits:**
1. Expected output is always fresh (computed at runtime)
2. Semantic `type` enum replaces boolean flag
3. Presentation concerns removed from domain
4. Single source of truth: the oracle code

---

## 3. Entity Changes Summary

### 3.1 Files Added

| File | Purpose | Location |
|------|---------|----------|
| `TestCaseType.java` | Visibility enum for testcases | `models/TestCaseType.java` |
| `NodeType.java` | Visualization hint enum | `models/NodeType.java` |
| `ReferenceSolution.java` | Oracle entity | `models/ReferenceSolution.java` |

### 3.2 Files Modified

| File | Changes Made |
|------|--------------|
| `TestCase.java` | Removed 3 fields, added 1 field |
| `Question.java` | Added 2 fields, renamed 1 field, modified 1 relationship |

### 3.3 Files Unchanged

All other entity files remain unchanged:
- `User.java`
- `Submission.java`
- `Tag.java`
- `Solution.java`
- `QuestionMetadata.java`
- `QuestionStatistics.java`
- `ExecutionMetrics.java`
- `BaseModel.java`
- `Language.java`
- `SubmissionStatus.java`
- `SubmissionVerdict.java`

---

## 4. Detailed Entity Specifications

### 4.1 TestCase Entity

#### 4.1.1 Fields Removed

| Field | Type | Reason for Removal |
|-------|------|--------------------|
| `expectedOutput` | `String` | **Architectural violation.** Expected output must be computed dynamically via oracle, not stored. Storing it creates stale data risk and allows judging bypass. |
| `isHidden` | `Boolean` | **Dual truth source.** Replaced by semantic `TestCaseType` enum. Boolean flags are error-prone and don't communicate intent clearly. |
| `orderIndex` | `Integer` | **Presentation leak.** Ordering is a UI concern, not domain truth. Testcases are editable by users (for RUN mode), making stored order unreliable. Order should be determined by `createdAt` or frontend state. |

#### 4.1.2 Fields Added

| Field | Type | Constraints | Purpose |
|-------|------|-------------|---------|
| `type` | `TestCaseType` | `NOT NULL` | Defines visibility: `DEFAULT` (user-visible, editable, for RUN) or `HIDDEN` (judge-only, for SUBMIT) |

#### 4.1.3 Before/After Comparison

```diff
public class TestCase extends BaseModel {
    private Question question;
    private String input;
-   private String expectedOutput;
-   private Boolean isHidden;
-   private Integer orderIndex;
+   private TestCaseType type;
}
```

#### 4.1.4 Final Schema

| Column | Type | Nullable | Notes |
|--------|------|----------|-------|
| `id` | `BIGINT` | NO | PK, auto-increment |
| `question_id` | `BIGINT` | YES | FK to `question` |
| `input` | `TEXT` | YES | JSON-formatted input |
| `type` | `VARCHAR(10)` | NO | `DEFAULT` or `HIDDEN` |
| `created_at` | `TIMESTAMP` | NO | From BaseModel |
| `updated_at` | `TIMESTAMP` | NO | From BaseModel |

---

### 4.2 ReferenceSolution Entity (NEW)

#### 4.2.1 Purpose

The **oracle** that computes expected output dynamically. Every question MUST have exactly one reference solution. This code is:
- Executed during judging to produce expected output
- Never exposed to users
- Deterministic (same input → same output)
- Maintained by problem authors/moderators

#### 4.2.2 Relationship Design

| Option Considered | Decision | Rationale |
|-------------------|----------|-----------|
| `@OneToMany` (multiple oracles per language) | ❌ Rejected | Adds complexity without MVP value. Multiple languages can be added later. |
| `@OneToOne` (exactly one oracle) | ✅ Chosen | Enforces cardinality at DB level. Simplifies judging logic. |

#### 4.2.3 Field Specification

| Field | Type | Constraints | Purpose |
|-------|------|-------------|---------|
| `question` | `Question` | `NOT NULL`, `UNIQUE` | Owning side of 1:1 relationship |
| `language` | `Language` | `NOT NULL` | Language for oracle execution (JAVA, PYTHON, CPP, JAVASCRIPT) |
| `sourceCode` | `String` | `NOT NULL`, `TEXT` | The oracle code |

#### 4.2.4 Complete Entity Definition

```java
@Entity
@Table(name = "reference_solution")
public class ReferenceSolution extends BaseModel {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", nullable = false, unique = true)
    private Question question;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Language language;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String sourceCode;
}
```

#### 4.2.5 Database Schema

| Column | Type | Nullable | Constraints |
|--------|------|----------|-------------|
| `id` | `BIGINT` | NO | PK, auto-increment |
| `question_id` | `BIGINT` | NO | FK, UNIQUE |
| `language` | `VARCHAR(20)` | NO | Enum value |
| `source_code` | `TEXT` | NO | Oracle code |
| `created_at` | `TIMESTAMP` | NO | From BaseModel |
| `updated_at` | `TIMESTAMP` | NO | From BaseModel |

---

### 4.3 Question Entity

#### 4.3.1 Fields Added

| Field | Type | Constraints | Purpose |
|-------|------|-------------|---------|
| `referenceSolution` | `ReferenceSolution` | Optional (but should be required for judging) | Inverse side of 1:1 relationship to oracle |
| `nodeType` | `NodeType` | Optional | Visualization hint for frontend (tree, graph, list rendering) |

#### 4.3.2 Fields Renamed

| Old Name | New Name | Reason |
|----------|----------|--------|
| `correctAnswer` | `acceptedSubmissions` | **Domain hygiene.** The old name was confusing—it stored `Submission` objects, not answers. New name accurately describes the content. |

#### 4.3.3 Relationship Changes

| Relationship | Old | New | Reason |
|--------------|-----|-----|--------|
| `referenceSolutions` | `@OneToMany List<ReferenceSolution>` | `@OneToOne ReferenceSolution` | Enforces exactly-one cardinality. Prevents 0 or multiple oracles. |
| `testCases` | `@OneToMany` | `@OneToMany orphanRemoval=true` | Ensures testcases are deleted when question is deleted |

#### 4.3.4 Before/After Comparison

```diff
public class Question extends BaseModel {
    private String questionTitle;
    private String questionDescription;
    private List<TestCase> testCases;
    private Boolean isOutputOrderMatters;
-   private List<Submission> correctAnswer;
+   private List<Submission> acceptedSubmissions;
    private List<Tag> tags;
    private String difficultyLevel;
    private String company;
    private String constraints;
    private Integer timeoutLimit;
    private List<Solution> solutions;
-   private List<ReferenceSolution> referenceSolutions;
+   private ReferenceSolution referenceSolution;
    private List<QuestionMetadata> metadataList;
-   private String nodeType;
+   private NodeType nodeType;
}
```

---

## 5. New Enums Reference

### 5.1 TestCaseType

**File:** `models/TestCaseType.java`

| Value | Description | Use Case |
|-------|-------------|----------|
| `DEFAULT` | Visible to users | Shown in testcase panel, editable, used for RUN mode |
| `HIDDEN` | Hidden from users | Used only during SUBMIT for final judging |

```java
public enum TestCaseType {
    DEFAULT,
    HIDDEN
}
```

### 5.2 NodeType

**File:** `models/NodeType.java`

| Value | Description | Frontend Behavior |
|-------|-------------|-------------------|
| `TREE_NODE` | Binary tree or N-ary tree | Render tree visualization |
| `GRAPH_NODE` | Graph (directed or undirected) | Render graph visualization |
| `LIST_NODE` | Linked list | Render linked list visualization |
| `null` | Not applicable | No special visualization |

```java
public enum NodeType {
    TREE_NODE,
    GRAPH_NODE,
    LIST_NODE
}
```

---

## 6. Database Schema Changes

### 6.1 New Tables

#### `reference_solution`

```sql
CREATE TABLE reference_solution (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    question_id BIGINT NOT NULL UNIQUE,
    language VARCHAR(20) NOT NULL,
    source_code TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    FOREIGN KEY (question_id) REFERENCES question(id)
);
```

### 6.2 Modified Tables

#### `test_case`

```sql
-- Add new column
ALTER TABLE test_case ADD COLUMN type VARCHAR(10) NOT NULL DEFAULT 'DEFAULT';

-- Migrate data
UPDATE test_case SET type = 'HIDDEN' WHERE is_hidden = true;
UPDATE test_case SET type = 'DEFAULT' WHERE is_hidden = false OR is_hidden IS NULL;

-- Drop old columns (AFTER verification)
ALTER TABLE test_case DROP COLUMN expected_output;
ALTER TABLE test_case DROP COLUMN is_hidden;
ALTER TABLE test_case DROP COLUMN order_index;
```

#### `question`

```sql
-- Add new column
ALTER TABLE question ADD COLUMN node_type VARCHAR(20);

-- Rename column (if using JPA auto-generation, this may happen automatically)
-- correctAnswer relationship becomes acceptedSubmissions (column name unchanged)
```

---

## 7. Impact on Dependent Services

### 7.1 Submission Service (HIGH IMPACT)

#### Must Implement

| Requirement | Description |
|-------------|-------------|
| **Oracle Execution** | For every submission, execute BOTH user code AND oracle code |
| **Dynamic Comparison** | Compare user output with oracle output (never use stored expected output) |
| **Fetch Testcases by Type** | RUN mode: use frontend-provided testcases; SUBMIT mode: fetch HIDDEN testcases |

#### API Contract Changes

```
POST /submissions
Request:
{
  "questionId": 123,
  "userId": "user-uuid",
  "code": "...",
  "language": "JAVA",
  "mode": "RUN" | "SUBMIT",
  "testcases": [...]  // Only for RUN mode, optional
}
```

#### Judging Flow

```
RUN Mode:
  1. Receive testcases from frontend (edited DEFAULT or custom)
  2. Execute user code against testcases
  3. Execute oracle code against same testcases
  4. Compare outputs
  5. Return results (with expected output if requested)

SUBMIT Mode:
  1. Fetch HIDDEN testcases from DB
  2. Execute user code against all testcases
  3. Execute oracle code against all testcases
  4. Compare outputs
  5. Determine final verdict
  6. Store submission record
```

### 7.2 Problem Service (MEDIUM IMPACT)

#### API Changes Required

| Endpoint | Change |
|----------|--------|
| `GET /problems/{id}` | Return only `DEFAULT` testcases (never return HIDDEN) |
| `POST /problems` | Accept `defaultTestcases[]` and `hiddenTestcases[]` separately |
| `PUT /problems/{id}` | Accept `defaultTestcases[]`, `hiddenTestcases[]`, and `referenceSolution` |

#### Response Structure

```json
{
  "id": 123,
  "title": "Two Sum",
  "description": "...",
  "nodeType": "TREE_NODE",
  "defaultTestcases": [
    { "id": 1, "input": "[2,7,11,15]\n9" }
  ],
  "metadata": { ... }
}
```

> **IMPORTANT:** `hiddenTestcases` and `referenceSolution` are NEVER exposed in GET responses.

### 7.3 Frontend (MEDIUM IMPACT)

#### Changes Required

| Component | Change |
|-----------|--------|
| Testcase Panel | Render `DEFAULT` testcases as editable |
| Custom Testcases | Remove separate tab—editing defaults creates customs |
| Visualization | Use `nodeType` to render tree/graph/list views |
| RUN Request | Send edited testcases to Submission Service |

---

## 8. Migration Strategy

### 8.1 Recommended Order

| Step | Action | Rollback Risk |
|------|--------|---------------|
| 1 | Add `type` column to `test_case` | Low |
| 2 | Backfill `type` from `is_hidden` | Low |
| 3 | Add `node_type` column to `question` | Low |
| 4 | Create `reference_solution` table | Low |
| 5 | Require oracle on new questions | Medium |
| 6 | Drop `expected_output` column | **HIGH** |
| 7 | Drop `is_hidden` column | Medium |
| 8 | Drop `order_index` column | Low |

### 8.2 Backfill Script

```sql
-- Step 2: Backfill type from is_hidden
UPDATE test_case 
SET type = CASE 
    WHEN is_hidden = true THEN 'HIDDEN'
    ELSE 'DEFAULT'
END;
```

### 8.3 Validation Before Dropping Columns

Before dropping `expected_output`:
1. Verify all questions have a `reference_solution`
2. Verify Submission Service is using oracle-based judging
3. Test RUN and SUBMIT flows in staging

---

## 9. Appendix: Complete Entity Definitions

### 9.1 TestCase.java

```java
package com.hrishabh.algocrackentityservice.models;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TestCase extends BaseModel {

    @ManyToOne
    @JoinColumn(name = "question_id")
    private Question question;

    @Column(columnDefinition = "TEXT")
    private String input;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TestCaseType type;
}
```

### 9.2 ReferenceSolution.java

```java
package com.hrishabh.algocrackentityservice.models;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "reference_solution")
public class ReferenceSolution extends BaseModel {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", nullable = false, unique = true)
    private Question question;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Language language;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String sourceCode;
}
```

### 9.3 Question.java (Relevant Fields)

```java
// New/Modified fields only
@OneToOne(mappedBy = "question", cascade = CascadeType.ALL, orphanRemoval = true)
private ReferenceSolution referenceSolution;

@Enumerated(EnumType.STRING)
@Column(length = 20)
private NodeType nodeType;

@OneToMany(mappedBy = "question", cascade = CascadeType.ALL)
private List<Submission> acceptedSubmissions = new ArrayList<>();
```

### 9.4 TestCaseType.java

```java
package com.hrishabh.algocrackentityservice.models;

public enum TestCaseType {
    DEFAULT,
    HIDDEN
}
```

### 9.5 NodeType.java

```java
package com.hrishabh.algocrackentityservice.models;

public enum NodeType {
    TREE_NODE,
    GRAPH_NODE,
    LIST_NODE
}
```

---

## Document History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2026-02-02 | Entity Service Team | Initial release |

---

**For questions or clarifications, contact the Entity Service team.**
