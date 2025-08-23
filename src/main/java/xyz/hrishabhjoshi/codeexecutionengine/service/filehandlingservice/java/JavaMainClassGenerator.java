package xyz.hrishabhjoshi.codeexecutionengine.service.filehandlingservice.java;

import xyz.hrishabhjoshi.codeexecutionengine.dto.CodeSubmissionDTO;
import xyz.hrishabhjoshi.codeexecutionengine.dto.CodeSubmissionDTO.QuestionMetadata;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

public class JavaMainClassGenerator {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static String generateMainClassContent(CodeSubmissionDTO submissionDto) throws JsonProcessingException {
        QuestionMetadata metadata = submissionDto.getQuestionMetadata();
        StringBuilder mainContent = new StringBuilder();

        appendImportsAndClassDeclaration(mainContent, metadata);
        appendMainMethodHeader(mainContent);

        for (int i = 0; i < submissionDto.getTestCases().size(); i++) {
            Map<String, Object> testCase = submissionDto.getTestCases().get(i);
            String inputJson = objectMapper.writeValueAsString(testCase.get("input"));
            appendTestCaseLogic(mainContent, submissionDto, metadata, inputJson, i);
        }

        mainContent.append("    }\n");

        if (metadata.getCustomDataStructureNames() != null) {
            String userCode = submissionDto.getUserSolutionCode();
            for (Map.Entry<String, String> entry : metadata.getCustomDataStructureNames().entrySet()) {
                String structureName = entry.getValue();

                mainContent.append(CustomDataStructureGenerator.generateCustomStructureHelper(structureName));

                if (!userCode.contains("class " + structureName)) {
                    mainContent.append(CustomDataStructureGenerator.generateCustomStructureClass(structureName));
                }

                mainContent.append(CustomDataStructureGenerator.generateListConverterMethods(structureName));


            }

            // Add converter methods for List<CustomDataStructure> return types
        }

        mainContent.append("}\n");
        return mainContent.toString();
    }


    private static void appendImportsAndClassDeclaration(StringBuilder builder, QuestionMetadata metadata) {
        builder.append("package ").append(metadata.getFullyQualifiedPackageName()).append(";\n\n");
        builder.append("import java.util.*;\n");
        builder.append("import java.util.stream.*;\n");
        builder.append("import java.util.Queue;\n"); // New import
        builder.append("import java.util.LinkedList;\n"); // New import
        builder.append("import com.fasterxml.jackson.databind.ObjectMapper;\n");
        builder.append("import com.fasterxml.jackson.core.JsonProcessingException;\n"); // **This is the fixed line**


        if (metadata.getCustomDataStructureNames() != null) {
            if (metadata.getCustomDataStructureNames().containsKey("ListNode") || metadata.getCustomDataStructureNames().containsKey("TreeNode") || metadata.getCustomDataStructureNames().containsKey("Node")) {
                builder.append("import com.fasterxml.jackson.annotation.JsonProperty;\n");
                builder.append("import com.fasterxml.jackson.databind.ObjectMapper;\n");

            }
        }


        builder.append("public class Main {\n");
    }

    private static void appendMainMethodHeader(StringBuilder builder) {
        builder.append("    public static void main(String[] args) {\n");
        builder.append("        Solution sol = new Solution();\n");
        builder.append("        ObjectMapper mapper = new ObjectMapper();\n\n");
    }

    private static void appendTestCaseLogic(StringBuilder builder, CodeSubmissionDTO submissionDto,
                                            QuestionMetadata metadata, String inputJson, int i) throws JsonProcessingException {
        builder.append("        // Test Case ").append(i).append("\n");
        builder.append("        long startTime").append(i).append(" = System.nanoTime();\n");
        builder.append("        try {\n");

        // 1️⃣ Generate input variables
        InputVariableGenerator.generateInputVariableDeclarations(builder, metadata, inputJson, i);

        // 2️⃣ Generate function call parameters
        String paramNames = InputVariableGenerator.generateFunctionParameters(metadata.getParameters(), i);

        // 3️⃣ Generate result assignment
        String returnType = metadata.getReturnType();

        // Determine proper variable type for test harness
        String resultVar = "result" + i;

        builder.append("            ").append(returnType).append(" ").append(resultVar)
                .append(" = sol.").append(metadata.getFunctionName()).append("(").append(paramNames).append(");\n");

        builder.append("            long endTime").append(i).append(" = System.nanoTime();\n");
        builder.append("            long duration").append(i).append(" = (endTime").append(i)
                .append(" - startTime").append(i).append(") / 1_000_000;\n");

        // 4️⃣ Serialize output intelligently
        if (JavaCodeHelper.isPrimitiveOrWrapper(returnType)) {
            // primitive or boxed types
            builder.append("            String actualOutput").append(i)
                    .append(" = String.valueOf(").append(resultVar).append(");\n");
        } else if (JavaCodeHelper.isArrayOfCustomDataStructure(returnType)) {
            // e.g., ListNode[] or TreeNode[]
            String customType = JavaCodeHelper.extractArrayElementType(returnType);
            builder.append("            String actualOutput").append(i).append(" = convert")
                    .append(customType).append("ListToJson(Arrays.asList(").append(resultVar).append("));\n");
        } else if (JavaCodeHelper.isListOfCustomDataStructure(returnType)) {
            // e.g., List<ListNode> or List<TreeNode>
            String customType = JavaCodeHelper.extractListElementType(returnType);
            builder.append("            String actualOutput").append(i).append(" = convert")
                    .append(customType).append("ListToJson(").append(resultVar).append(");\n");
        } else if (JavaCodeHelper.isCustomDataStructure(returnType)) {
            // Single object like ListNode or TreeNode
            builder.append("            String actualOutput").append(i).append(" = convert")
                    .append(returnType).append("ToJson(").append(resultVar).append(");\n");
        } else {
            // fallback: generic Object serialization
            builder.append("            String actualOutput").append(i).append(" = mapper.writeValueAsString(")
                    .append(resultVar).append(");\n");
        }

        // 5️⃣ Print test case result
        builder.append("            System.out.println(\"TEST_CASE_RESULT: ").append(i).append(",\" + actualOutput")
                .append(i).append(" + \",\" + duration").append(i).append(" + \",\");\n");

        // 6️⃣ Catch exceptions
        builder.append("        } catch (Exception e) {\n");
        builder.append("            long endTime").append(i).append(" = System.nanoTime();\n");
        builder.append("            long duration").append(i).append(" = (endTime").append(i).append(" - startTime")
                .append(i).append(") / 1_000_000;\n");
        builder.append("            System.out.println(\"TEST_CASE_RESULT: ").append(i).append(",,\" + duration")
                .append(i).append(" + \",\" + e.getClass().getSimpleName() + \": \" + e.getMessage());\n");
        builder.append("        }\n\n");
    }


    // Add this helper method to the JavaMainClassGenerator class
    private static String extractCustomDataStructure(String type) {
        if (type == null || type.trim().isEmpty()) {
            return null;
        }

        type = type.trim();

        // Pattern to match custom data structures within generic types
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("(ListNode|TreeNode|Node)");
        java.util.regex.Matcher matcher = pattern.matcher(type);

        if (matcher.find()) {
            return matcher.group(1);
        }

        return null;
    }

}