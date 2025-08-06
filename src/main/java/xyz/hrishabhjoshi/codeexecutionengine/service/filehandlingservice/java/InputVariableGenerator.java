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
        JsonNode inputNode = objectMapper.readTree(inputJson);
        List<ParamInfoDTO> parameters = metadata.getParameters();

        if (inputNode.isObject()) {
            for (ParamInfoDTO param : parameters) {
                String paramType = param.getType();
                String paramName = param.getName();
                JsonNode paramValueNode = inputNode.get(paramName);

                if (paramValueNode != null) {
                    String declarationValue = ValueDeclarationGenerator.generateValueDeclaration(paramType, paramValueNode, metadata.getCustomDataStructureNames());
                    builder.append("            ").append(paramType).append(" ").append(paramName).append(testCaseIndex)
                            .append(" = ").append(declarationValue).append(";\n");
                }
            }
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