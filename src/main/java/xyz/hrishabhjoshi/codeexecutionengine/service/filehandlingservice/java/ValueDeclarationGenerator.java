package xyz.hrishabhjoshi.codeexecutionengine.service.filehandlingservice.java;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

public class ValueDeclarationGenerator {

    public static String generateValueDeclaration(String paramType, JsonNode node, Map<String, String> customClasses) throws JsonProcessingException {

        // Handle array types for custom data structures
        if (paramType.endsWith("[]")) {
            String baseType = paramType.substring(0, paramType.length() - 2);
            String extractedType = extractCustomDataStructure(baseType);
            if (extractedType != null) {
                switch (extractedType) {
                    case "ListNode":
                        return "buildListNodeArray(\"" + node.toString().replace("\"", "\\\"") + "\")";
                    case "TreeNode":
                        return "buildTreeNodeArray(\"" + node.toString().replace("\"", "\\\"") + "\")";
                    case "Node":
                        return "buildNodeArray(\"" + node.toString().replace("\"", "\\\"") + "\")";
                }
            }
        }

        // Handle List types for custom data structures
        String extractedType = extractCustomDataStructure(paramType);
        if (extractedType != null) {
            boolean isListType = paramType.startsWith("List<") || paramType.contains("List<");

            switch (extractedType) {
                case "ListNode":
                    if (isListType) {
                        return "buildListNodeList(\"" + node.toString().replace("\"", "\\\"") + "\")";
                    } else {
                        return "buildListNode(\"" + node.toString().replace("\"", "\\\"") + "\", false)";
                    }
                case "TreeNode":
                    if (isListType) {
                        return "buildTreeNodeList(\"" + node.toString().replace("\"", "\\\"") + "\")";
                    } else {
                        return "buildTreeNode(\"" + node.toString().replace("\"", "\\\"") + "\", false)";
                    }
                case "Node":
                    if (isListType) {
                        return "buildNodeList(\"" + node.toString().replace("\"", "\\\"") + "\")";
                    } else {
                        return "buildNode(\"" + node.toString().replace("\"", "\\\"") + "\", false)";
                    }
            }
        }

        // Handle standard types (rest of your existing code remains the same)
        switch (paramType) {
            case "int":
            case "Integer":
                return node.asInt() + "";
            case "double":
            case "Double":
                return node.asDouble() + "";
            case "boolean":
            case "Boolean":
                return node.asBoolean() + "";
            case "String":
                return "\"" + node.asText() + "\"";
            case "int[]":
                return "new int[]{ " + StreamSupport.stream(node.spliterator(), false)
                        .map(JsonNode::toString).collect(Collectors.joining(", ")) + " }";
            case "String[]":
                return "new String[]{ " + StreamSupport.stream(node.spliterator(), false)
                        .map(n -> "\"" + n.asText() + "\"").collect(Collectors.joining(", ")) + " }";
            case "List<Integer>":
                return "List.of(" + StreamSupport.stream(node.spliterator(), false)
                        .map(JsonNode::toString).collect(Collectors.joining(", ")) + ")";
            case "List<String>":
                return "List.of(" + StreamSupport.stream(node.spliterator(), false)
                        .map(n -> "\"" + n.asText() + "\"").collect(Collectors.joining(", ")) + ")";
            case "List<List<Integer>>":
                StringBuilder listOfListsBuilder = new StringBuilder("Arrays.asList(");
                for (int i = 0; i < node.size(); i++) {
                    if (i > 0) listOfListsBuilder.append(", ");
                    JsonNode subArray = node.get(i);
                    listOfListsBuilder.append("List.of(");
                    for (int j = 0; j < subArray.size(); j++) {
                        if (j > 0) listOfListsBuilder.append(", ");
                        listOfListsBuilder.append(subArray.get(j).toString());
                    }
                    listOfListsBuilder.append(")");
                }
                listOfListsBuilder.append(")");
                return listOfListsBuilder.toString();
            case "List<List<String>>":
                StringBuilder listOfStringListsBuilder = new StringBuilder("Arrays.asList(");
                for (int i = 0; i < node.size(); i++) {
                    if (i > 0) listOfStringListsBuilder.append(", ");
                    JsonNode subArray = node.get(i);
                    listOfStringListsBuilder.append("List.of(");
                    for (int j = 0; j < subArray.size(); j++) {
                        if (j > 0) listOfStringListsBuilder.append(", ");
                        listOfStringListsBuilder.append("\"").append(subArray.get(j).asText()).append("\"");
                    }
                    listOfStringListsBuilder.append(")");
                }
                listOfStringListsBuilder.append(")");
                return listOfStringListsBuilder.toString();
            default:
                return node.toString();
        }
    }


    /**
     * Extracts custom data structure names from complex type declarations
     * Examples:
     * - "List<TreeNode>" -> "TreeNode"
     * - "TreeNode" -> "TreeNode"
     * - "List<ListNode>" -> "ListNode"
     * - "Optional<List<Node>>" -> "Node"
     */
    private static String extractCustomDataStructure(String type) {
        if (type == null || type.trim().isEmpty()) {
            return null;
        }

        type = type.trim();

        // Pattern to match custom data structures within generic types
        Pattern pattern = Pattern.compile("(ListNode|TreeNode|Node)");
        Matcher matcher = pattern.matcher(type);

        if (matcher.find()) {
            return matcher.group(1);
        }

        return null;
    }
}