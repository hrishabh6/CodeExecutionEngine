package xyz.hrishabhjoshi.codeexecutionengine.complexityprofile.harness;

import com.fasterxml.jackson.databind.ObjectMapper;
import xyz.hrishabhjoshi.codeexecutionengine.complexityprofile.dto.ComplexityProfileJobPayload;
import xyz.hrishabhjoshi.codeexecutionengine.config.ComplexityProfileExecutionProperties;
import xyz.hrishabhjoshi.codeexecutionengine.config.ExecutionRuntimeProperties;
import xyz.hrishabhjoshi.codeexecutionengine.service.utils.ManagedProcessRunner;

import java.nio.file.Path;

/**
 * Entry point for the bounded profiler child JVM (not a Spring context).
 */
public final class ComplexityProfileChildMain {

    private ComplexityProfileChildMain() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            System.exit(2);
        }
        ObjectMapper objectMapper = new ObjectMapper();
        ComplexityProfileJobPayload job = objectMapper.readValue(Path.of(args[0]).toFile(), ComplexityProfileJobPayload.class);
        Path resultFile = Path.of(args[1]);
        ComplexityProfileExecutionProperties properties = objectMapper.readValue(
                Path.of(args[2]).toFile(), ComplexityProfileExecutionProperties.class);

        ExecutionRuntimeProperties runtimeProperties = new ExecutionRuntimeProperties();
        runtimeProperties.getLanguages().put("java", defaultJavaRuntime());

        ComplexityProfileMeasurementEngine engine = new ComplexityProfileMeasurementEngine(
                new ManagedProcessRunner(),
                runtimeProperties,
                properties,
                objectMapper);
        ComplexityProfileJavaHarness.HarnessRunResult result = engine.execute(job);
        objectMapper.writeValue(resultFile.toFile(), result);
        System.exit(0);
    }

    private static ExecutionRuntimeProperties.LanguageRuntime defaultJavaRuntime() {
        ExecutionRuntimeProperties.LanguageRuntime runtime = new ExecutionRuntimeProperties.LanguageRuntime();
        runtime.setCompile("javac");
        runtime.setRun("java");
        return runtime;
    }
}
