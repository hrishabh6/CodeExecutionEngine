package xyz.hrishabhjoshi.codeexecutionengine.runner;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskExecutionPipelineJvmOptionsTest {

    @Test
    void boundsProcessorDerivedJvmThreadSizing() {
        String options = TaskExecutionPipeline.javaToolOptionsPrefix();

        assertTrue(options.contains("-XX:ActiveProcessorCount=2"));
        assertTrue(options.contains("-Xmx256m"));
        assertTrue(options.contains("-XX:MaxMetaspaceSize=128m"));
        assertTrue(options.contains("-XX:CompressedClassSpaceSize=64m"));
        assertTrue(options.contains("-XX:ReservedCodeCacheSize=32m"));
    }
}
