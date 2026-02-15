package xyz.hrishabhjoshi.codeexecutionengine.service.filehandlingservice.java;

import xyz.hrishabhjoshi.codeexecutionengine.dto.CodeSubmissionDTO;
import xyz.hrishabhjoshi.codeexecutionengine.dto.CodeSubmissionDTO.QuestionMetadata;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

@Slf4j
public class JavaMainClassGenerator {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final java.util.Set<String> KNOWN_CUSTOM_DS = java.util.Set.of("ListNode", "TreeNode", "Node");

    public static String generateMainClassContent(CodeSubmissionDTO submissionDto) throws JsonProcessingException {
        QuestionMetadata metadata = submissionDto.getQuestionMetadata();

        log.info("[MainClassGen] === START generating Main.java ===");
        log.info("[MainClassGen] functionName={}, returnType={}, questionType={}, testCases={}, mutationTarget={}",
                metadata.getFunctionName(), metadata.getReturnType(), metadata.getQuestionType(),
                submissionDto.getTestCases() != null ? submissionDto.getTestCases().size() : 0,
                metadata.getMutationTarget());

        // Route to design-class generator if applicable
        if ("DESIGN_CLASS".equals(metadata.getQuestionType())) {
            log.info("[MainClassGen] Routing to JavaDesignClassGenerator for DESIGN_CLASS question");
            return JavaDesignClassGenerator.generateMainClassContent(submissionDto);
        }

        StringBuilder mainContent = new StringBuilder();

        // Auto-detect custom data structures if not explicitly provided
        Map<String, String> customDS = metadata.getCustomDataStructureNames();
        if (customDS == null || customDS.isEmpty()) {
            customDS = detectCustomDataStructures(metadata);
            log.info("[MainClassGen] Auto-detected custom DS: {}", customDS);
        } else {
            log.info("[MainClassGen] Using provided custom DS: {}", customDS);
        }

        appendImportsAndClassDeclaration(mainContent, metadata, customDS);
        appendMainMethodHeader(mainContent);

        for (int i = 0; i < submissionDto.getTestCases().size(); i++) {
            Map<String, Object> testCase = submissionDto.getTestCases().get(i);
            String inputJson = objectMapper.writeValueAsString(testCase.get("input"));
            log.info("[MainClassGen] Processing testCase[{}]: inputJson={}", i, inputJson);
            appendTestCaseLogic(mainContent, submissionDto, metadata, inputJson, i);
        }

        mainContent.append("    }\n");

        // Generate custom data structure helpers using detected DS
        // NOTE: We do NOT generate the class here - Solution.java has it
        if (customDS != null && !customDS.isEmpty()) {
            for (Map.Entry<String, String> entry : customDS.entrySet()) {
                String structureName = entry.getValue();

                // Only generate helper methods and converters - NOT the class itself
                mainContent.append(CustomDataStructureGenerator.generateCustomStructureHelper(structureName));
                mainContent.append(CustomDataStructureGenerator.generateListConverterMethods(structureName));
            }
        }

        mainContent.append("}\n");
        log.info("[MainClassGen] Main.java total length: {}", mainContent.length());
        log.info("[MainClassGen] === END generating Main.java ===");
        return mainContent.toString();
    }

    /**
     * Auto-detect custom data structures from parameter types and return type.
     */
    private static Map<String, String> detectCustomDataStructures(QuestionMetadata metadata) {
        Map<String, String> detected = new java.util.HashMap<>();

        // Check return type
        if (metadata.getReturnType() != null) {
            for (String ds : KNOWN_CUSTOM_DS) {
                if (metadata.getReturnType().contains(ds)) {
                    detected.put(ds, ds);
                }
            }
        }

        // Check parameter types
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

    private static void appendImportsAndClassDeclaration(StringBuilder builder, QuestionMetadata metadata,
            Map<String, String> customDS) {
        builder.append("package ").append(metadata.getFullyQualifiedPackageName()).append(";\n\n");
        builder.append("import java.util.*;\n");
        builder.append("import java.util.stream.*;\n");
        builder.append("import java.util.Queue;\n");
        builder.append("import java.util.LinkedList;\n");
        builder.append("import com.fasterxml.jackson.databind.ObjectMapper;\n");
        builder.append("import com.fasterxml.jackson.core.JsonProcessingException;\n");

        if (customDS != null && !customDS.isEmpty()) {
            if (customDS.containsKey("ListNode") || customDS.containsKey("TreeNode") || customDS.containsKey("Node")) {
                builder.append("import com.fasterxml.jackson.annotation.JsonProperty;\n");
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

        // 3️⃣ Generate result assignment + serialization
        String returnType = metadata.getReturnType();

        if ("void".equals(returnType)) {
            // ---- VOID RETURN: call function, then serialize the mutated input param ----

            // Safety guard: prevent crash when parameters list is empty
            if (metadata.getParameters() == null || metadata.getParameters().isEmpty()) {
                log.error("[MainClassGen] VOID return with NO parameters — cannot determine mutation target. " +
                        "This may be a design-class question requiring questionType=DESIGN_CLASS metadata.");
                builder.append("            // ERROR: No parameters defined for void return type\n");
                builder.append("            String actualOutput").append(i)
                        .append(" = \"ERROR: void return type with no parameters - this question may need questionType=DESIGN_CLASS\";\n");
                builder.append("            long endTime").append(i).append(" = System.nanoTime();\n");
                builder.append("            long duration").append(i).append(" = (endTime").append(i)
                        .append(" - startTime").append(i).append(") / 1_000_000;\n");
            } else {
                builder.append("            sol.").append(metadata.getFunctionName())
                        .append("(").append(paramNames).append(");\n");

                log.info("[MainClassGen] testCase[{}]: VOID return branch, mutationTarget={}", i,
                        metadata.getMutationTarget());

                builder.append("            long endTime").append(i).append(" = System.nanoTime();\n");
                builder.append("            long duration").append(i).append(" = (endTime").append(i)
                        .append(" - startTime").append(i).append(") / 1_000_000;\n");

                // Determine which parameter was mutated
                String mutationTarget = metadata.getMutationTarget();
                int targetIndex = (mutationTarget != null) ? Integer.parseInt(mutationTarget) : 0;
                String targetParamName = metadata.getParameters().get(targetIndex).getName();
                String targetVar = targetParamName + i;
                String targetType = metadata.getParameters().get(targetIndex).getType();

                // Serialize the mutated parameter using existing type-aware logic
                if (JavaCodeHelper.isPrimitiveArray(targetType)) {
                    builder.append("            String actualOutput").append(i)
                            .append(" = java.util.Arrays.toString(").append(targetVar).append(");\n");
                } else if (JavaCodeHelper.isCustomDataStructure(targetType)) {
                    builder.append("            String actualOutput").append(i).append(" = convert")
                            .append(targetType).append("ToJson(").append(targetVar).append(");\n");
                } else {
                    builder.append("            String actualOutput").append(i).append(" = mapper.writeValueAsString(")
                            .append(targetVar).append(");\n");
                }
            }
        } else {
            // ---- NON-VOID: existing logic ----
            log.info("[MainClassGen] testCase[{}]: NON-VOID return branch, returnType={}", i, returnType);
            String resultVar = "result" + i;

            builder.append("            ").append(returnType).append(" ").append(resultVar)
                    .append(" = sol.").append(metadata.getFunctionName()).append("(").append(paramNames).append(");\n");

            builder.append("            long endTime").append(i).append(" = System.nanoTime();\n");
            builder.append("            long duration").append(i).append(" = (endTime").append(i)
                    .append(" - startTime").append(i).append(") / 1_000_000;\n");

            // 4️⃣ Serialize output intelligently
            if (JavaCodeHelper.isPrimitiveArray(returnType)) {
                builder.append("            String actualOutput").append(i)
                        .append(" = java.util.Arrays.toString(").append(resultVar).append(");\n");
            } else if (JavaCodeHelper.isPrimitiveOrWrapper(returnType)) {
                builder.append("            String actualOutput").append(i)
                        .append(" = String.valueOf(").append(resultVar).append(");\n");
            } else if (JavaCodeHelper.isArrayOfCustomDataStructure(returnType)) {
                String customType = JavaCodeHelper.extractArrayElementType(returnType);
                builder.append("            String actualOutput").append(i).append(" = convert")
                        .append(customType).append("ListToJson(Arrays.asList(").append(resultVar).append("));\n");
            } else if (JavaCodeHelper.isListOfCustomDataStructure(returnType)) {
                String customType = JavaCodeHelper.extractListElementType(returnType);
                builder.append("            String actualOutput").append(i).append(" = convert")
                        .append(customType).append("ListToJson(").append(resultVar).append(");\n");
            } else if (JavaCodeHelper.isCustomDataStructure(returnType)) {
                builder.append("            String actualOutput").append(i).append(" = convert")
                        .append(returnType).append("ToJson(").append(resultVar).append(");\n");
            } else {
                builder.append("            String actualOutput").append(i).append(" = mapper.writeValueAsString(")
                        .append(resultVar).append(");\n");
            }
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