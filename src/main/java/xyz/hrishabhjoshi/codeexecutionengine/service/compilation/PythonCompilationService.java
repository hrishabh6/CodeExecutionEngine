package xyz.hrishabhjoshi.codeexecutionengine.service.compilation;

import org.springframework.stereotype.Service;
import xyz.hrishabhjoshi.codeexecutionengine.dto.CompilationResult;

import java.io.IOException;
import java.nio.file.Path;
import java.util.function.Consumer;

@Service("pythonCompilationService")
public class PythonCompilationService implements CompilationService {

    @Override
    public String getLanguage() {
        return "python";
    }

    @Override
    public CompilationResult compile(String submissionId, String fullyQualifiedPackageName, Path submissionPath, Consumer<String> logConsumer)
            throws IOException, InterruptedException {

        logConsumer.accept("COMPILE_SERVICE: No compilation needed for Python.");
        return new CompilationResult(true, "No compilation errors. Python is an interpreted language.");
    }
}