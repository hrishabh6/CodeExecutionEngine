package xyz.hrishabhjoshi.codeexecutionengine.service.filehandlingservice.java;

import java.util.Set;

public class JavaCodeHelper {

    private static final Set<String> PRIMITIVE_TYPES = Set.of(
            "int", "Integer", "double", "Double", "boolean", "Boolean", "String"
    );

    public static boolean isPrimitiveOrWrapper(String type) {
        return PRIMITIVE_TYPES.contains(type) ||
                type.startsWith("List<Integer>") ||
                type.startsWith("List<String>") ||
                type.startsWith("List<List<") ||
                type.endsWith("[]");
    }

    public static boolean isCustomDataStructure(String type) {
        return type.contains("ListNode") ||
                type.contains("TreeNode") ||
                type.contains("Node");
    }

    public static boolean isListOfCustomDataStructure(String type) {
        return type.startsWith("List<") && isCustomDataStructure(type);
    }

    // ✅ New helper to detect arrays of custom DS
    public static boolean isArrayOfCustomDataStructure(String type) {
        return type.endsWith("[]") && isCustomDataStructure(type.substring(0, type.length() - 2));
    }

    // ✅ Extract array element type, e.g., "ListNode[]" -> "ListNode"
    public static String extractArrayElementType(String type) {
        if (isArrayOfCustomDataStructure(type)) {
            return type.substring(0, type.length() - 2);
        }
        return type; // fallback
    }

    // ✅ Extract list element type, e.g., "List<ListNode>" -> "ListNode"
    public static String extractListElementType(String type) {
        if (isListOfCustomDataStructure(type)) {
            int start = type.indexOf("<") + 1;
            int end = type.lastIndexOf(">");
            return type.substring(start, end).trim();
        }
        return type; // fallback
    }
}
