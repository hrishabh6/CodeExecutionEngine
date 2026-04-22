package xyz.hrishabhjoshi.codeexecutionengine.service.codeexecutionservice;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import xyz.hrishabhjoshi.codeexecutionengine.dto.CodeExecutionResultDTO;
import xyz.hrishabhjoshi.codeexecutionengine.dto.CodeSubmissionDTO;

import java.nio.file.Path;
import java.util.Locale;
import java.util.function.Consumer;

@Service
@Primary
public class RoutingCodeExecutor implements CodeExecutor {

    private final LocalProcessCodeExecutor localProcessCodeExecutor;
    private final KubernetesJobExecutor kubernetesJobExecutor;

    @Value("${execution.backend:worker-pod}")
    private String backend;

    public RoutingCodeExecutor(
            LocalProcessCodeExecutor localProcessCodeExecutor,
            KubernetesJobExecutor kubernetesJobExecutor) {
        this.localProcessCodeExecutor = localProcessCodeExecutor;
        this.kubernetesJobExecutor = kubernetesJobExecutor;
    }

    @Override
    public CodeExecutionResultDTO execute(
            CodeSubmissionDTO submissionDto,
            String submissionId,
            String executionId,
            Path submissionRootPath,
            String fullyQualifiedMainClass,
            String language,
            Consumer<String> logConsumer) {

        return switch (backend.toLowerCase(Locale.ROOT)) {
            case "local-process", "worker-pod" -> localProcessCodeExecutor.execute(
                    submissionDto,
                    submissionId,
                    executionId,
                    submissionRootPath,
                    fullyQualifiedMainClass,
                    language,
                    logConsumer);
            case "kubernetes-job" -> kubernetesJobExecutor.execute(
                    submissionDto,
                    submissionId,
                    executionId,
                    submissionRootPath,
                    fullyQualifiedMainClass,
                    language,
                    logConsumer);
            default -> throw new IllegalArgumentException("Unsupported execution backend: " + backend);
        };
    }
}
