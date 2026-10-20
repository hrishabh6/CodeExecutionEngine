package xyz.hrishabhjoshi.codeexecutionengine.core.generation;

import xyz.hrishabhjoshi.codeexecutionengine.service.filehandlingservice.FileGenerator;
import xyz.hrishabhjoshi.codeexecutionengine.service.filehandlingservice.java.JavaFileGenerator;
import xyz.hrishabhjoshi.codeexecutionengine.service.filehandlingservice.python.PythonFileGenerator;
import xyz.hrishabhjoshi.codeexecutionengine.service.filehandlingservice.python.PythonInputContentGenerator;
import xyz.hrishabhjoshi.codeexecutionengine.service.filehandlingservice.python.PythonMainContentGenerator;
import xyz.hrishabhjoshi.codeexecutionengine.service.filehandlingservice.python.PythonSolutionContentGenerator;

import java.util.Locale;
import java.util.Map;

public final class FileGeneratorRegistry {

    private final Map<String, FileGenerator> generators;

    public FileGeneratorRegistry(Map<String, FileGenerator> generators) {
        this.generators = Map.copyOf(generators);
    }

    public static FileGeneratorRegistry createDefault() {
        PythonInputContentGenerator inputGenerator = new PythonInputContentGenerator();
        PythonMainContentGenerator mainContentGenerator = new PythonMainContentGenerator(inputGenerator);
        PythonSolutionContentGenerator solutionContentGenerator = new PythonSolutionContentGenerator();
        return new FileGeneratorRegistry(Map.of(
                "java", new JavaFileGenerator(),
                "python", new PythonFileGenerator(mainContentGenerator, solutionContentGenerator)));
    }

    public FileGenerator require(String language) {
        FileGenerator generator = generators.get(language.toLowerCase(Locale.ROOT));
        if (generator == null) {
            throw new IllegalArgumentException("Unsupported language: " + language);
        }
        return generator;
    }
}
