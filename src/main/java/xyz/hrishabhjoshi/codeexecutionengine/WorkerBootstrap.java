package xyz.hrishabhjoshi.codeexecutionengine;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import xyz.hrishabhjoshi.codeexecutionengine.service.helperservice.ExecutionWorkerService;

/**
 * Bootstraps worker threads on application startup.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkerBootstrap implements CommandLineRunner {

    private final ExecutionWorkerService workerService;

    @Value("${execution.worker.count:5}")
    private int workerCount;

    @Override
    public void run(String... args) {
        log.info("==============================================");
        log.info("[BOOTSTRAP] Code Execution Engine starting...");
        log.info("[BOOTSTRAP] Configured worker count: {}", workerCount);
        log.info("==============================================");

        for (int i = 1; i <= workerCount; i++) {
            String workerId = "worker-" + i;
            log.info("[BOOTSTRAP] Launching {}", workerId);
            workerService.startWorker(workerId);
        }

        log.info("==============================================");
        log.info("[BOOTSTRAP] All {} workers launched!", workerCount);
        log.info("[BOOTSTRAP] CXE is ready to accept submissions");
        log.info("==============================================");
    }
}
