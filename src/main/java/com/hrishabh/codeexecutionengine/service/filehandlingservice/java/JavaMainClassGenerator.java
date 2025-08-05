package com.hrishabh.codeexecutionengine.service.filehandlingservice.java;

import com.hrishabh.codeexecutionengine.dto.CodeSubmissionDTO;
import com.hrishabh.codeexecutionengine.dto.CodeSubmissionDTO.QuestionMetadata;
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
            }
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
    private static void appendTestCaseLogic(StringBuilder builder, CodeSubmissionDTO submissionDto, QuestionMetadata metadata, String inputJson, int i) throws JsonProcessingException {
        builder.append("        // Test Case ").append(i).append("\n");
        builder.append("        long startTime").append(i).append(" = System.nanoTime();\n");
        builder.append("        try {\n");

        InputVariableGenerator.generateInputVariableDeclarations(builder, metadata, inputJson, i);

        String paramNames = InputVariableGenerator.generateFunctionParameters(metadata.getParameters(), i);
        builder.append("            ").append(metadata.getReturnType()).append(" result").append(i)
                .append(" = sol.").append(metadata.getFunctionName()).append("(").append(paramNames).append(");\n");

        builder.append("            long endTime").append(i).append(" = System.nanoTime();\n");
        builder.append("            long duration").append(i).append(" = (endTime").append(i).append(" - startTime").append(i).append(") / 1_000_000;\n");

        String returnType = metadata.getReturnType();
        if (JavaCodeHelper.isPrimitiveOrWrapper(returnType)) {
            builder.append("            String actualOutput").append(i).append(" = String.valueOf(result").append(i).append(");\n");
        } else if ("Node".equals(returnType)) { // New condition for Node
            builder.append("            String actualOutput").append(i).append(" = convertNodeToAdjacencyList(result").append(i).append(");\n");
        } else {
            builder.append("            String actualOutput").append(i).append(" = mapper.writeValueAsString(result").append(i).append(");\n");
        }

        builder.append("            System.out.println(\"TEST_CASE_RESULT: ").append(i).append(",\" + actualOutput").append(i).append(" + \",\" + duration").append(i).append(" + \",\");\n");

        builder.append("        } catch (Exception e) {\n");
        builder.append("            long endTime").append(i).append(" = System.nanoTime();\n");
        builder.append("            long duration").append(i).append(" = (endTime").append(i).append(" - startTime").append(i).append(") / 1_000_000;\n");
        builder.append("            System.out.println(\"TEST_CASE_RESULT: ").append(i).append(",,\" + duration").append(i).append(" + \",\" + e.getClass().getSimpleName() + \": \" + e.getMessage());\n");
        builder.append("        }\n\n");
    }
}