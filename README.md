# Code Execution Engine Documentation

## Overview

The Code Execution Engine provides a robust and secure solution for executing user-submitted code with test cases. It's designed as a Spring Boot service that can be easily integrated into your application for code evaluation, testing, and validation purposes.

## 🚀 Code Execution Manager

The `CodeExecutionManager` is the primary entry point for submitting and executing user code. It orchestrates the entire process, from generating source files to running the code in a secure environment and cleaning up temporary resources. This class is designed as a `@Service` and can be easily autowired into other components of your application.

### Usage

To use the `CodeExecutionManager`, simply autowire it into your service or controller and call the `runCodeWithTestcases` method.

```java
import xyz.hrishabhjoshi.codeexecutionengine.CodeExecutionManager;
import xyz.hrishabhjoshi.codeexecutionengine.dto.CodeSubmissionDTO;
import xyz.hrishabhjoshi.codeexecutionengine.dto.CodeExecutionResultDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CodeSubmissionController {

    @Autowired
    private CodeExecutionManager codeExecutionManager;

    @PostMapping("/submit")
    public CodeExecutionResultDTO submitCode(@RequestBody CodeSubmissionDTO submission) {
        // You can add logging here to see the execution flow
        return codeExecutionManager.runCodeWithTestcases(submission, System.out::println);
    }
}
```

## 📋 DTO Structures

The `runCodeWithTestcases` method expects a `CodeSubmissionDTO` object. This DTO is the central payload that contains all the information needed for the execution engine.

### CodeSubmissionDTO

This class encapsulates the user's code, test cases, and metadata about the problem.

```java
public class CodeSubmissionDTO {
    private String userSolutionCode;
    private List<Map<String, Object>> testCases;
    private QuestionMetadata questionMetadata;
    private String language;
    private String submissionId;

    // Getters and Setters
}
```

#### Properties

| Property | Type | Description |
|----------|------|-------------|
| `userSolutionCode` | `String` | A string containing the user's solution. This should include the Solution class and any custom data structures they define. |
| `testCases` | `List<Map<String, Object>>` | A list of maps, where each map represents a test case. The input for the test case should be under the key "input". |
| `questionMetadata` | `QuestionMetadata` | An object containing metadata about the problem, essential for dynamic code generation. |
| `language` | `String` | The programming language of the submission (e.g., "java"). |
| `submissionId` | `String` | An optional unique ID for the submission. If not provided, a random UUID will be generated. |

### QuestionMetadata

This object provides the necessary context for the code generator.

```java
public class QuestionMetadata {
    private String fullyQualifiedPackageName;
    private String returnType;
    private String functionName;
    private List<ParamInfoDTO> parameters;
    private Map<String, String> customDataStructureNames;
    
    // Getters and Setters
}
```

#### Properties

| Property | Type | Description |
|----------|------|-------------|
| `fullyQualifiedPackageName` | `String` | The package where the generated code should be placed (e.g., "com.algocrack.solution.q8"). |
| `returnType` | `String` | The return type of the user's function (e.g., "Node", "int", "List<Integer>"). |
| `functionName` | `String` | The name of the method to be called on the Solution class (e.g., "cloneGraph"). |
| `parameters` | `List<ParamInfoDTO>` | A list of ParamInfoDTO objects defining the type and name of each function parameter. |
| `customDataStructureNames` | `Map<String, String>` | A map that tells the generator which custom classes to handle. The key is the generic name (e.g., "Node"), and the value is the specific class name to be used (e.g., "Node"). |

### ParamInfoDTO

This object describes a single parameter of the user's function.

```java
public class ParamInfoDTO {
    private String name;
    private String type;
    
    // Getters and Setters
}
```

#### Properties

| Property | Type | Description |
|----------|------|-------------|
| `name` | `String` | The parameter name |
| `type` | `String` | The parameter type |

## 💡 Example Usage

### Clone Graph Problem Example

To submit a solution for a problem like "Clone Graph," where a custom `Node` data structure is required, your `CodeSubmissionDTO` JSON should look like this:

```json
{
  "submissionId": "unique-submission-id-123",
  "language": "java",
  "userSolutionCode": "import com.fasterxml.jackson.annotation.JsonProperty;\\nimport java.util.*;\\n\\nclass Node {\\n    @JsonProperty\\n    public int val;\\n    @JsonProperty\\n    public List<Node> neighbors;\\n\\n    // ... constructors\\n}\\n\\nclass Solution {\\n    public Node cloneGraph(Node node) {\\n        // ... user's complete solution code ...\\n    }\\n}",
  "testCases": [
    {
      "input": {
        "node": "[[2,4],[1,3],[2,4],[1,3]]"
      }
    },
    {
      "input": {
        "node": "[]"
      }
    }
  ],
  "questionMetadata": {
    "fullyQualifiedPackageName": "com.algocrack.solution.q8",
    "returnType": "Node",
    "functionName": "cloneGraph",
    "parameters": [
      {
        "name": "node",
        "type": "Node"
      }
    ],
    "customDataStructureNames": {
      "Node": "Node"
    }
  }
}
```

## 🔧 Key Features

- **Secure Execution**: Runs code in an isolated environment with proper security measures
- **Multiple Language Support**: Currently supports Java with extensibility for other languages
- **Custom Data Structures**: Handles complex data structures like custom classes and nodes
- **Test Case Validation**: Automatically runs submitted code against provided test cases
- **Dynamic Code Generation**: Generates necessary boilerplate code for execution
- **Resource Management**: Automatic cleanup of temporary files and resources
- **Spring Integration**: Seamlessly integrates with Spring Boot applications

## 🛡️ Security Considerations

The Code Execution Engine is designed with security as a primary concern. It provides:

- Isolated execution environments
- Resource limitation and monitoring
- Temporary file cleanup
- Secure code compilation and execution

## 🔄 Workflow

1. **Submission**: Code is submitted via the `CodeSubmissionDTO`
2. **Validation**: Input validation and metadata processing
3. **Code Generation**: Dynamic generation of execution wrapper code
4. **Compilation**: Secure compilation of user code
5. **Execution**: Running code against test cases in isolated environment
6. **Results**: Collection and formatting of execution results
7. **Cleanup**: Automatic cleanup of temporary resources

---

*This documentation covers the core functionality of the Code Execution Engine. For additional features or advanced configuration options, please refer to the complete API documentation.*