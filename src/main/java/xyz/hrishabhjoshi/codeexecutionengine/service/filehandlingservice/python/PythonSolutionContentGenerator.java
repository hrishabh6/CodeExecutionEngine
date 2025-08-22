// Updated PythonSolutionContentGenerator.java
package xyz.hrishabhjoshi.codeexecutionengine.service.filehandlingservice.python;

import org.springframework.stereotype.Component;
import xyz.hrishabhjoshi.codeexecutionengine.dto.CodeSubmissionDTO;

import java.util.Set;
import java.util.HashSet;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

@Component
public class PythonSolutionContentGenerator {

    public String generateSolutionContent(CodeSubmissionDTO submissionDto) {
        StringBuilder solutionContent = new StringBuilder();

        // First add necessary imports
        solutionContent.append("from typing import Optional, List\n");

        // Check what custom data structures are used in the user's code
        String userCode = submissionDto.getUserSolutionCode();
        Set<String> neededStructures = findCustomDataStructuresInCode(userCode);

        // Also check the metadata for additional structures
        CodeSubmissionDTO.QuestionMetadata metadata = submissionDto.getQuestionMetadata();
        if (metadata.getParameters() != null) {
            for (var param : metadata.getParameters()) {
                String extractedType = extractCustomDataStructure(param.getType());
                if (extractedType != null) {
                    neededStructures.add(extractedType);
                }
            }
        }

        if (metadata.getReturnType() != null) {
            String extractedType = extractCustomDataStructure(metadata.getReturnType());
            if (extractedType != null) {
                neededStructures.add(extractedType);
            }
        }

        System.out.println("DEBUG: Structures needed in solution.py: " + neededStructures);

        // Add class definitions for needed structures
        for (String structureName : neededStructures) {
            solutionContent.append(PythonCustomDSGenerator.generateClass(structureName));
            System.out.println("DEBUG: Added " + structureName + " class to solution.py");
        }

        // Add a blank line before user code if we added any classes
        if (!neededStructures.isEmpty()) {
            solutionContent.append("\n");
        }

        // Add the user's code
        solutionContent.append(userCode);

        return solutionContent.toString();
    }

    /**
     * Find custom data structures mentioned in the user's code
     */
    private Set<String> findCustomDataStructuresInCode(String code) {
        Set<String> structures = new HashSet<>();

        // Look for type hints and variable usage
        Pattern pattern = Pattern.compile("\\b(ListNode|TreeNode|Node)\\b");
        Matcher matcher = pattern.matcher(code);

        while (matcher.find()) {
            structures.add(matcher.group(1));
        }

        return structures;
    }

    /**
     * Extract custom data structure names from complex type declarations
     */
    private String extractCustomDataStructure(String type) {
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