// ValueDeclarationGenerator.java

package xyz.hrishabhjoshi.codeexecutionengine.service.filehandlingservice.java;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class ValueDeclarationGenerator {

    public static String generateValueDeclaration(String paramType, JsonNode node, Map<String, String> customClasses) throws JsonProcessingException {
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
            case "ListNode":
                String listNodeClassName = customClasses.getOrDefault("ListNode", "ListNode");
                return "buildListNode(\"" + node.toString().replace("\"", "\\\"") + "\")";
            case "TreeNode":
                String treeNodeClassName = customClasses.getOrDefault("TreeNode", "TreeNode");
                return "buildTreeNode(\"" + node.toString().replace("\"", "\\\"") + "\")";
            case "Node":
                String nodeClassName = customClasses.getOrDefault("Node", "Node");
                return "buildNode(\"" + node.toString().replace("\"", "\\\"") + "\")";
            default:
                return node.toString();
        }
    }
}