package com.hrishabh.codeexecutionengine.service.filehandlingservice;

import com.hrishabh.codeexecutionengine.dto.CodeSubmissionDTO;
import com.hrishabh.codeexecutionengine.dto.CodeSubmissionDTO.QuestionMetadata;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@Component("javaFileGenerator")
public class JavaFileGenerator implements FileGenerator {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void generateFiles(CodeSubmissionDTO submissionDto, Path rootPath) throws IOException {
        QuestionMetadata metadata = submissionDto.getQuestionMetadata();
        if (metadata == null || metadata.getFullyQualifiedPackageName() == null) {
            throw new IllegalArgumentException("QuestionMetadata or fully qualified package name is missing.");
        }

        Path packageDir = createPackageDirectories(rootPath, metadata.getFullyQualifiedPackageName());
        Files.createDirectories(packageDir);

        String mainClassContent = generateMainClassContent(submissionDto);
        Path mainFilePath = packageDir.resolve("Main.java");
        Files.writeString(mainFilePath, mainClassContent);

        String solutionClassContent = generateSolutionClassContent(submissionDto);
        Path solutionFilePath = packageDir.resolve("Solution.java");
        Files.writeString(solutionFilePath, solutionClassContent);
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
        mainContent.append("public class Main {\n");
        mainContent.append("    public static void main(String[] args) {\n");
        mainContent.append("        Solution sol = new Solution();\n\n");

        for (int i = 0; i < submissionDto.getTestCases().size(); i++) {
            Map<String, Object> testCase = submissionDto.getTestCases().get(i);
            String inputJson = objectMapper.writeValueAsString(testCase.get("input"));

            mainContent.append("        // Test Case ").append(i).append("\n");
            mainContent.append("        long startTime").append(i).append(" = System.nanoTime();\n");
            mainContent.append("        try {\n");

            // 💡 This is the refactored code to declare and initialize variables from the DTO
            generateInputVariableDeclarations(mainContent, metadata.getParamTypes(), testCase.get("input"), i);

            String paramNames = generateFunctionParameters(metadata.getParamTypes(), i);
            mainContent.append("            ").append(metadata.getReturnType()).append(" result").append(i)
                    .append(" = sol.").append(metadata.getFunctionName()).append("(").append(paramNames).append(");\n");

            mainContent.append("            long endTime").append(i).append(" = System.nanoTime();\n");
            mainContent.append("            long duration").append(i).append(" = (endTime").append(i).append(" - startTime").append(i).append(") / 1_000_000;\n");
            mainContent.append("            String actualOutput").append(i).append(" = String.valueOf(result").append(i).append(");\n");
            mainContent.append("            System.out.println(\"TEST_CASE_RESULT: ").append(i).append(",\" + actualOutput").append(i).append(" + \",\" + duration").append(i).append(" + \",\");\n");

            mainContent.append("        } catch (Exception e) {\n");
            mainContent.append("            long endTime").append(i).append(" = System.nanoTime();\n");
            mainContent.append("            long duration").append(i).append(" = (endTime").append(i).append(" - startTime").append(i).append(") / 1_000_000;\n");
            mainContent.append("            System.out.println(\"TEST_CASE_RESULT: ").append(i).append(",,\" + duration").append(i).append(" + \",\" + e.getClass().getSimpleName() + \": \" + e.getMessage());\n");
            mainContent.append("        }\n\n");
        }

        mainContent.append("    }\n");
        mainContent.append("}\n");
        return mainContent.toString();
    }

    private void generateInputVariableDeclarations(StringBuilder builder, List<String> paramTypes, Object inputData, int testCaseIndex) throws JsonProcessingException {
        // This is a simplified implementation for Map<String, Object> inputs
        if (inputData instanceof Map) {
            Map<String, Object> inputMap = (Map<String, Object>) inputData;
            char paramName = 'a';
            for (String paramType : paramTypes) {
                String currentParamName = String.valueOf(paramName++);
                Object value = inputMap.get(currentParamName);
                if (value != null) {
                    builder.append("            ").append(paramType).append(" ").append(currentParamName)
                            .append(testCaseIndex).append(" = ").append(value).append(";\n");
                }
            }
        }
    }

    private String generateFunctionParameters(List<String> paramTypes, int testCaseIndex) {
        StringBuilder params = new StringBuilder();
        char paramName = 'a';
        for (int j = 0; j < paramTypes.size(); j++) {
            if (j > 0) params.append(", ");
            params.append(String.valueOf(paramName++)).append(testCaseIndex);
        }
        return params.toString();
    }

    private String generateSolutionClassContent(CodeSubmissionDTO submissionDto) {
        QuestionMetadata metadata = submissionDto.getQuestionMetadata();
        return "package " + metadata.getFullyQualifiedPackageName() + ";\n\n" + submissionDto.getUserSolutionCode();
    }
}