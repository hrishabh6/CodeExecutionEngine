package xyz.hrishabhjoshi.codeexecutionengine.service.filehandlingservice.java;

import xyz.hrishabhjoshi.codeexecutionengine.dto.CodeSubmissionDTO.QuestionMetadata;
import xyz.hrishabhjoshi.codeexecutionengine.dto.ParamInfoDTO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;

public class InputVariableGenerator {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static void generateInputVariableDeclarations(StringBuilder builder, QuestionMetadata metadata, String inputJson, int testCaseIndex) throws JsonProcessingException {
        System.out.println("LOGGING: [InputVarGen] Generating variables for testCase " + testCaseIndex);
        System.out.println("LOGGING: [InputVarGen] inputJson = " + inputJson);
        
        JsonNode inputNode = objectMapper.readTree(inputJson);
        List<ParamInfoDTO> parameters = metadata.getParameters();
        
        System.out.println("LOGGING: [InputVarGen] inputNode.isObject() = " + inputNode.isObject());
        System.out.println("LOGGING: [InputVarGen] parameters count = " + (parameters != null ? parameters.size() : 0));

        if (inputNode.isObject()) {
            if (parameters == null || parameters.isEmpty()) {
                System.out.println("LOGGING: [InputVarGen] ERROR: No parameters in metadata!");
                return;
            }
            for (ParamInfoDTO param : parameters) {
                String paramType = param.getType();
                String paramName = param.getName();
                JsonNode paramValueNode = inputNode.get(paramName);

                System.out.println("LOGGING: [InputVarGen] Looking for param: " + paramName + " (type: " + paramType + ")");
                System.out.println("LOGGING: [InputVarGen] Found value: " + (paramValueNode != null ? paramValueNode.toString() : "NULL"));

                if (paramValueNode != null) {
                    String declarationValue = ValueDeclarationGenerator.generateValueDeclaration(paramType, paramValueNode, metadata.getCustomDataStructureNames());
                    builder.append("            ").append(paramType).append(" ").append(paramName).append(testCaseIndex)
                            .append(" = ").append(declarationValue).append(";\n");
                    System.out.println("LOGGING: [InputVarGen] Generated: " + paramType + " " + paramName + testCaseIndex);
                } else {
                    System.out.println("LOGGING: [InputVarGen] WARNING: paramValueNode is NULL for " + paramName);
                }
            }
        } else {
            System.out.println("LOGGING: [InputVarGen] ERROR: inputNode is NOT an object! Actual type: " + inputNode.getNodeType());
        }
    }

    public static String generateFunctionParameters(List<ParamInfoDTO> parameters, int testCaseIndex) {
        StringBuilder params = new StringBuilder();
        for (int j = 0; j < parameters.size(); j++) {
            if (j > 0) params.append(", ");
            params.append(parameters.get(j).getName()).append(testCaseIndex);
        }
        return params.toString();
    }
}
