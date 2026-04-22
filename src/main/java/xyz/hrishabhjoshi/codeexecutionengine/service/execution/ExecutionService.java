package xyz.hrishabhjoshi.codeexecutionengine.service.execution;

import xyz.hrishabhjoshi.codeexecutionengine.dto.ExecutionResult; // Corrected import to new ExecutionResult DTO
import java.io.IOException;
import java.nio.file.Path;
import java.util.function.Consumer;

public interface ExecutionService {
    String getLanguage(); // "java", "cpp"

    /**
     * Executes code located at the given submissionPath inside the current worker
     * runtime.
     *
     * @param submissionId     The unique ID for the submission.
     * @param submissionPath   The absolute path to the generated submission workspace.
     * @param fullyQualifiedMainClass The fully qualified name of the Main class (e.g., "mycode.Main").
     * @param logConsumer      A consumer to handle real-time process log lines.
     * @return An ExecutionResult object containing execution logs and test case outputs.
     * @throws IOException If an I/O error occurs while starting the execution process.
     * @throws InterruptedException If the process is interrupted.
     */
    ExecutionResult run(String submissionId, Path submissionPath, String fullyQualifiedMainClass, Consumer<String> logConsumer) throws IOException, InterruptedException;
}
