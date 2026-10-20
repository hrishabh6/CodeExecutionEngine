package xyz.hrishabhjoshi.codeexecutionengine.core.generation;

import xyz.hrishabhjoshi.codeexecutionengine.dto.CodeSubmissionDTO;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Trusted runner/orchestrator helper: generate sources into an existing workspace root.
 */
public final class SourceWorkspaceGenerator {

    private final FileGeneratorRegistry registry;

    public SourceWorkspaceGenerator(FileGeneratorRegistry registry) {
        this.registry = registry;
    }

    public static SourceWorkspaceGenerator withDefaultRegistry() {
        return new SourceWorkspaceGenerator(FileGeneratorRegistry.createDefault());
    }

    public void generateIntoWorkspace(CodeSubmissionDTO submission, Path workspaceRoot) throws IOException {
        registry.require(submission.getLanguage()).generateFiles(submission, workspaceRoot);
    }
}
