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

    public static void generateInputVariableDeclarations(StringBuilder builder, QuestionMetadata metadata,
            String inputJson, int testCaseIndex) throws JsonProcessingException {
        log.debug("[InputVarGen] Generating variables for testCase {}", testCaseIndex);
        log.debug("[InputVarGen] inputJson = {}", inputJson);

        JsonNode inputNode = objectMapper.readTree(inputJson);
        List<ParamInfoDTO> parameters = metadata.getParameters();

        log.debug("[InputVarGen] inputNode type = {}", inputNode.getNodeType());
        log.debug("[InputVarGen] parameters count = {}", (parameters != null ? parameters.size() : 0));

        if (parameters == null || parameters.isEmpty()) {
            log.error("[InputVarGen] ERROR: No parameters in metadata!");
            return;
        }

        if (inputNode.isObject()) {
            // Handle Object input (map of paramName -> value)
            for (ParamInfoDTO param : parameters) {
                String paramType = param.getType();
                String paramName = param.getName();
                JsonNode paramValueNode = inputNode.get(paramName);

                log.debug("[InputVarGen] Looking for param: {} (type: {})", paramName, paramType);
                if (paramValueNode != null) {
                    generateVariable(builder, paramType, paramName, paramValueNode, metadata, testCaseIndex);
                } else {
                    log.warn("[InputVarGen] WARNING: paramValueNode is NULL for {}", paramName);
                }
            }
        } else if (inputNode.isArray()) {
            // Handle Array input (ordered list of values)
            if (inputNode.size() != parameters.size()) {
                log.warn("[InputVarGen] WARNING: Input array size ({}) does not match parameters count ({})",
                        inputNode.size(), parameters.size());
            }

            for (int i = 0; i < parameters.size(); i++) {
                ParamInfoDTO param = parameters.get(i);
                String paramType = param.getType();
                String paramName = param.getName();

                if (i < inputNode.size()) {
                    JsonNode paramValueNode = inputNode.get(i);
                    generateVariable(builder, paramType, paramName, paramValueNode, metadata, testCaseIndex);
                } else {
                    log.error("[InputVarGen] ERROR: Missing value for param {} at index {}", paramName, i);
                }
            }
        } else {
            log.error("[InputVarGen] ERROR: inputNode is NOT an object or array! Actual type: {}",
                    inputNode.getNodeType());
        }
    }

    private static void generateVariable(StringBuilder builder, String paramType, String paramName,
            JsonNode paramValueNode, QuestionMetadata metadata, int testCaseIndex) throws JsonProcessingException {
        String declarationValue = ValueDeclarationGenerator.generateValueDeclaration(paramType, paramValueNode,
                metadata.getCustomDataStructureNames());
        
        // [DEBUG_TRACE] Log generated variable declaration
        log.info(">>> [DEBUG_TRACE] Generating variable: type={}, name={}, valNode={}", paramType, paramName, paramValueNode);
        log.info(">>> [DEBUG_TRACE] Generated code: {} {}{} = {};", paramType, paramName, testCaseIndex, declarationValue);

        builder.append("            ").append(paramType).append(" ").append(paramName).append(testCaseIndex)
                .append(" = ").append(declarationValue).append(";\n");
        log.debug("[InputVarGen] Generated: {} {}{}", paramType, paramName, testCaseIndex);
    }

    public static String generateFunctionParameters(List<ParamInfoDTO> parameters, int testCaseIndex) {
        StringBuilder params = new StringBuilder();
        for (int j = 0; j < parameters.size(); j++) {
            if (j > 0)
                params.append(", ");
            params.append(parameters.get(j).getName()).append(testCaseIndex);
        }
        return params.toString();
    }
}
