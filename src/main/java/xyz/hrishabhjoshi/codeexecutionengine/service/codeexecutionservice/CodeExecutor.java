package xyz.hrishabhjoshi.codeexecutionengine.service.codeexecutionservice;

import xyz.hrishabhjoshi.codeexecutionengine.dto.CodeExecutionResultDTO;
import xyz.hrishabhjoshi.codeexecutionengine.dto.CodeSubmissionDTO;

import java.nio.file.Path;
import java.util.function.Consumer;

public interface CodeExecutor {

    CodeExecutionResultDTO execute(
            CodeSubmissionDTO submissionDto,
            String submissionId,
            String executionId,
            Path submissionRootPath,
            String fullyQualifiedMainClass,
            String language,
            Consumer<String> logConsumer);
}
