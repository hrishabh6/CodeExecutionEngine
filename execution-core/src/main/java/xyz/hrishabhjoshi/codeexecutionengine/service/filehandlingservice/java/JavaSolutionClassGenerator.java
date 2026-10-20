package xyz.hrishabhjoshi.codeexecutionengine.service.filehandlingservice.java;

import xyz.hrishabhjoshi.codeexecutionengine.dto.CodeSubmissionDTO;
import xyz.hrishabhjoshi.codeexecutionengine.dto.CodeSubmissionDTO.QuestionMetadata;
import xyz.hrishabhjoshi.codeexecutionengine.dto.ParamInfoDTO;
import lombok.extern.slf4j.Slf4j;

import java.util.HashSet;
import java.util.Set;

@Slf4j
public class JavaSolutionClassGenerator {

    private static final Set<String> KNOWN_CUSTOM_DS = Set.of("ListNode", "TreeNode", "Node");

    public static String generateSolutionClassContent(CodeSubmissionDTO submissionDto) {
        QuestionMetadata metadata = submissionDto.getQuestionMetadata();
        StringBuilder content = new StringBuilder();

        log.info("[SolutionGen] === START generating Solution.java ===");
        log.info("[SolutionGen] packageName={}, functionName={}, returnType={}, questionType={}",
                metadata.getFullyQualifiedPackageName(), metadata.getFunctionName(), metadata.getReturnType(),
                metadata.getQuestionType());

        // For design-class questions, user code IS the class — don't wrap in Solution
        if ("DESIGN_CLASS".equals(metadata.getQuestionType())) {
            log.info("[SolutionGen] DESIGN_CLASS mode: emitting user code as-is (not wrapped in Solution class)");
            return generateDesignClassSolutionContent(submissionDto);
        }

        content.append("package ").append(metadata.getFullyQualifiedPackageName()).append(";\n\n");

        // Auto-detect required custom data structures
        Set<String> requiredDS = detectRequiredDataStructures(metadata);
        log.info("[SolutionGen] Detected required custom DS: {}", requiredDS);
        String userCode = submissionDto.getUserSolutionCode();

        // Extract imports from user code
        StringBuilder userImports = new StringBuilder();
        StringBuilder userCodeWithoutImports = new StringBuilder();
        for (String line : userCode.split("\n")) {
            if (line.trim().startsWith("import ")) {
                userImports.append(line).append("\n");
            } else {
                userCodeWithoutImports.append(line).append("\n");
            }
        }

        log.info("[SolutionGen] Extracted {} import lines from user code", userImports.toString().split("\n").length);

        // Always add standard Java imports (LeetCode-style)
        // Users expect java.util classes like PriorityQueue, List, Map, etc. to be
        // available
        content.append("import java.util.*;\n");

        // Add user's imports
        if (userImports.length() > 0) {
            content.append(userImports);
        }
        content.append("\n");

        // Add custom DS class definitions if not in user code
        for (String ds : requiredDS) {
            if (!userCode.contains("class " + ds)) {
                log.debug("[SolutionGen] Adding {} class to Solution.java", ds);
                content.append(generateCustomDSClass(ds));
                content.append("\n");
            }
        }

        // Add user code WITHOUT imports (already added above)
        content.append(userCodeWithoutImports.toString().trim());
        log.info("[SolutionGen] Solution.java content length: {}", content.length());
        log.info("[SolutionGen] === END generating Solution.java ===");
        return content.toString();
    }

    private static Set<String> detectRequiredDataStructures(QuestionMetadata metadata) {
        Set<String> required = new HashSet<>();

        // Check return type
        if (metadata.getReturnType() != null) {
            for (String ds : KNOWN_CUSTOM_DS) {
                if (metadata.getReturnType().contains(ds)) {
                    required.add(ds);
                }
            }
        }

        // Check parameter types
        if (metadata.getParameters() != null) {
            for (ParamInfoDTO param : metadata.getParameters()) {
                if (param.getType() != null) {
                    for (String ds : KNOWN_CUSTOM_DS) {
                        if (param.getType().contains(ds)) {
                            required.add(ds);
                        }
                    }
                }
            }
        }

        return required;
    }

     private static String generateCustomDSClass(String structureName) {
        StringBuilder classDef = new StringBuilder();
        switch (structureName) {
            case "ListNode":
                classDef.append("class ListNode {\n");
                classDef.append("    int val;\n");
                classDef.append("    ListNode next;\n");
                classDef.append("    ListNode() {}\n");
                classDef.append("    ListNode(int val) { this.val = val; }\n");
                classDef.append("    ListNode(int val, ListNode next) { this.val = val; this.next = next; }\n");
                classDef.append("}\n");
                break;
            case "TreeNode":
                classDef.append("class TreeNode {\n");
                classDef.append("    int val;\n");
                classDef.append("    TreeNode left;\n");
                classDef.append("    TreeNode right;\n");
                classDef.append("    TreeNode() {}\n");
                classDef.append("    TreeNode(int val) { this.val = val; }\n");
                classDef.append("    TreeNode(int val, TreeNode left, TreeNode right) {\n");
                classDef.append("        this.val = val;\n");
                classDef.append("        this.left = left;\n");
                classDef.append("        this.right = right;\n");
                classDef.append("    }\n");
                classDef.append("}\n");
                break;
            case "Node":
                classDef.append("class Node {\n");
                classDef.append("    public int val;\n");
                classDef.append("    public List<Node> neighbors;\n");
                classDef.append("    public Node() { neighbors = new ArrayList<Node>(); }\n");
                classDef.append("    public Node(int _val) { val = _val; neighbors = new ArrayList<Node>(); }\n");
                classDef.append(
                        "    public Node(int _val, List<Node> _neighbors) { val = _val; neighbors = _neighbors; }\n");
                classDef.append("}\n");
                break;
        }
        return classDef.toString();
    }

    /**
     * Generate Solution.java for design-class questions.
     * User code IS the class — emit it as-is with package declaration and necessary DS classes.
     */
    private static String generateDesignClassSolutionContent(CodeSubmissionDTO submissionDto) {
        QuestionMetadata metadata = submissionDto.getQuestionMetadata();
        StringBuilder content = new StringBuilder();

        content.append("package ").append(metadata.getFullyQualifiedPackageName()).append(";\n\n");

        // Standard imports
        content.append("import java.util.*;\n");

        // Extract user imports
        String userCode = submissionDto.getUserSolutionCode();
        StringBuilder userImports = new StringBuilder();
        StringBuilder userCodeWithoutImports = new StringBuilder();
        for (String line : userCode.split("\n")) {
            if (line.trim().startsWith("import ")) {
                userImports.append(line).append("\n");
            } else {
                userCodeWithoutImports.append(line).append("\n");
            }
        }

        if (userImports.length() > 0) {
            content.append(userImports);
        }
        content.append("\n");

        // Add custom DS class definitions if needed and not already in user code
        Set<String> requiredDS = detectRequiredDataStructures(metadata);

        // Also scan user code to detect DS usage
        for (String ds : KNOWN_CUSTOM_DS) {
            if (userCode.contains(ds)) {
                requiredDS.add(ds);
            }
        }

        for (String ds : requiredDS) {
            if (!userCode.contains("class " + ds)) {
                log.debug("[SolutionGen] Adding {} class to design-class Solution.java", ds);
                content.append(generateCustomDSClass(ds));
                content.append("\n");
            }
        }

        // Emit user code without imports — user code already contains the class declaration
        content.append(userCodeWithoutImports.toString().trim());

        log.info("[SolutionGen] Design-class Solution.java content length: {}", content.length());
        log.info("[SolutionGen] === END generating design-class Solution.java ===");
        return content.toString();
    }
}