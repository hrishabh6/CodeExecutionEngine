package xyz.hrishabhjoshi.codeexecutionengine.complexityprofile.harness;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import xyz.hrishabhjoshi.codeexecutionengine.complexityprofile.dto.ComplexityProfileExecutionDtos.ParameterDto;

import java.util.ArrayList;
import java.util.List;

/**
 * Reconstructs fresh mutable Java arguments from JSON outside timed regions.
 */
public class ComplexityProfileArgumentMaterializer {

    private final ObjectMapper objectMapper;

    public ComplexityProfileArgumentMaterializer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Object[] materializeArguments(JsonNode inputRoot, List<ParameterDto> parameters) {
        if (parameters == null || parameters.isEmpty()) {
            throw new IllegalArgumentException("parameters required");
        }
        if (inputRoot.isArray()) {
            if (inputRoot.size() != parameters.size()) {
                throw new IllegalArgumentException("input arity mismatch");
            }
            Object[] args = new Object[parameters.size()];
            for (int i = 0; i < parameters.size(); i++) {
                args[i] = materializeValue(inputRoot.get(i), parameters.get(i).type());
            }
            return args;
        }
        throw new IllegalArgumentException("input must be JSON array for V1 harness");
    }

    private Object materializeValue(JsonNode node, String type) {
        return switch (normalize(type)) {
            case "int", "integer" -> node.asInt();
            case "long" -> node.asLong();
            case "double" -> node.asDouble();
            case "boolean" -> node.asBoolean();
            case "string" -> node.asText();
            case "int[]" -> cloneIntArray(node);
            case "int[][]" -> cloneIntMatrix(node);
            case "long[]" -> cloneLongArray(node);
            default -> throw new UnsupportedMutableInputTypeException(type);
        };
    }

    public static final class UnsupportedMutableInputTypeException extends IllegalArgumentException {
        public UnsupportedMutableInputTypeException(String type) {
            super("unsupported mutable input type for profiling: " + type);
        }
    }

    private static int[] cloneIntArray(JsonNode node) {
        int[] values = new int[node.size()];
        for (int i = 0; i < node.size(); i++) {
            values[i] = node.get(i).asInt();
        }
        return values;
    }

    private static long[] cloneLongArray(JsonNode node) {
        long[] values = new long[node.size()];
        for (int i = 0; i < node.size(); i++) {
            values[i] = node.get(i).asLong();
        }
        return values;
    }

    private static int[][] cloneIntMatrix(JsonNode node) {
        int rows = node.size();
        int[][] matrix = new int[rows][];
        for (int r = 0; r < rows; r++) {
            JsonNode row = node.get(r);
            matrix[r] = new int[row.size()];
            for (int c = 0; c < row.size(); c++) {
                matrix[r][c] = row.get(c).asInt();
            }
        }
        return matrix;
    }

    private static String normalize(String type) {
        return type == null ? "" : type.trim().toLowerCase().replace("integer", "int");
    }
}
