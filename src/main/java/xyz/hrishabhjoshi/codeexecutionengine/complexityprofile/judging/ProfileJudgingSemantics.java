package xyz.hrishabhjoshi.codeexecutionengine.complexityprofile.judging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import xyz.hrishabhjoshi.codeexecutionengine.complexityprofile.dto.ComplexityProfileExecutionDtos.QuestionMetadataDto;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Mirrors AlgoCrack Submission {@code JudgingPipeline} normalize/compare semantics for profile validation.
 * Keeps complexity profiling aligned with normal oracle judging (unordered nested lists, etc.).
 */
public final class ProfileJudgingSemantics {

    private final ObjectMapper objectMapper;

    public ProfileJudgingSemantics(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public boolean matches(Object actualReturnValue, JsonNode expectedOutput, QuestionMetadataDto metadata) {
        JsonNode actualNode = objectMapper.valueToTree(actualReturnValue);
        JsonNode normalizedActual = normalize(actualNode, metadata);
        JsonNode normalizedExpected = normalize(expectedOutput.deepCopy(), metadata);
        return compare(normalizedActual, normalizedExpected, metadata);
    }

    private JsonNode normalize(JsonNode node, QuestionMetadataDto metadata) {
        if (node == null || node.isNull()) {
            return node;
        }
        Boolean orderMatters = metadata.isOutputOrderMatters();
        if (orderMatters != null && !orderMatters && node.isArray()) {
            if (isNestedListType(metadata.returnType())) {
                return sortNestedList(node);
            }
            if (isListType(metadata.returnType())) {
                return sortTopLevelList(node);
            }
        }
        return node;
    }

    private boolean compare(JsonNode actual, JsonNode expected, QuestionMetadataDto metadata) {
        if (actual == null || expected == null) {
            return actual == expected;
        }
        Boolean orderMatters = metadata.isOutputOrderMatters();
        if (orderMatters != null && !orderMatters) {
            return actual.equals(expected);
        }
        if (isListType(metadata.returnType()) && actual.isArray() && expected.isArray()) {
            return actual.equals(expected);
        }
        return actual.equals(expected);
    }

    private JsonNode sortTopLevelList(JsonNode node) {
        List<JsonNode> elements = new ArrayList<>();
        node.forEach(elements::add);
        elements.sort(Comparator.comparing(JsonNode::toString));
        ArrayNode sorted = objectMapper.createArrayNode();
        elements.forEach(sorted::add);
        return sorted;
    }

    private JsonNode sortNestedList(JsonNode node) {
        List<ArrayNode> normalizedInner = new ArrayList<>();
        for (JsonNode innerElement : node) {
            if (innerElement.isArray()) {
                List<JsonNode> innerList = new ArrayList<>();
                innerElement.forEach(innerList::add);
                innerList.sort(Comparator.comparing(JsonNode::toString));
                ArrayNode sortedInner = objectMapper.createArrayNode();
                innerList.forEach(sortedInner::add);
                normalizedInner.add(sortedInner);
            } else {
                ArrayNode wrapper = objectMapper.createArrayNode();
                wrapper.add(innerElement);
                normalizedInner.add(wrapper);
            }
        }
        normalizedInner.sort(Comparator.comparing(ArrayNode::toString));
        ArrayNode result = objectMapper.createArrayNode();
        normalizedInner.forEach(result::add);
        return result;
    }

    static boolean isListType(String returnType) {
        if (returnType == null) {
            return false;
        }
        String lower = returnType.toLowerCase();
        return lower.startsWith("list") || lower.endsWith("[]") || lower.contains("array");
    }

    static boolean isNestedListType(String returnType) {
        if (returnType == null) {
            return false;
        }
        String lower = returnType.toLowerCase();
        return lower.contains("list<list") || lower.contains("[][]");
    }
}
