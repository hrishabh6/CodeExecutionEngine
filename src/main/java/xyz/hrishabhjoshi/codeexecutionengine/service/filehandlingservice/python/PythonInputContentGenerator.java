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

/**
 * Generates Python input variable declarations and function parameters for test cases.
 *
 * <p>This component handles the conversion of JSON test case inputs into Python
 * variable declarations, including proper handling of custom data structures
 * like ListNode, TreeNode, and Graph Node.</p>
 *
 * @author Hrishabhj Joshi
 * @version 1.0
 * @since 1.0
 */
@Component
public class PythonInputContentGenerator {

    private static final Pattern CUSTOM_DS_PATTERN = Pattern.compile("(ListNode|TreeNode|Node)");

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Generates Python variable declarations for test case inputs.
     *
     * <p>This method processes each parameter in the function signature and creates
     * corresponding Python variable declarations with proper type conversions.
     * Custom data structures are handled using builder helper methods.</p>
     *
     * @param builder The StringBuilder to append variable declarations to
     * @param metadata The question metadata containing parameter information
     * @param inputJson The JSON string containing test case input values
     * @param testCaseIndex The index of the current test case
     * @throws JsonProcessingException if JSON parsing fails
     */
    public void generateInputVariableDeclarations(StringBuilder builder, CodeSubmissionDTO.QuestionMetadata metadata,
                                                  String inputJson, int testCaseIndex) throws JsonProcessingException {
        Map<String, Object> inputNode = objectMapper.readValue(inputJson, Map.class);
        List<ParamInfoDTO> parameters = metadata.getParameters();

        if (inputNode != null && !inputNode.isEmpty()) {
            for (ParamInfoDTO param : parameters) {
                String paramName = param.getName();
                String paramType = param.getType();
                Object paramValue = inputNode.get(paramName);

                if (paramValue != null) {
                    String declarationValue = generateValueDeclaration(paramType, paramValue);
                    builder.append("        ").append(paramName).append("_").append(testCaseIndex).append(" = ").append(declarationValue).append("\n");
                } else {
                    builder.append("        ").append(paramName).append("_").append(testCaseIndex).append(" = None\n");
                }
            }
        }
    }

    /**
     * Generates appropriate Python value declarations based on parameter type and value.
     *
     * <p>This method handles different types of values:</p>
     * <ul>
     *   <li>Null values: converted to Python None</li>
     *   <li>Custom data structures: converted using builder helper methods</li>
     *   <li>Primitive types: converted to Python-compatible JSON</li>
     * </ul>
     *
     * @param paramType The parameter type (may include generics like List[TreeNode])
     * @param value The actual value from the test case input
     * @return Python code string for declaring the variable with the given value
     * @throws JsonProcessingException if JSON processing fails
     */
    private String generateValueDeclaration(String paramType, Object value) throws JsonProcessingException {
        if (value == null) {
            return "None";
        }

        String jsonValue = objectMapper.writeValueAsString(value).replace("null", "None");
        String actualType = extractCustomDataStructure(paramType);

        if (actualType != null) {
            boolean isListType = paramType.contains("List[");
            String pythonBool = isListType ? "True" : "False";

            switch (actualType) {
                case "ListNode":
                    return "build_list_node(" + jsonValue + ", is_list_type=" + pythonBool + ")";
                case "TreeNode":
                    return "build_tree_node(" + jsonValue + ", is_list_type=" + pythonBool + ")";
                case "Node":
                    return "build_node(" + jsonValue + ", is_list_type=" + pythonBool + ")";
            }
        }

        return jsonValue;
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
     * Generates comma-separated function parameter names for test case execution.
     *
     * <p>Creates parameter names in the format: param1_0, param2_0, param3_0
     * where the number suffix corresponds to the test case index.</p>
     *
     * @param parameters List of parameter information from the function signature
     * @param testCaseIndex The index of the current test case
     * @return Comma-separated string of parameter names for function call
     */
    public String generateFunctionParameters(List<ParamInfoDTO> parameters, int testCaseIndex) {
        return parameters.stream()
                .map(p -> p.getName() + "_" + testCaseIndex)
                .collect(Collectors.joining(", "));
    }
}