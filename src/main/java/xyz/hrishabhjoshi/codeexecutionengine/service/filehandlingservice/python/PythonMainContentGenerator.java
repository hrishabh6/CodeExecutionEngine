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

@Component
public class PythonMainContentGenerator {

    @Autowired
    private PythonInputContentGenerator inputGenerator;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public String generateMainContent(CodeSubmissionDTO submissionDto) throws JsonProcessingException {
        CodeSubmissionDTO.QuestionMetadata metadata = submissionDto.getQuestionMetadata();
        StringBuilder mainContent = new StringBuilder();

        // Debug logging
        System.out.println("DEBUG: Function name: " + metadata.getFunctionName());
        System.out.println("DEBUG: Return type: " + metadata.getReturnType());
        if (metadata.getParameters() != null) {
            for (var param : metadata.getParameters()) {
                System.out.println("DEBUG: Parameter - Name: " + param.getName() + ", Type: " + param.getType());
            }
        }

        mainContent.append("import solution\n");
        mainContent.append("import time\n");
        mainContent.append("import json\n");
        mainContent.append("from collections import deque\n");
        mainContent.append("from typing import Optional, List\n\n");

        // Track which custom data structures are needed
        Set<String> neededStructures = new HashSet<>();

        // Check parameters for custom data structures
        if (metadata.getParameters() != null) {
            for (var param : metadata.getParameters()) {
                String paramType = param.getType();
                System.out.println("DEBUG: Checking parameter type: " + paramType);
                String extractedType = extractCustomDataStructure(paramType);
                if (extractedType != null) {
                    neededStructures.add(extractedType);
                    System.out.println("DEBUG: Added custom structure: " + extractedType);
                }
            }
        }

        // Check return type for custom data structures
        if (metadata.getReturnType() != null) {
            String extractedType = extractCustomDataStructure(metadata.getReturnType());
            if (extractedType != null) {
                neededStructures.add(extractedType);
                System.out.println("DEBUG: Added return type structure: " + extractedType);
            }
        }

        System.out.println("DEBUG: Total needed structures: " + neededStructures);

        // Import custom data structures from solution module
        if (!neededStructures.isEmpty()) {
            String importStatement = "from solution import " + String.join(", ", neededStructures) + "\n\n";
            mainContent.append(importStatement);
            System.out.println("DEBUG: Added import statement: " + importStatement.trim());
        }

        // Generate helper methods for needed structures (classes are now in solution.py)
        for (String structureName : neededStructures) {
            System.out.println("DEBUG: Generating helper for structure: " + structureName);
            mainContent.append(PythonCustomDSGenerator.generateHelperMethod(structureName));
            System.out.println("DEBUG: Added helper method for: " + structureName);
        }

        mainContent.append("def run_test_cases(solution_instance):\n");
        for (int i = 0; i < submissionDto.getTestCases().size(); i++) {
            Map<String, Object> testCase = submissionDto.getTestCases().get(i);
            String inputJson = objectMapper.writeValueAsString(testCase.get("input"));
            System.out.println("DEBUG: Test case " + i + " input JSON: " + inputJson);

            mainContent.append("    # Test Case ").append(i).append("\n");
            mainContent.append("    start_time_").append(i).append(" = time.time()\n");
            mainContent.append("    try:\n");

            inputGenerator.generateInputVariableDeclarations(mainContent, metadata, inputJson, i);
            String paramNames = inputGenerator.generateFunctionParameters(metadata.getParameters(), i);

            mainContent.append("        result_").append(i).append(" = solution_instance.").append(metadata.getFunctionName()).append("(").append(paramNames).append(")\n");
            generateOutputLogic(mainContent, metadata.getReturnType(), i);
            mainContent.append("    except Exception as e:\n");
            mainContent.append("        end_time_").append(i).append(" = time.time()\n");
            mainContent.append("        duration_").append(i).append(" = int((end_time_").append(i).append(" - start_time_").append(i).append(") * 1000)\n");
            mainContent.append("        print(f'TEST_CASE_RESULT: ").append(i).append(",,{duration_").append(i).append("},' + str(e))\n\n");
        }
        mainContent.append("\n");

        mainContent.append("if __name__ == '__main__':\n");
        mainContent.append("    sol = solution.Solution()\n");
        mainContent.append("    run_test_cases(sol)\n");

        System.out.println("DEBUG: Final generated content:\n" + mainContent.toString());
        return mainContent.toString();
    }

    /**
     * Extracts custom data structure names from complex type declarations
     * Examples:
     * - "Optional[TreeNode]" -> "TreeNode"
     * - "List[ListNode]" -> "ListNode"
     * - "TreeNode" -> "TreeNode"
     * - "Optional[List[Node]]" -> "Node"
     */
    private String extractCustomDataStructure(String type) {
        if (type == null || type.trim().isEmpty()) {
            return null;
        }

        type = type.trim();

        // Pattern to match custom data structures within generic types
        Pattern pattern = Pattern.compile("(ListNode|TreeNode|Node)");
        Matcher matcher = pattern.matcher(type);

        if (matcher.find()) {
            String foundType = matcher.group(1);
            System.out.println("DEBUG: extractCustomDataStructure(" + type + ") = " + foundType);
            return foundType;
        }

        System.out.println("DEBUG: extractCustomDataStructure(" + type + ") = null");
        return null;
    }

    private void generateOutputLogic(StringBuilder builder, String returnType, int testCaseIndex) throws JsonProcessingException {
        builder.append("        end_time_").append(testCaseIndex).append(" = time.time()\n");
        builder.append("        duration_").append(testCaseIndex).append(" = int((end_time_").append(testCaseIndex).append(" - start_time_").append(testCaseIndex).append(") * 1000)\n");
        builder.append("        output_val = None\n");

        // Extract the actual return type from complex declarations
        String actualReturnType = extractCustomDataStructure(returnType);

        if (actualReturnType != null) {
            if (actualReturnType.equalsIgnoreCase("ListNode")) {
                builder.append("        if result_").append(testCaseIndex).append(" is not None:\n");
                builder.append("            output_list = []\n");
                builder.append("            curr = result_").append(testCaseIndex).append("\n");
                builder.append("            while curr:\n");
                builder.append("                output_list.append(curr.val)\n");
                builder.append("                curr = curr.next\n");
                builder.append("            output_val = output_list\n");
            } else if (actualReturnType.equalsIgnoreCase("TreeNode")) {
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
            } else if (actualReturnType.equalsIgnoreCase("Node")) {
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
        } else {
            builder.append("        output_val = result_").append(testCaseIndex).append("\n");
        }

        builder.append("        print(f'TEST_CASE_RESULT: ").append(testCaseIndex).append(",{json.dumps(output_val)},{duration_").append(testCaseIndex).append("},')\n");
    }
}