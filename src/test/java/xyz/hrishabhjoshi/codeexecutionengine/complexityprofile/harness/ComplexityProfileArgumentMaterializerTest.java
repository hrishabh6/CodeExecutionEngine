package xyz.hrishabhjoshi.codeexecutionengine.complexityprofile.harness;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.junit.jupiter.api.Test;
import xyz.hrishabhjoshi.codeexecutionengine.complexityprofile.dto.ComplexityProfileExecutionDtos.ParameterDto;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ComplexityProfileArgumentMaterializerTest {

    @Test
    void eachMaterializationProducesFreshMutableArray() {
        ObjectMapper mapper = new ObjectMapper();
        ComplexityProfileArgumentMaterializer materializer = new ComplexityProfileArgumentMaterializer(mapper);
        ArrayNode input = mapper.createArrayNode();
        ArrayNode nums = mapper.createArrayNode().add(1).add(2).add(3);
        input.add(nums);
        Object[] first = materializer.materializeArguments(input, List.of(new ParameterDto("nums", "int[]")));
        Object[] second = materializer.materializeArguments(input, List.of(new ParameterDto("nums", "int[]")));
        int[] firstArray = (int[]) first[0];
        int[] secondArray = (int[]) second[0];
        assertNotSame(firstArray, secondArray);
        assertArrayEquals(firstArray, secondArray);
        firstArray[0] = 99;
        assertArrayEquals(new int[] {1, 2, 3}, secondArray);
    }

    @Test
    void rejectsUnsupportedMutableParameterTypes() {
        ObjectMapper mapper = new ObjectMapper();
        ComplexityProfileArgumentMaterializer materializer = new ComplexityProfileArgumentMaterializer(mapper);
        var input = mapper.createArrayNode().add(mapper.createObjectNode().put("k", 1));
        assertThrows(
                ComplexityProfileArgumentMaterializer.UnsupportedMutableInputTypeException.class,
                () -> materializer.materializeArguments(input, List.of(new ParameterDto("graph", "List<List<Integer>>"))));
    }
}
