package xyz.hrishabhjoshi.codeexecutionengine.core.pipeline;

import xyz.hrishabhjoshi.codeexecutionengine.dto.ExecutionResult;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class ExecutionOutputParser {

    private ExecutionOutputParser() {
    }

    public static List<ExecutionResult.TestCaseOutput> parseHarnessOutput(
            String output,
            Long memoryBytes,
            Consumer<String> logConsumer) {
        List<ExecutionResult.TestCaseOutput> results = new ArrayList<>();
        String[] lines = output.split("\n");
        int parsedCount = 0;

        for (String line : lines) {
            if (!line.startsWith("TEST_CASE_RESULT:")) {
                continue;
            }
            String dataPart = line.substring("TEST_CASE_RESULT: ".length());
            try {
                int firstCommaIndex = dataPart.indexOf(',');
                int index = Integer.parseInt(dataPart.substring(0, firstCommaIndex).trim());

                int lastCommaIndex = dataPart.lastIndexOf(',');
                int secondLastCommaIndex = dataPart.lastIndexOf(',', lastCommaIndex - 1);

                String actualOutput = dataPart.substring(firstCommaIndex + 1, secondLastCommaIndex).trim();
                String durationString = dataPart.substring(secondLastCommaIndex + 1, lastCommaIndex).trim();
                long duration = Long.parseLong(durationString);

                String errorMessage = null;
                String errorType = null;
                if (lastCommaIndex < dataPart.length() - 1) {
                    String errorInfo = dataPart.substring(lastCommaIndex + 1).trim();
                    if (!errorInfo.isEmpty()) {
                        int colonIndex = errorInfo.indexOf(':');
                        if (colonIndex != -1) {
                            errorType = errorInfo.substring(0, colonIndex).trim();
                            errorMessage = errorInfo.substring(colonIndex + 1).trim();
                        } else {
                            errorType = errorInfo;
                            errorMessage = errorInfo;
                        }
                    }
                }

                results.add(ExecutionResult.TestCaseOutput.builder()
                        .testCaseIndex(index)
                        .actualOutput(actualOutput)
                        .executionTimeMs(duration)
                        .errorMessage(errorMessage)
                        .errorType(errorType)
                        .memoryBytes(memoryBytes)
                        .build());
                parsedCount++;
                if (logConsumer != null) {
                    logConsumer.accept("PARSE_SUCCESS: Test case " + index
                            + ", duration: " + duration + "ms, memory: "
                            + (memoryBytes != null ? memoryBytes + " bytes" : "unavailable"));
                }
            } catch (Exception e) {
                if (logConsumer != null) {
                    logConsumer.accept("PARSE_ERROR: Failed to parse execution output line - "
                            + e.getClass().getSimpleName() + ": " + e.getMessage());
                }
            }
        }

        if (logConsumer != null) {
            logConsumer.accept("PARSE_COMPLETE: Parsed " + parsedCount + " test case results");
        }
        return results;
    }
}
