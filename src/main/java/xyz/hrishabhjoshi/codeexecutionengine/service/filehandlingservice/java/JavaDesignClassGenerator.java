package xyz.hrishabhjoshi.codeexecutionengine.service.filehandlingservice.java;

import xyz.hrishabhjoshi.codeexecutionengine.dto.CodeSubmissionDTO;
import xyz.hrishabhjoshi.codeexecutionengine.dto.CodeSubmissionDTO.QuestionMetadata;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * Generates Main.java for design-class type questions (e.g., Codec, LRUCache,
 * MinStack).
 *
 * Design-class questions use the LeetCode 2-array format:
 * input[0] = ["ClassName", "method1", "method2", ...]
 * input[1] = [constructorArgs, method1Args, method2Args, ...]
 *
 * The generated Main.java:
 * 1. Parses operations and arguments from each test case
 * 2. Instantiates the user's class (first operation = constructor)
 * 3. Calls each method via reflection
 * 4. Collects return values into a JSON array as the output
 */
@Slf4j
public class JavaDesignClassGenerator {

        private static final ObjectMapper objectMapper = new ObjectMapper();
        private static final java.util.Set<String> KNOWN_CUSTOM_DS = java.util.Set.of("ListNode", "TreeNode", "Node");

        public static String generateMainClassContent(CodeSubmissionDTO submissionDto) throws JsonProcessingException {
                QuestionMetadata metadata = submissionDto.getQuestionMetadata();
                StringBuilder content = new StringBuilder();

                log.info("[DesignClassGen] === START generating Main.java for DESIGN_CLASS ===");
                log.info("[DesignClassGen] className={}, testCases={}", metadata.getFunctionName(),
                                submissionDto.getTestCases() != null ? submissionDto.getTestCases().size() : 0);

                String className = metadata.getFunctionName(); // For design-class, functionName = class name

                // Auto-detect custom data structures
                Map<String, String> customDS = metadata.getCustomDataStructureNames();
                if (customDS == null || customDS.isEmpty()) {
                        customDS = detectCustomDataStructures(metadata, submissionDto.getUserSolutionCode());
                        log.info("[DesignClassGen] Auto-detected custom DS: {}", customDS);
                }

                // ---- Package & Imports ----
                content.append("package ").append(metadata.getFullyQualifiedPackageName()).append(";\n\n");
                content.append("import java.util.*;\n");
                content.append("import java.util.stream.*;\n");
                content.append("import java.lang.reflect.*;\n");
                content.append("import com.fasterxml.jackson.databind.*;\n");
                content.append("import com.fasterxml.jackson.databind.node.*;\n");
                content.append("import com.fasterxml.jackson.core.JsonProcessingException;\n");
                content.append("import com.fasterxml.jackson.core.type.TypeReference;\n");

                if (customDS != null && !customDS.isEmpty()) {
                        content.append("import com.fasterxml.jackson.annotation.JsonProperty;\n");
                }
                content.append("\n");

                // ---- Class declaration ----
                content.append("public class Main {\n\n");

                // ---- Helper: invokeMethod using reflection ----
                appendReflectionHelper(content);

                // ---- Helper: parseArgument - converts JsonNode to Java object for method
                // parameter ----
                appendArgumentParser(content, customDS);

                // ---- Custom DS helpers (TreeNode, ListNode, etc.) ----
                if (customDS != null && !customDS.isEmpty()) {
                        for (Map.Entry<String, String> entry : customDS.entrySet()) {
                                String structureName = entry.getValue();
                                content.append(CustomDataStructureGenerator
                                                .generateCustomStructureHelper(structureName));
                                content.append(CustomDataStructureGenerator
                                                .generateListConverterMethods(structureName));
                        }
                }

                // ---- Serialization helpers ----
                appendSerializationHelpers(content, customDS);

                // ---- Main method ----
                content.append("    public static void main(String[] args) throws Exception {\n");
                content.append("        ObjectMapper mapper = new ObjectMapper();\n\n");

                // Generate test case logic for each test case
                for (int i = 0; i < submissionDto.getTestCases().size(); i++) {
                        Map<String, Object> testCase = submissionDto.getTestCases().get(i);
                        String inputJson = objectMapper.writeValueAsString(testCase.get("input"));
                        log.info("[DesignClassGen] Processing testCase[{}]: inputJson={}", i, inputJson);
                        appendDesignTestCaseLogic(content, className, inputJson, i);
                }

                content.append("    }\n");
                content.append("}\n");

                log.info("[DesignClassGen] Main.java total length: {}", content.length());
                log.info("[DesignClassGen] === END generating Main.java for DESIGN_CLASS ===");
                return content.toString();
        }

        /**
         * Generate the test case logic for a single design-class test case.
         * Input format: [[operations], [arguments]]
         */
        private static void appendDesignTestCaseLogic(StringBuilder content, String className, String inputJson,
                        int i) {
                content.append("        // Test Case ").append(i).append("\n");
                content.append("        long startTime").append(i).append(" = System.nanoTime();\n");
                content.append("        try {\n");

                // Parse the 2-array input
                content.append("            // Parse design-class input: [[operations], [arguments]]\n");
                content.append("            JsonNode inputRoot").append(i).append(" = mapper.readTree(")
                                .append(escapeJavaString(inputJson)).append(");\n");
                content.append("            JsonNode opsNode").append(i).append(" = inputRoot").append(i)
                                .append(".get(0);\n");
                content.append("            JsonNode argsNode").append(i).append(" = inputRoot").append(i)
                                .append(".get(1);\n\n");

                // Validate the input format
                content.append("            if (opsNode").append(i).append(" == null || argsNode").append(i)
                                .append(" == null || !opsNode").append(i).append(".isArray() || !argsNode").append(i)
                                .append(".isArray()) {\n");
                content.append(
                                "                throw new RuntimeException(\"Invalid design-class input format: expected [[operations],[arguments]]\");\n");
                content.append("            }\n\n");

                // Execute operations
                content.append("            List<String> results").append(i).append(" = new ArrayList<>();\n");
                content.append("            Object instance").append(i).append(" = null;\n");
                content.append("            Object prevResult").append(i).append(" = null;\n\n");

                content.append("            for (int opIdx = 0; opIdx < opsNode").append(i)
                                .append(".size(); opIdx++) {\n");
                content.append("                String opName = opsNode").append(i).append(".get(opIdx).asText();\n");
                content.append("                JsonNode opArgs = argsNode").append(i).append(".get(opIdx);\n\n");

                // $PREV substitution: replace "$PREV" args with previous operation's result
                content.append("                // Substitute $PREV markers with previous operation's result\n");
                content.append("                if (opArgs != null && opArgs.isArray()) {\n");
                content.append("                    com.fasterxml.jackson.databind.node.ArrayNode substituted = mapper.createArrayNode();\n");
                content.append("                    for (int ai = 0; ai < opArgs.size(); ai++) {\n");
                content.append("                        JsonNode arg = opArgs.get(ai);\n");
                content.append("                        if (arg.isTextual() && arg.asText().equals(\"$PREV\")) {\n");
                content.append("                            if (prevResult").append(i).append(" != null) {\n");
                content.append("                                substituted.add(mapper.readTree(serializeResult(prevResult")
                                .append(i).append(", mapper)));\n");
                content.append("                            } else {\n");
                content.append("                                substituted.addNull();\n");
                content.append("                            }\n");
                content.append("                        } else {\n");
                content.append("                            substituted.add(arg);\n");
                content.append("                        }\n");
                content.append("                    }\n");
                content.append("                    opArgs = substituted;\n");
                content.append("                }\n\n");

                // Constructor (first operation)
                content.append("                if (opIdx == 0) {\n");
                content.append("                    // Constructor call: instantiate the class\n");
                content.append("                    instance").append(i).append(" = createInstance(\"")
                                .append(className)
                                .append("\", opArgs, mapper);\n");
                content.append("                    results").append(i).append(".add(\"null\");\n");
                content.append("                } else {\n");
                content.append("                    // Method call: invoke on instance\n");
                content.append("                    Object result = invokeMethod(instance").append(i)
                                .append(", opName, opArgs, mapper);\n");
                content.append("                    prevResult").append(i).append(" = result;\n");
                content.append("                    if (result == null) {\n");
                content.append("                        results").append(i).append(".add(\"null\");\n");
                content.append("                    } else {\n");
                content.append("                        results").append(i)
                                .append(".add(serializeResult(result, mapper));\n");
                content.append("                    }\n");
                content.append("                }\n");
                content.append("            }\n\n");

                // Output
                content.append("            long endTime").append(i).append(" = System.nanoTime();\n");
                content.append("            long duration").append(i).append(" = (endTime").append(i)
                                .append(" - startTime").append(i).append(") / 1_000_000;\n");
                content.append("            String actualOutput").append(i).append(" = \"[\" + results").append(i)
                                .append(".stream().collect(java.util.stream.Collectors.joining(\",\")) + \"]\";\n");
                content.append("            System.out.println(\"TEST_CASE_RESULT: ").append(i)
                                .append(",\" + actualOutput").append(i).append(" + \",\" + duration").append(i)
                                .append(" + \",\");\n");

                // Exception handling
                content.append("        } catch (Exception e) {\n");
                content.append("            long endTime").append(i).append(" = System.nanoTime();\n");
                content.append("            long duration").append(i).append(" = (endTime").append(i)
                                .append(" - startTime").append(i).append(") / 1_000_000;\n");
                content.append("            System.out.println(\"TEST_CASE_RESULT: ").append(i)
                                .append(",,\" + duration").append(i)
                                .append(" + \",\" + e.getClass().getSimpleName() + \": \" + e.getMessage());\n");
                content.append("        }\n\n");
        }

        /**
         * Generate helper to create an instance of the user's class using reflection.
         */
        private static void appendReflectionHelper(StringBuilder content) {
                // createInstance helper
                content.append(
                                "    private static Object createInstance(String className, JsonNode constructorArgs, ObjectMapper mapper) throws Exception {\n");
                content.append("        Class<?> clazz = Class.forName(Main.class.getPackageName() + \".\" + className);\n");
                content.append("        if (constructorArgs == null || constructorArgs.size() == 0) {\n");
                content.append("            return clazz.getDeclaredConstructor().newInstance();\n");
                content.append("        }\n");
                content.append("        // Find the best matching constructor\n");
                content.append("        for (Constructor<?> ctor : clazz.getDeclaredConstructors()) {\n");
                content.append("            if (ctor.getParameterCount() == constructorArgs.size()) {\n");
                content.append("                Object[] parsedArgs = new Object[constructorArgs.size()];\n");
                content.append("                Class<?>[] paramTypes = ctor.getParameterTypes();\n");
                content.append("                boolean match = true;\n");
                content.append("                for (int i = 0; i < constructorArgs.size(); i++) {\n");
                content.append(
                                "                    parsedArgs[i] = parseArgument(constructorArgs.get(i), paramTypes[i], mapper);\n");
                content.append(
                                "                    if (parsedArgs[i] == null && paramTypes[i].isPrimitive()) { match = false; break; }\n");
                content.append("                }\n");
                content.append("                if (match) return ctor.newInstance(parsedArgs);\n");
                content.append("            }\n");
                content.append("        }\n");
                content.append(
                                "        throw new RuntimeException(\"No matching constructor found for \" + className + \" with \" + constructorArgs.size() + \" args\");\n");
                content.append("    }\n\n");

                // invokeMethod helper
                content.append(
                                "    private static Object invokeMethod(Object instance, String methodName, JsonNode methodArgs, ObjectMapper mapper) throws Exception {\n");
                content.append("        Class<?> clazz = instance.getClass();\n");
                content.append("        int argCount = (methodArgs != null) ? methodArgs.size() : 0;\n");
                content.append("        for (Method method : clazz.getDeclaredMethods()) {\n");
                content.append(
                                "            if (method.getName().equals(methodName) && method.getParameterCount() == argCount) {\n");
                content.append("                method.setAccessible(true);\n");
                content.append("                Object[] parsedArgs = new Object[argCount];\n");
                content.append("                Class<?>[] paramTypes = method.getParameterTypes();\n");
                content.append("                for (int i = 0; i < argCount; i++) {\n");
                content.append(
                                "                    parsedArgs[i] = parseArgument(methodArgs.get(i), paramTypes[i], mapper);\n");
                content.append("                }\n");
                content.append("                Object result = method.invoke(instance, parsedArgs);\n");
                content.append("                return (method.getReturnType() == void.class) ? null : result;\n");
                content.append("            }\n");
                content.append("        }\n");
                content.append("        // Try public methods from superclass\n");
                content.append("        for (Method method : clazz.getMethods()) {\n");
                content.append(
                                "            if (method.getName().equals(methodName) && method.getParameterCount() == argCount) {\n");
                content.append("                Object[] parsedArgs = new Object[argCount];\n");
                content.append("                Class<?>[] paramTypes = method.getParameterTypes();\n");
                content.append("                for (int i = 0; i < argCount; i++) {\n");
                content.append(
                                "                    parsedArgs[i] = parseArgument(methodArgs.get(i), paramTypes[i], mapper);\n");
                content.append("                }\n");
                content.append("                Object result = method.invoke(instance, parsedArgs);\n");
                content.append("                return (method.getReturnType() == void.class) ? null : result;\n");
                content.append("            }\n");
                content.append("        }\n");
                content.append(
                                "        throw new RuntimeException(\"No matching method '\" + methodName + \"' with \" + argCount + \" args found in \" + clazz.getSimpleName());\n");
                content.append("    }\n\n");
        }

        /**
         * Generate helper to parse a JsonNode into a Java object of the expected type.
         * Handles primitives, strings, arrays, lists, TreeNode, ListNode, etc.
         */
        private static void appendArgumentParser(StringBuilder content, Map<String, String> customDS) {
                boolean hasTreeNode = customDS != null && customDS.containsKey("TreeNode");
                boolean hasListNode = customDS != null && customDS.containsKey("ListNode");

                content.append(
                                "    private static Object parseArgument(JsonNode node, Class<?> targetType, ObjectMapper mapper) throws Exception {\n");
                content.append("        if (node == null || node.isNull()) return null;\n\n");

                // Primitives
                content.append("        // Primitives and wrappers\n");
                content.append("        if (targetType == int.class || targetType == Integer.class) return node.asInt();\n");
                content.append("        if (targetType == long.class || targetType == Long.class) return node.asLong();\n");
                content.append(
                                "        if (targetType == double.class || targetType == Double.class) return node.asDouble();\n");
                content.append(
                                "        if (targetType == float.class || targetType == Float.class) return (float) node.asDouble();\n");
                content.append(
                                "        if (targetType == boolean.class || targetType == Boolean.class) return node.asBoolean();\n");
                content.append(
                                "        if (targetType == char.class || targetType == Character.class) return node.asText().charAt(0);\n");
                content.append("        if (targetType == String.class) return node.asText();\n\n");

                // Arrays
                content.append("        // int[]\n");
                content.append("        if (targetType == int[].class) {\n");
                content.append("            int[] arr = new int[node.size()];\n");
                content.append("            for (int i = 0; i < node.size(); i++) arr[i] = node.get(i).asInt();\n");
                content.append("            return arr;\n");
                content.append("        }\n");
                content.append("        // String[]\n");
                content.append("        if (targetType == String[].class) {\n");
                content.append("            String[] arr = new String[node.size()];\n");
                content.append("            for (int i = 0; i < node.size(); i++) arr[i] = node.get(i).asText();\n");
                content.append("            return arr;\n");
                content.append("        }\n");
                content.append("        // double[]\n");
                content.append("        if (targetType == double[].class) {\n");
                content.append("            double[] arr = new double[node.size()];\n");
                content.append("            for (int i = 0; i < node.size(); i++) arr[i] = node.get(i).asDouble();\n");
                content.append("            return arr;\n");
                content.append("        }\n");
                content.append("        // int[][]\n");
                content.append("        if (targetType == int[][].class) {\n");
                content.append("            int[][] arr = new int[node.size()][];\n");
                content.append("            for (int i = 0; i < node.size(); i++) {\n");
                content.append("                JsonNode row = node.get(i);\n");
                content.append("                arr[i] = new int[row.size()];\n");
                content.append("                for (int j = 0; j < row.size(); j++) arr[i][j] = row.get(j).asInt();\n");
                content.append("            }\n");
                content.append("            return arr;\n");
                content.append("        }\n\n");

                // Custom data structures — only emit if detected
                if (hasTreeNode) {
                        content.append("        // TreeNode (level-order array)\n");
                        content.append("        if (targetType.getSimpleName().equals(\"TreeNode\")) {\n");
                        content.append(
                                        "            try { return buildTreeNode(node.toString(), false); } catch (Exception e) { return null; }\n");
                        content.append("        }\n");
                }
                if (hasListNode) {
                        content.append("        // ListNode (integer array)\n");
                        content.append("        if (targetType.getSimpleName().equals(\"ListNode\")) {\n");
                        content.append(
                                        "            try { return buildListNode(node.toString(), false); } catch (Exception e) { return null; }\n");
                        content.append("        }\n");
                }
                content.append("\n");

                // Lists (generic fallback)
                content.append("        // List types (fallback to Jackson deserialization)\n");
                content.append("        if (List.class.isAssignableFrom(targetType)) {\n");
                content.append("            return mapper.readValue(node.toString(), new TypeReference<List<Object>>(){});\n");
                content.append("        }\n\n");

                // Fallback: use Jackson mapper
                content.append("        // Fallback: use Jackson mapper\n");
                content.append("        return mapper.treeToValue(node, targetType);\n");
                content.append("    }\n\n");
        }

        /**
         * Generate serialization helpers for converting results back to JSON-compatible
         * strings.
         */
        private static void appendSerializationHelpers(StringBuilder content, Map<String, String> customDS) {
                boolean hasTreeNode = customDS != null && customDS.containsKey("TreeNode");
                boolean hasListNode = customDS != null && customDS.containsKey("ListNode");

                content.append(
                                "    private static String serializeResult(Object result, ObjectMapper mapper) throws JsonProcessingException {\n");
                content.append("        if (result == null) return \"null\";\n");
                content.append("        if (result instanceof String) return mapper.writeValueAsString(result);\n");
                content.append("        if (result instanceof Integer || result instanceof Long || result instanceof Double\n");
                content.append("                || result instanceof Float || result instanceof Boolean) {\n");
                content.append("            return String.valueOf(result);\n");
                content.append("        }\n");
                content.append("        if (result instanceof int[]) return java.util.Arrays.toString((int[]) result);\n");
                content.append("        if (result instanceof String[]) return mapper.writeValueAsString(result);\n");
                content.append(
                                "        if (result instanceof double[]) return java.util.Arrays.toString((double[]) result);\n");

                // Custom DS serialization — only emit if detected
                if (hasTreeNode || hasListNode) {
                        content.append("        String className = result.getClass().getSimpleName();\n");
                }
                if (hasTreeNode) {
                        content.append("        if (className.equals(\"TreeNode\")) {\n");
                        content.append(
                                        "            try { return convertTreeNodeToJson((TreeNode) result); } catch (Exception e) { return result.toString(); }\n");
                        content.append("        }\n");
                }
                if (hasListNode) {
                        content.append("        if (className.equals(\"ListNode\")) {\n");
                        content.append(
                                        "            try { return convertListNodeToJson((ListNode) result); } catch (Exception e) { return result.toString(); }\n");
                        content.append("        }\n");
                }

                // Generic fallback
                content.append("        return mapper.writeValueAsString(result);\n");
                content.append("    }\n\n");
        }

        /**
         * Detect custom data structures from user code.
         */
        private static Map<String, String> detectCustomDataStructures(QuestionMetadata metadata, String userCode) {
                Map<String, String> detected = new java.util.HashMap<>();
                if (userCode != null) {
                        for (String ds : KNOWN_CUSTOM_DS) {
                                if (userCode.contains(ds)) {
                                        detected.put(ds, ds);
                                }
                        }
                }
                // Also check return type and parameters if present
                if (metadata.getReturnType() != null) {
                        for (String ds : KNOWN_CUSTOM_DS) {
                                if (metadata.getReturnType().contains(ds)) {
                                        detected.put(ds, ds);
                                }
                        }
                }
                if (metadata.getParameters() != null) {
                        for (var param : metadata.getParameters()) {
                                if (param.getType() != null) {
                                        for (String ds : KNOWN_CUSTOM_DS) {
                                                if (param.getType().contains(ds)) {
                                                        detected.put(ds, ds);
                                                }
                                        }
                                }
                        }
                }
                return detected;
        }

        private static String escapeJavaString(String s) {
                return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
        }
}
