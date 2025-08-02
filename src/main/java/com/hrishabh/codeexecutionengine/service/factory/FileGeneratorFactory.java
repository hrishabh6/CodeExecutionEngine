package com.hrishabh.codeexecutionengine.service.factory;


import com.hrishabh.codeexecutionengine.service.filehandlingservice.FileGenerator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class FileGeneratorFactory {

    private final Map<String, FileGenerator> generators;

    @Autowired
    public FileGeneratorFactory(@Qualifier("javaFileGenerator") FileGenerator javaFileGenerator) {
        // You would add more generators here as you implement them
        // For example: @Qualifier("pythonFileGenerator") FileGenerator pythonFileGenerator
        this.generators = Map.of(
                "java", javaFileGenerator
                // "python", pythonFileGenerator // Uncomment when you have a Python implementation
        );
    }

    /**
     * Retrieves the appropriate FileGenerator implementation for a given language.
     *
     * @param language The language of the submission (e.g., "java").
     * @return A FileGenerator implementation.
     * @throws IllegalArgumentException If no generator is found for the specified language.
     */
    public FileGenerator getFileGenerator(String language) {
        FileGenerator generator = generators.get(language.toLowerCase());
        if (generator == null) {
            throw new IllegalArgumentException("No file generator found for language: " + language);
        }
        return generator;
    }
}