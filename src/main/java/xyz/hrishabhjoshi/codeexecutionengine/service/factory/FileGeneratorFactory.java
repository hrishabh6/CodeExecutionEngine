package xyz.hrishabhjoshi.codeexecutionengine.service.factory;

import org.springframework.stereotype.Component;
import xyz.hrishabhjoshi.codeexecutionengine.core.generation.FileGeneratorRegistry;
import xyz.hrishabhjoshi.codeexecutionengine.service.filehandlingservice.FileGenerator;

@Component
public class FileGeneratorFactory {

    private final FileGeneratorRegistry registry;

    public FileGeneratorFactory() {
        this(FileGeneratorRegistry.createDefault());
    }

    FileGeneratorFactory(FileGeneratorRegistry registry) {
        this.registry = registry;
    }

    public FileGenerator getFileGenerator(String language) {
        return registry.require(language);
    }
}
