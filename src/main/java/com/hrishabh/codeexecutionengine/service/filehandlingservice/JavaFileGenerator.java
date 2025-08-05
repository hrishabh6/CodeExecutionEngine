package com.hrishabh.codeexecutionengine.service.filehandlingservice;

import com.hrishabh.codeexecutionengine.dto.CodeSubmissionDTO;
import com.hrishabh.codeexecutionengine.dto.CodeSubmissionDTO.QuestionMetadata;
import com.hrishabh.codeexecutionengine.dto.ParamInfoDTO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Component("javaFileGenerator")
public class JavaFileGenerator implements FileGenerator {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void generateFiles(CodeSubmissionDTO submissionDto, Path rootPath) throws IOException {
        System.out.println("LOGGING: Starting file generation...");

        QuestionMetadata metadata = submissionDto.getQuestionMetadata();
        if (metadata == null || metadata.getFullyQualifiedPackageName() == null) {
            throw new IllegalArgumentException("QuestionMetadata or fully qualified package name is missing.");
        }

        Path packageDir = createPackageDirectories(rootPath, metadata.getFullyQualifiedPackageName());
        Files.createDirectories(packageDir);

        System.out.println("LOGGING: Generating Main.java content...");
        String mainClassContent = generateMainClassContent(submissionDto);
        Path mainFilePath = packageDir.resolve("Main.java");
        Files.writeString(mainFilePath, mainClassContent);
        System.out.println("LOGGING: Main.java generated at " + mainFilePath.toAbsolutePath());

        System.out.println("LOGGING: Generating Solution.java content...");
        String solutionClassContent = generateSolutionClassContent(submissionDto);
        Path solutionFilePath = packageDir.resolve("Solution.java");
        Files.writeString(solutionFilePath, solutionClassContent);
        System.out.println("LOGGING: Solution.java generated at " + solutionFilePath.toAbsolutePath());
    }

    private Path createPackageDirectories(Path rootPath, String fullyQualifiedPackageName) {
        Path packagePath = rootPath;
        String[] packageParts = fullyQualifiedPackageName.split("\\.");
        for (String part : packageParts) {
            packagePath = packagePath.resolve(part);
        }
        return packagePath;
    }

    private String generateMainClassContent(CodeSubmissionDTO submissionDto) throws JsonProcessingException {
        QuestionMetadata metadata = submissionDto.getQuestionMetadata();
        StringBuilder mainContent = new StringBuilder();

        mainContent.append("package ").append(metadata.getFullyQualifiedPackageName()).append(";\n\n");
        mainContent.append("import java.util.*;\n");
        mainContent.append("import java.util.stream.*;\n");
        mainContent.append("import com.fasterxml.jackson.databind.ObjectMapper;\n");

        if (metadata.getCustomDataStructureNames() != null && metadata.getCustomDataStructureNames().containsKey("ListNode")) {
            mainContent.append("import com.fasterxml.jackson.annotation.JsonProperty;\n");
        }

        mainContent.append("public class Main {\n");
        mainContent.append("    public static void main(String[] args) {\n");
        mainContent.append("        Solution sol = new Solution();\n");
        mainContent.append("        ObjectMapper mapper = new ObjectMapper();\n\n");

        for (int i = 0; i < submissionDto.getTestCases().size(); i++) {
            Map<String, Object> testCase = submissionDto.getTestCases().get(i);
            String inputJson = objectMapper.writeValueAsString(testCase.get("input"));

            mainContent.append("        // Test Case ").append(i).append("\n");
            mainContent.append("        long startTime").append(i).append(" = System.nanoTime();\n");
            mainContent.append("        try {\n");

            generateInputVariableDeclarations(mainContent, metadata, inputJson, i);

            String paramNames = generateFunctionParameters(metadata.getParameters(), i);
            mainContent.append("            ").append(metadata.getReturnType()).append(" result").append(i)
                    .append(" = sol.").append(metadata.getFunctionName()).append("(").append(paramNames).append(");\n");

            mainContent.append("            long endTime").append(i).append(" = System.nanoTime();\n");
            mainContent.append("            long duration").append(i).append(" = (endTime").append(i).append(" - startTime").append(i).append(") / 1_000_000;\n");

            String returnType = metadata.getReturnType();
            if (returnType.equalsIgnoreCase("int") || returnType.equalsIgnoreCase("Integer") || returnType.equalsIgnoreCase("double") || returnType.equalsIgnoreCase("Double") || returnType.equalsIgnoreCase("boolean") || returnType.equalsIgnoreCase("Boolean") || returnType.equalsIgnoreCase("String")) {
                mainContent.append("            String actualOutput").append(i).append(" = String.valueOf(result").append(i).append(");\n");
            } else {
                mainContent.append("            String actualOutput").append(i).append(" = mapper.writeValueAsString(result").append(i).append(");\n");
            }

            mainContent.append("            System.out.println(\"TEST_CASE_RESULT: ").append(i).append(",\" + actualOutput").append(i).append(" + \",\" + duration").append(i).append(" + \",\");\n");

            mainContent.append("        } catch (Exception e) {\n");
            mainContent.append("            long endTime").append(i).append(" = System.nanoTime();\n");
            mainContent.append("            long duration").append(i).append(" = (endTime").append(i).append(" - startTime").append(i).append(") / 1_000_000;\n");
            mainContent.append("            System.out.println(\"TEST_CASE_RESULT: ").append(i).append(",,\" + duration").append(i).append(" + \",\" + e.getClass().getSimpleName() + \": \" + e.getMessage());\n");
            mainContent.append("        }\n\n");
        }

        mainContent.append("    }\n");
        String userCode = submissionDto.getUserSolutionCode();

        // 💡 Conditional generation of the custom class and helper
        if (metadata.getCustomDataStructureNames() != null && metadata.getCustomDataStructureNames().containsKey("ListNode")) {
            String listNodeClassName = metadata.getCustomDataStructureNames().get("ListNode");
            // 💡 Always generate the helper method
            mainContent.append(generateListNodeHelper(listNodeClassName));

            // 💡 Only generate the class if the user's code doesn't contain it
            if (!submissionDto.getUserSolutionCode().contains("class " + listNodeClassName)) {
                mainContent.append(generateListNodeClass(listNodeClassName));
            }
        }


        mainContent.append("}\n");

        System.out.println("LOGGING: Final Main.java content:\n" + mainContent.toString());
        return mainContent.toString();
    }

    private void generateInputVariableDeclarations(StringBuilder builder, QuestionMetadata metadata, String inputJson, int testCaseIndex) throws JsonProcessingException {
        JsonNode inputNode = objectMapper.readTree(inputJson);
        List<ParamInfoDTO> parameters = metadata.getParameters();

        if (inputNode.isObject()) {
            for (ParamInfoDTO param : parameters) {
                String paramType = param.getType();
                String paramName = param.getName();
                JsonNode paramValueNode = inputNode.get(paramName);

                if (paramValueNode != null) {
                    String declarationValue = generateValueDeclaration(paramType, paramValueNode, metadata.getCustomDataStructureNames());
                    builder.append("            ").append(paramType).append(" ").append(paramName).append(testCaseIndex)
                            .append(" = ").append(declarationValue).append(";\n");
                    System.out.println("LOGGING: Generated declaration for " + paramName + testCaseIndex + " as: " + declarationValue);
                }
            }
        }
    }

    private String generateValueDeclaration(String paramType, JsonNode node, Map<String, String> customClasses) throws JsonProcessingException {
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
            default:
                return node.toString();
        }
    }

    private String generateFunctionParameters(List<ParamInfoDTO> parameters, int testCaseIndex) {
        StringBuilder params = new StringBuilder();
        for (int j = 0; j < parameters.size(); j++) {
            if (j > 0) params.append(", ");
            params.append(parameters.get(j).getName()).append(testCaseIndex);
        }
        return params.toString();
    }

    // 💡 This method now uses StringBuilder for explicit spacing
    private String generateListNodeHelper(String listNodeClassName) {
        StringBuilder helper = new StringBuilder();
        helper.append("    private static ").append(listNodeClassName).append(" buildListNode(String jsonString) throws com.fasterxml.jackson.core.JsonProcessingException {\n");
        helper.append("        ObjectMapper mapper = new ObjectMapper();\n");
        helper.append("        List<Integer> list = mapper.readValue(jsonString, mapper.getTypeFactory().constructCollectionType(List.class, Integer.class));\n");
        helper.append("        if (list == null || list.isEmpty()) {\n");
        helper.append("            return null;\n");
        helper.append("        }\n");
        helper.append("        ").append(listNodeClassName).append(" dummy = new ").append(listNodeClassName).append("(0);\n");
        helper.append("        ").append(listNodeClassName).append(" current = dummy;\n");
        helper.append("        for (Integer val : list) {\n");
        helper.append("            current.next = new ").append(listNodeClassName).append("(val);\n");
        helper.append("            current = current.next;\n");
        helper.append("        }\n");
        helper.append("        return dummy.next;\n");
        helper.append("    }\n\n");
        return helper.toString();
    }

    private String generateListNodeClass(String listNodeClassName) {
        StringBuilder classDef = new StringBuilder();
        classDef.append("    private static class ").append(listNodeClassName).append(" {\n");
        classDef.append("        @JsonProperty\n");
        classDef.append("        int val;\n");
        classDef.append("        @JsonProperty\n");
        classDef.append("        ").append(listNodeClassName).append(" next;\n");
        classDef.append("        public ").append(listNodeClassName).append("() {}\n");
        classDef.append("        public ").append(listNodeClassName).append("(int val) { this.val = val; }\n");
        classDef.append("        public ").append(listNodeClassName).append("(int val, ").append(listNodeClassName).append(" next) { this.val = val; this.next = next; }\n");
        classDef.append("    }\n");
        return classDef.toString();
    }

    private String generateSolutionClassContent(CodeSubmissionDTO submissionDto) {
        QuestionMetadata metadata = submissionDto.getQuestionMetadata();
        return "package " + metadata.getFullyQualifiedPackageName() + ";\n\n" + submissionDto.getUserSolutionCode();
    }
}