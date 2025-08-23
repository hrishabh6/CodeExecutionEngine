package xyz.hrishabhjoshi.codeexecutionengine.service.filehandlingservice.python;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import xyz.hrishabhjoshi.codeexecutionengine.dto.CodeSubmissionDTO;

import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
 * Generates the main.py content for Python code execution.
 *
 * <p>This component creates the main execution file that:</p>
 * <ul>
 *   <li>Imports necessary modules and custom data structures</li>
 *   <li>Generates test case execution logic</li>
 *   <li>Handles input/output conversions for custom data structures</li>
 *   <li>Formats execution results for parsing by the execution service</li>
 * </ul>
 *
 * @author Hrishabhj Joshi
 * @version 1.0
 * @since 1.0
 */
@Component
public class PythonMainContentGenerator {

    private static final Pattern CUSTOM_DS_PATTERN = Pattern.compile("(ListNode|TreeNode|Node)");

    @Autowired
    private PythonInputContentGenerator inputGenerator;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Generates the complete content for the main.py file.
     *
     * <p>The generated content includes:</p>
     * <ul>
     *   <li>Standard Python imports</li>
     *   <li>Custom data structure imports and helper methods</li>
     *   <li>Test case execution logic with timing and error handling</li>
     *   <li>Main execution entry point</li>
     * </ul>
     *
     * @param submissionDto The submission data containing question metadata and test cases
     * @return The complete Python main.py file content as a string
     * @throws JsonProcessingException if JSON processing fails during test case handling
     */
    public String generateMainContent(CodeSubmissionDTO submissionDto) throws JsonProcessingException {
        CodeSubmissionDTO.QuestionMetadata metadata = submissionDto.getQuestionMetadata();
        StringBuilder mainContent = new StringBuilder();

        addStandardImports(mainContent);
        Set<String> neededStructures = identifyRequiredDataStructures(metadata);
        addCustomDataStructureImports(mainContent, neededStructures);
        addHelperMethods(mainContent, neededStructures);
        addTestCaseExecutionLogic(mainContent, submissionDto, metadata);
        addMainExecutionEntry(mainContent);

        return mainContent.toString();
    }

    /**
     * Adds standard Python imports required for execution.
     *
     * @param content The StringBuilder to append imports to
     */
    private void addStandardImports(StringBuilder content) {
        content.append("import solution\n");
        content.append("import time\n");
        content.append("import json\n");
        content.append("from collections import deque\n");
        content.append("from typing import Optional, List\n\n");
    }

    /**
     * Identifies which custom data structures are needed based on parameter and return types.
     *
     * @param metadata The question metadata containing type information
     * @return Set of custom data structure names that are required
     */
    private Set<String> identifyRequiredDataStructures(CodeSubmissionDTO.QuestionMetadata metadata) {
        Set<String> neededStructures = new HashSet<>();

        if (metadata.getParameters() != null) {
            for (var param : metadata.getParameters()) {
                String extractedType = extractCustomDataStructure(param.getType());
                if (extractedType != null) {
                    neededStructures.add(extractedType);
                }
            }
        }

        if (metadata.getReturnType() != null) {
            String extractedType = extractCustomDataStructure(metadata.getReturnType());
            if (extractedType != null) {
                neededStructures.add(extractedType);
            }
        }

        return neededStructures;
    }

    /**
     * Adds imports for custom data structures from the solution module.
     *
     * @param content The StringBuilder to append imports to
     * @param neededStructures Set of required data structure names
     */
    private void addCustomDataStructureImports(StringBuilder content, Set<String> neededStructures) {
        if (!neededStructures.isEmpty()) {
            String importStatement = "from solution import " + String.join(", ", neededStructures) + "\n\n";
            content.append(importStatement);
        }
    }

    /**
     * Adds helper methods for building custom data structures.
     *
     * @param content The StringBuilder to append helper methods to
     * @param neededStructures Set of required data structure names
     */
    private void addHelperMethods(StringBuilder content, Set<String> neededStructures) {
        for (String structureName : neededStructures) {
            content.append(PythonCustomDSGenerator.generateHelperMethod(structureName));
        }
    }

    /**
     * Adds the test case execution logic including input preparation and output handling.
     *
     * @param content The StringBuilder to append execution logic to
     * @param submissionDto The submission data
     * @param metadata The question metadata
     * @throws JsonProcessingException if JSON processing fails
     */
    private void addTestCaseExecutionLogic(StringBuilder content, CodeSubmissionDTO submissionDto,
                                           CodeSubmissionDTO.QuestionMetadata metadata) throws JsonProcessingException {
        content.append("def run_test_cases(solution_instance):\n");

        for (int i = 0; i < submissionDto.getTestCases().size(); i++) {
            Map<String, Object> testCase = submissionDto.getTestCases().get(i);
            String inputJson = objectMapper.writeValueAsString(testCase.get("input"));

            content.append("    # Test Case ").append(i).append("\n");
            content.append("    start_time_").append(i).append(" = time.time()\n");
            content.append("    try:\n");

            inputGenerator.generateInputVariableDeclarations(content, metadata, inputJson, i);
            String paramNames = inputGenerator.generateFunctionParameters(metadata.getParameters(), i);

            content.append("        result_").append(i).append(" = solution_instance.").append(metadata.getFunctionName()).append("(").append(paramNames).append(")\n");
            generateOutputLogic(content, metadata.getReturnType(), i);

            content.append("    except Exception as e:\n");
            content.append("        end_time_").append(i).append(" = time.time()\n");
            content.append("        duration_").append(i).append(" = int((end_time_").append(i).append(" - start_time_").append(i).append(") * 1000)\n");
            content.append("        print(f'TEST_CASE_RESULT: ").append(i).append(",,{duration_").append(i).append("},' + str(e))\n\n");
        }
        content.append("\n");
    }

    /**
     * Adds the main execution entry point.
     *
     * @param content The StringBuilder to append the main entry point to
     */
    private void addMainExecutionEntry(StringBuilder content) {
        content.append("if __name__ == '__main__':\n");
        content.append("    sol = solution.Solution()\n");
        content.append("    run_test_cases(sol)\n");
    }

    /**
     * Extracts custom data structure names from complex type declarations.
     *
     * <p>Handles various type formats including:</p>
     * <ul>
     *   <li>Simple types: "TreeNode" → "TreeNode"</li>
     *   <li>Optional types: "Optional[TreeNode]" → "TreeNode"</li>
     *   <li>List types: "List[ListNode]" → "ListNode"</li>
     *   <li>Nested types: "Optional[List[Node]]" → "Node"</li>
     * </ul>
     *
     * @param type The type string to analyze
     * @return The extracted custom data structure name, or null if none found
     */
    private String extractCustomDataStructure(String type) {
        if (type == null || type.trim().isEmpty()) {
            return null;
        }

        Matcher matcher = CUSTOM_DS_PATTERN.matcher(type.trim());
        return matcher.find() ? matcher.group(1) : null;
    }

    /**
     * Generates output processing logic based on the return type.
     *
     * <p>Different return types require different output processing:</p>
     * <ul>
     *   <li>ListNode: Converts linked list to array representation</li>
     *   <li>TreeNode: Converts tree to level-order array representation</li>
     *   <li>Node: Converts graph to adjacency list representation</li>
     *   <li>Primitive types: Direct JSON serialization</li>
     * </ul>
     *
     * @param builder The StringBuilder to append output logic to
     * @param returnType The function's return type
     * @param testCaseIndex The current test case index
     * @throws JsonProcessingException if JSON processing fails
     */
    private void generateOutputLogic(StringBuilder builder, String returnType, int testCaseIndex) throws JsonProcessingException {
        builder.append("        end_time_").append(testCaseIndex).append(" = time.time()\n");
        builder.append("        duration_").append(testCaseIndex).append(" = int((end_time_").append(testCaseIndex).append(" - start_time_").append(testCaseIndex).append(") * 1000)\n");
        builder.append("        output_val = None\n");

        String actualReturnType = extractCustomDataStructure(returnType);

        if (actualReturnType != null) {
            switch (actualReturnType) {
                case "ListNode":
                    generateListNodeOutputLogic(builder, testCaseIndex);
                    break;
                case "TreeNode":
                    generateTreeNodeOutputLogic(builder, testCaseIndex);
                    break;
                case "Node":
                    generateGraphNodeOutputLogic(builder, testCaseIndex);
                    break;
            }
        } else {
            builder.append("        output_val = result_").append(testCaseIndex).append("\n");
        }

        builder.append("        print(f'TEST_CASE_RESULT: ").append(testCaseIndex).append(",{json.dumps(output_val)},{duration_").append(testCaseIndex).append("},')\n");
    }

    /**
     * Generates output logic for ListNode return types.
     */
    private void generateListNodeOutputLogic(StringBuilder builder, int testCaseIndex) {
        builder.append("        if result_").append(testCaseIndex).append(" is not None:\n");
        builder.append("            output_list = []\n");
        builder.append("            curr = result_").append(testCaseIndex).append("\n");
        builder.append("            while curr:\n");
        builder.append("                output_list.append(curr.val)\n");
        builder.append("                curr = curr.next\n");
        builder.append("            output_val = output_list\n");
    }

    /**
     * Generates output logic for TreeNode return types.
     */
    private void generateTreeNodeOutputLogic(StringBuilder builder, int testCaseIndex) {
        builder.append("        if result_").append(testCaseIndex).append(" is not None:\n");
        builder.append("            output_list = []\n");
        builder.append("            queue = deque([result_").append(testCaseIndex).append("])\n");
        builder.append("            while queue:\n");
        builder.append("                curr = queue.popleft()\n");
        builder.append("                if curr:\n");
        builder.append("                    output_list.append(curr.val)\n");
        builder.append("                    queue.append(curr.left)\n");
        builder.append("                    queue.append(curr.right)\n");
        builder.append("                else:\n");
        builder.append("                    output_list.append(None)\n");
        builder.append("            while output_list and output_list[-1] is None:\n");
        builder.append("                output_list.pop()\n");
        builder.append("            output_val = output_list\n");
    }

    /**
     * Generates output logic for Graph Node return types.
     */
    private void generateGraphNodeOutputLogic(StringBuilder builder, int testCaseIndex) {
        builder.append("        if result_").append(testCaseIndex).append(" is not None:\n");
        builder.append("            adj_list = []\n");
        builder.append("            visited = {}\n");
        builder.append("            queue = deque([result_").append(testCaseIndex).append("])\n");
        builder.append("            visited[result_").append(testCaseIndex).append(".val] = result_").append(testCaseIndex).append("\n");
        builder.append("            while queue:\n");
        builder.append("                curr = queue.popleft()\n");
        builder.append("                for neighbor in curr.neighbors:\n");
        builder.append("                    if neighbor.val not in visited:\n");
        builder.append("                        visited[neighbor.val] = neighbor\n");
        builder.append("                        queue.append(neighbor)\n");
        builder.append("            max_val = max(visited.keys()) if visited else 0\n");
        builder.append("            adj_list = [[] for _ in range(max_val)]\n");
        builder.append("            for node_val, node_obj in visited.items():\n");
        builder.append("                if node_val - 1 < len(adj_list):\n");
        builder.append("                    adj_list[node_val - 1] = [n.val for n in node_obj.neighbors]\n");
        builder.append("            output_val = adj_list\n");
    }
}