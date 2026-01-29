package xyz.hrishabhjoshi.codeexecutionengine.service.filehandlingservice.java;

import xyz.hrishabhjoshi.codeexecutionengine.dto.CodeSubmissionDTO.QuestionMetadata;
import xyz.hrishabhjoshi.codeexecutionengine.dto.ParamInfoDTO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
public class InputVariableGenerator {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static void generateInputVariableDeclarations(StringBuilder builder, QuestionMetadata metadata, String inputJson, int testCaseIndex) throws JsonProcessingException {
        log.debug("[InputVarGen] Generating variables for testCase {}", testCaseIndex);
        log.debug("[InputVarGen] inputJson = {}", inputJson);

        JsonNode inputNode = objectMapper.readTree(inputJson);
        List<ParamInfoDTO> parameters = metadata.getParameters();

        log.debug("[InputVarGen] inputNode.isObject() = {}", inputNode.isObject());
        log.debug("[InputVarGen] parameters count = {}", (parameters != null ? parameters.size() : 0));

        if (inputNode.isObject()) {
            if (parameters == null || parameters.isEmpty()) {
                log.error("[InputVarGen] ERROR: No parameters in metadata!");
                return;
            }
            for (ParamInfoDTO param : parameters) {
                String paramType = param.getType();
                String paramName = param.getName();
                JsonNode paramValueNode = inputNode.get(paramName);

                log.debug("[InputVarGen] Looking for param: {} (type: {})", paramName, paramType);
                log.debug("[InputVarGen] Found value: {}", (paramValueNode != null ? paramValueNode.toString() : "NULL"));

                if (paramValueNode != null) {
                    String declarationValue = ValueDeclarationGenerator.generateValueDeclaration(paramType, paramValueNode, metadata.getCustomDataStructureNames());
                    builder.append("            ").append(paramType).append(" ").append(paramName).append(testCaseIndex)
                            .append(" = ").append(declarationValue).append(";\n");
                    log.debug("[InputVarGen] Generated: {} {}{}", paramType, paramName, testCaseIndex);
                } else {
                    log.warn("[InputVarGen] WARNING: paramValueNode is NULL for {}", paramName);
                }
            }
        } else {
            log.error("[InputVarGen] ERROR: inputNode is NOT an object! Actual type: {}", inputNode.getNodeType());
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
