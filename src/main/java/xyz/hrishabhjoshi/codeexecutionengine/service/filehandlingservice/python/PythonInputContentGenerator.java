package xyz.hrishabhjoshi.codeexecutionengine.service.filehandlingservice.python;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import xyz.hrishabhjoshi.codeexecutionengine.dto.CodeSubmissionDTO;
import xyz.hrishabhjoshi.codeexecutionengine.dto.ParamInfoDTO;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

@Component
public class PythonInputContentGenerator {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public void generateInputVariableDeclarations(StringBuilder builder, CodeSubmissionDTO.QuestionMetadata metadata, String inputJson, int testCaseIndex) throws JsonProcessingException {
        Map<String, Object> inputNode = objectMapper.readValue(inputJson, Map.class);
        List<ParamInfoDTO> parameters = metadata.getParameters();

        System.out.println("DEBUG: Input JSON for test case " + testCaseIndex + ": " + inputJson);
        System.out.println("DEBUG: Parsed input node: " + inputNode);

        if (inputNode != null && !inputNode.isEmpty()) {
            for (ParamInfoDTO param : parameters) {
                String paramName = param.getName();
                String paramType = param.getType();
                Object paramValue = inputNode.get(paramName);

                System.out.println("DEBUG: Processing parameter - Name: " + paramName + ", Type: " + paramType + ", Value: " + paramValue);

                if (paramValue != null) {
                    String declarationValue = generateValueDeclaration(paramType, paramValue);
                    builder.append("        ").append(paramName).append("_").append(testCaseIndex).append(" = ").append(declarationValue).append("\n");
                    System.out.println("DEBUG: Generated declaration: " + paramName + "_" + testCaseIndex + " = " + declarationValue);
                } else {
                    builder.append("        ").append(paramName).append("_").append(testCaseIndex).append(" = None\n");
                    System.out.println("DEBUG: Generated null declaration: " + paramName + "_" + testCaseIndex + " = None");
                }
            }
        }
    }

    private String generateValueDeclaration(String paramType, Object value) throws JsonProcessingException {
        if (value == null) {
            return "None";
        }

        // Convert JSON to Python-compatible string (null -> None)
        String jsonValue = objectMapper.writeValueAsString(value).replace("null", "None");

        System.out.println("DEBUG: generateValueDeclaration - Type: " + paramType + ", Value: " + value + ", JSON Value: " + jsonValue);

        // Extract the actual data structure type from generic declarations
        String actualType = extractCustomDataStructure(paramType);

        if (actualType != null) {
            if ("ListNode".equals(actualType)) {
                // Pass the parameter type to help the builder decide what to return
                boolean isListType = paramType.contains("List[");
                String pythonBool = isListType ? "True" : "False";  // Convert to Python boolean
                String result = "build_list_node(" + jsonValue + ", is_list_type=" + pythonBool + ")";
                System.out.println("DEBUG: Generated ListNode call: " + result);
                return result;
            } else if ("TreeNode".equals(actualType)) {
                boolean isListType = paramType.contains("List[");
                String pythonBool = isListType ? "True" : "False";  // Convert to Python boolean
                String result = "build_tree_node(" + jsonValue + ", is_list_type=" + pythonBool + ")";
                System.out.println("DEBUG: Generated TreeNode call: " + result);
                return result;
            } else if ("Node".equals(actualType)) {
                boolean isListType = paramType.contains("List[");
                String pythonBool = isListType ? "True" : "False";  // Convert to Python boolean
                String result = "build_node(" + jsonValue + ", is_list_type=" + pythonBool + ")";
                System.out.println("DEBUG: Generated Node call: " + result);
                return result;
            }
        }

        // For primitive types, return the Python-compatible value directly
        System.out.println("DEBUG: Returning primitive value: " + jsonValue);
        return jsonValue;
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

    public String generateFunctionParameters(List<ParamInfoDTO> parameters, int testCaseIndex) {
        String result = parameters.stream()
                .map(p -> p.getName() + "_" + testCaseIndex)
                .collect(Collectors.joining(", "));
        System.out.println("DEBUG: Generated function parameters: " + result);
        return result;
    }
}