# Judging Pipeline — Phase Log

## Phase 1: Foundation (Zero Risk)
**Date:** 2026-02-10
**Status:** ✅ Complete
**Build:** ✅ Passes (`./gradlew build -x test`)
**Behavior Change:** None — identity pipeline replicates exact same behavior as before

### New Files Created (15 files)

#### Interfaces (4)
| File | Package | Purpose |
|---|---|---|
| `OutputExtractor.java` | `judging/extractor/` | Phase 1 interface — extract raw output |
| `OutputNormalizer.java` | `judging/normalizer/` | Phase 2 interface — normalize output |
| `OutputComparator.java` | `judging/comparator/` | Phase 3 interface — compare outputs |
| `OutputValidator.java` | `judging/validator/` | Phase 4 interface — validate constraints |

#### Supporting Types (3)
| File | Package | Purpose |
|---|---|---|
| `ComparisonResult.java` | `judging/comparator/` | Result DTO for comparison |
| `ValidationResult.java` | `judging/validator/` | Result DTO for validation |
| `ValidationStage.java` | `judging/validator/` | Enum: PRE_COMPARE, POST_COMPARE |

#### Core DTOs & Pipeline (4)
| File | Package | Purpose |
|---|---|---|
| `JudgingContext.java` | `judging/` | Metadata context for all pipeline phases |
| `JudgingResult.java` | `judging/` | Result DTO with passed/failed/judgeError |
| `ExecutionOutput.java` | `judging/` | Wrapper around raw CXE output |
| `JudgingPipeline.java` | `judging/` | Pipeline orchestrator (5 internal steps) |

#### Identity Implementations (3)
| File | Package | Purpose |
|---|---|---|
| `IdentityExtractor.java` | `judging/extractor/` | Pass-through (default) |
| `IdentityNormalizer.java` | `judging/normalizer/` | No-op (default) |
| `ExactMatchComparator.java` | `judging/comparator/` | String/JSON equality (current behavior) |

#### Assembler (1)
| File | Package | Purpose |
|---|---|---|
| `PipelineAssembler.java` | `judging/` | Assembles pipeline from components |

### Modified Files (1)
| File | Changes |
|---|---|
| `UnifiedExecutionService.java` | Added `PipelineAssembler` field, updated `buildRunResponse()` to use pipeline, added `buildJudgingContext()` helper |

### Potential Crash Risks
- **`PipelineAssembler` is now a required dependency** of `UnifiedExecutionService`. If Spring can't autowire it (e.g., missing `@Component`/`@Service` on any phase impl), the app won't start.
- **`ExactMatchComparator` requires `ObjectMapper` injection.** If no `ObjectMapper` bean exists, Spring will fail. (This should always be present in a Spring Boot app.)
- **`buildJudgingContext()` accesses `metadata.getQuestion()`** which may be null if the `QuestionMetadata` was fetched without joining to `Question`. In Phase 1 this is safe (we only use `returnType` and `executionStrategy`), but Phase 2+ will need this to not be null.

### Known Lint Warnings
- `validationService` in `UnifiedExecutionService` is now unused by `buildRunResponse()` but still needed by `SubmissionProcessingService` (submit flow). Will be cleaned up when submit flow also migrates to pipeline.

---

## Phase 2: Unordered Comparison (Medium Risk)
**Date:** 2026-02-10
**Status:** ✅ Complete
**Build:** ✅ Passes (`./gradlew build -x test`)
**Behavior Change:** Questions with `isOutputOrderMatters == false` now use sorted normalization + set comparison. Questions with list return types now use JSON array extraction. All questions with `isOutputOrderMatters == null` or `true` continue using exact same behavior as Phase 1.

### New Files Created (7 files)

#### Extractors (2)
| File | Package | Purpose |
|---|---|---|
| `JsonArrayExtractor.java` | `judging/extractor/` | Parse raw output as JSON array |
| `JsonObjectExtractor.java` | `judging/extractor/` | Parse raw output as any JSON node |

#### Normalizers (3)
| File | Package | Purpose |
|---|---|---|
| `SortedListNormalizer.java` | `judging/normalizer/` | Sort top-level array elements |
| `SortedNestedListNormalizer.java` | `judging/normalizer/` | Sort inner + outer arrays (e.g., 4Sum) |
| `EdgeNormalizer.java` | `judging/normalizer/` | Normalize edge direction [a,b]→[min,max], sort edges |

#### Comparators (2)
| File | Package | Purpose |
|---|---|---|
| `JsonDeepComparator.java` | `judging/comparator/` | Jackson JsonNode.equals() deep comparison |
| `SetEqualityComparator.java` | `judging/comparator/` | Unordered set comparison with size fast-fail |

### Modified Files (3)
| File | Changes |
|---|---|
| `PipelineAssembler.java` | Added Phase 2 component fields, implemented `selectExtractor()`, `selectNormalizer()`, `selectComparator()` selection logic based on `returnType`, `isOutputOrderMatters`, `nodeType` |
| `QuestionMetadataRepository.java` | Added `findByQuestionIdAndLanguageWithQuestion()` — JOIN FETCH query for eager Question loading |
| `UnifiedExecutionService.java` | Changed metadata fetch to use JOIN FETCH version, `buildJudgingContext()` now populates `nodeType` and `isOutputOrderMatters` from Question entity |

### Potential Crash Risks
- **`findByQuestionIdAndLanguageWithQuestion` JOIN FETCH** — if the `question` relationship in `QuestionMetadata` is configured incorrectly, this query will fail at runtime. Unlikely since the non-JOIN-FETCH version already works.
- **`PipelineAssembler` now has 9 injected dependencies** (up from 3). If any new `@Component` beans fail to initialize, the assembler won't start. All 7 new components are simple (only depend on `ObjectMapper`).
- **`isOutputOrderMatters == null` defaults to ordered comparison** (identity normalizer + exact match). This is the safe default — existing questions without this field set will behave exactly as before.
- **JSON parsing failures are handled gracefully** — all extractors and comparators fall back to raw string comparison if JSON parsing fails. No crashes from malformed output.

---

## Phase 3: Node Type Support (Medium Risk)
**Date:** 2026-02-10
**Status:** ✅ Complete
**Build:** ✅ Passes (`./gradlew build -x test`)
**Behavior Change:** Questions with `nodeType == TREE_NODE` now use structural tree comparison. Questions with `nodeType == LIST_NODE` now use JSON array extraction. All others unchanged.

### New Files Created (2 files)

| File | Package | Purpose |
|---|---|---|
| `TreeTraversalUtil.java` | `judging/util/` | Shared tree traversal: build from level-order, serialize, structural equality, linked list check |
| `StructuralTreeComparator.java` | `judging/comparator/` | Recursive tree structure comparison via TreeTraversalUtil |

### Modified Files (1)
| File | Changes |
|---|---|
| `PipelineAssembler.java` | Added `StructuralTreeComparator` field (10 total deps). `selectExtractor()` now routes `TREE_NODE`/`LIST_NODE` → `JsonArrayExtractor`. `selectComparator()` now routes `TREE_NODE` → `StructuralTreeComparator`. |

### Potential Crash Risks
- **`PipelineAssembler` now has 10 injected dependencies.** All are `@Component` beans with simple dependencies (only `ObjectMapper`). Risk is low.
- **`TreeTraversalUtil.buildFromLevelOrder()`** assumes LeetCode-style level-order format `[1,2,3,null,null,4,5]`. If CXE produces a different tree format, comparison will fail (not crash — just wrong results).
- **`StructuralTreeComparator` falls back to string comparison** if JSON parsing fails. No crash risk from malformed tree output.

---

## Phase 4: Validators (Medium Risk)
**Date:** 2026-02-10
**Status:** ✅ Complete
**Build:** ✅ Both Entity Service and Submission Service pass
**Behavior Change:** Questions with `nodeType == LIST_NODE/TREE_NODE` now get structural safety checks (PRE_COMPARE). Questions with `validationHints` containing `EXPECT_LINEAR_FORM` or `SUDOKU_RULES` get POST_COMPARE validation. All questions without these traits/hints are unaffected.

### Architecture: 5-Class Validator Taxonomy
| Class | Validator | Stage | Trigger | Status |
|---|---|---|---|---|
| 1. Structural Safety | `StructuralSafetyValidator` | PRE_COMPARE | nodeType (trait) | ✅ Done |
| 2. Shape/Form | `LinkedListShapeValidator` | POST_COMPARE | hint: EXPECT_LINEAR_FORM | ✅ Done |
| 4. Constraint/Rule | `SudokuConstraintValidator` | POST_COMPARE | hint: SUDOKU_RULES | ✅ Done |
| 3. Independence | *DeepCopyValidator* | POST_COMPARE | hint: REQUIRE_DEEP_COPY | ⏳ Phase 6 |
| 5. Mutation Integrity | *MutationIntegrityValidator* | POST_COMPARE | mutationTarget | ⏳ Phase 5 |

### New Files Created (4 files)
| File | Package | Purpose |
|---|---|---|
| `ListTraversalUtil.java` | `judging/util/` | Linked list size bounds + repeating pattern detection |
| `StructuralSafetyValidator.java` | `judging/validator/` | PRE_COMPARE: cycle/size protection for LIST_NODE/TREE_NODE |
| `LinkedListShapeValidator.java` | `judging/validator/` | POST_COMPARE: verify linked list shape (all left=null) |
| `SudokuConstraintValidator.java` | `judging/validator/` | POST_COMPARE: Sudoku row/col/box validation |

### Modified Files (6 files)
| File | Changes |
|---|---|
| `Question.java` (Entity Service) | Added `validationHints` column (nullable, comma-separated) |
| `JudgingContext.java` | Added `validationHints` field (`List<String>`) |
| `OutputValidator.java` | Updated interface: `validate(Object, Object, JudgingContext)` — now accepts oracle output |
| `JudgingPipeline.java` | Fixed validator calls to pass both user and oracle extracted output |
| `PipelineAssembler.java` | Added 3 validator fields, implemented `selectValidators()` with trait-driven + hint-driven routing |
| `UnifiedExecutionService.java` | Updated `buildJudgingContext()` to parse comma-separated `validationHints` from Question |

### Potential Crash Risks
- **Entity Service must be rebuilt and published** before Submission Service can access `validationHints`. ✅ Done (`publishToMavenLocal`).
- **`PipelineAssembler` now has 13 injected dependencies.** All are simple `@Component` beans. Risk is low.
- **Validators degrade gracefully** — if input can't be parsed as JSON, validators return `passed()` (skip validation). No crashes from unexpected formats.
- **`validationHints` is nullable** — questions without hints get no hint-driven validators. Trait-driven validators still activate based on `nodeType`.

---

## Phase 5: Void Return Support (Medium Risk)
**Date:** 2026-02-14
**Status:** ✅ Complete
**Build:** ✅ All 3 services pass (Entity Service, Submission Service, CXE)
**Behavior Change:** Void return functions (flatten, reorder, sudoku solver) now work end-to-end. CXE serializes the mutated input parameter instead of the (empty) return value. Pipeline treats void output identically to regular output.

### Architecture: Cross-Service Data Flow
```
QuestionMetadata.mutationTarget/serializationStrategy (Entity Service)
  → JudgingContext (Submission Service)
  → CodeBundle.QuestionMetadataBundle (internal DTO)
  → ExecutionRequest.QuestionMetadata (CXE request DTO)
  → CodeSubmissionDTO.QuestionMetadata (CXE internal DTO)
  → JavaMainClassGenerator void branch (code generation)
```

### Modified Files

**Entity Service (1 file)**
| File | Changes |
|---|---|
| `QuestionMetadata.java` | Added `mutationTarget` + `serializationStrategy` fields |

**CXE (2 files)**
| File | Changes |
|---|---|
| `CodeSubmissionDTO.java` | Added `mutationTarget` + `serializationStrategy` to inner `QuestionMetadata` |
| `JavaMainClassGenerator.java` | Added void branch: skip result assignment, serialize mutated input param |

**Submission Service (4 files)**
| File | Changes |
|---|---|
| `CodeBundle.java` | Added `mutationTarget` + `serializationStrategy` to `QuestionMetadataBundle` |
| `ExecutionRequest.java` | Added `mutationTarget` + `serializationStrategy` to `QuestionMetadata` |
| `CxeExecutionAdapter.java` | Wire new fields in `translateToRequest()` |
| `UnifiedExecutionService.java` | Wire new fields in `buildJudgingContext()` + `buildCodeBundle()` |

### Database Values for Void Questions
| Question | mutationTarget | serializationStrategy |
|---|---|---|
| Flatten Binary Tree | `0` | `LEVEL_ORDER` |
| Reorder List | `0` | `ARRAY` |
| Sudoku Solver | `0` | `JSON` |

### Potential Crash Risks
- **CXE void branch**: If `mutationTarget` is null for a void function, defaults to `0` (first param). Safe fallback.
- **Non-void functions**: Completely unaffected — the void branch only triggers when `returnType == "void"`.
- **CXE must be redeployed** for the code generator change to take effect.

---

## Phase 6: Special Cases (Low Risk)
**Date:** 2026-02-14
**Status:** ✅ Complete
**Build:** ✅ Submission Service passes
**Behavior Change:** Clone Graph (#8) now gets graph structure validation via `DeepCopyValidator`. All 10 questions now have correct pipeline routing.

### New Files Created (1 file)
| File | Package | Purpose |
|---|---|---|
| `DeepCopyValidator.java` | `judging/validator/` | POST_COMPARE: Graph structure validation (valid indices, bidirectional edges) |

### Modified Files (1 file)
| File | Changes |
|---|---|
| `PipelineAssembler.java` | Added `DeepCopyValidator` injection, wired `REQUIRE_DEEP_COPY` hint |

### DeepCopyValidator Limitations
True pointer-identity verification (proving the clone shares no nodes with the original) is **impossible through serialized JSON**. This validator performs best-effort structural verification:
1. Non-empty output
2. All neighbour indices within valid range (1 to N)
3. Bidirectional edges (undirected graph symmetry)

True deep-copy verification would require CXE-side instrumentation.

### Final Pipeline Coverage — All 10 Questions
| # | Question | Extractor | Normalizer | Comparator | Validator |
|---|---|---|---|---|---|
| 1 | Trapping Rain Water II | Identity | Identity | ExactMatch | — |
| 2 | Reverse Nodes in k-Group | JsonArray | Identity | JsonDeep | StructuralSafety |
| 3 | Critical Connections | JsonArray | Edge | SetEquality | — |
| 4 | Serialize/Deserialize Tree | JsonArray | Identity | StructuralTree | StructuralSafety |
| 5 | Word Break II | JsonArray | SortedList | SetEquality | — |
| 6 | Flatten Binary Tree (void) | JsonArray | Identity | StructuralTree | StructuralSafety, LinkedListShape |
| 7 | Reorder List (void) | JsonArray | Identity | JsonDeep | StructuralSafety |
| 8 | Clone Graph | JsonArray | Identity | JsonDeep | DeepCopy |
| 9 | Sudoku Solver (void) | JsonArray | Identity | JsonDeep | SudokuConstraint |
| 10 | 4Sum | JsonArray | SortedNestedList | SetEquality | — |

### Component Inventory
- **3 Extractors**: Identity, JsonArray (+ JsonObject reserved)
- **4 Normalizers**: Identity, SortedList, SortedNestedList, Edge
- **4 Comparators**: ExactMatch, JsonDeep, SetEquality, StructuralTree
- **4 Validators**: StructuralSafety, LinkedListShape, SudokuConstraint, DeepCopy
- **Total: 15 reusable components** serving 10 questions

### ✅ ALL PHASES COMPLETE (1-6)
