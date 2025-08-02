package com.hrishabh.codeexecutionengine.service.compilation;

import com.hrishabh.codeexecutionengine.dto.CompilationResult;

import java.io.IOException;
import java.nio.file.Path;
import java.util.function.Consumer;

public interface CompilationService {

    String getLanguage();

    CompilationResult compile(String submissionId, String fullyQualifiedPackageName, Path submissionPath, Consumer<String> logConsumer)
            throws IOException, InterruptedException;
}