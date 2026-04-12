package xyz.hrishabhjoshi.codeexecutionengine.service.utils;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

@Component
public class ManagedProcessRunner {

    @Value("${execution.runtime.memory-soft-limit-mb:256}")
    private long memorySoftLimitMb;

    public ProcessExecutionResult run(
            List<String> command,
            Path workingDirectory,
            Consumer<String> logConsumer,
            long timeoutSeconds,
            String logPrefix) throws IOException, InterruptedException {
        return run(command, workingDirectory, logConsumer, timeoutSeconds, logPrefix, false);
    }

    public ProcessExecutionResult run(
            List<String> command,
            Path workingDirectory,
            Consumer<String> logConsumer,
            long timeoutSeconds,
            String logPrefix,
            boolean skipMemoryLimit) throws IOException, InterruptedException {

        ProcessBuilder processBuilder = new ProcessBuilder(
                skipMemoryLimit ? command : applySoftMemoryLimit(command));
        processBuilder.directory(workingDirectory.toFile());
        processBuilder.redirectErrorStream(true);

        Process process = processBuilder.start();
        StringBuilder output = new StringBuilder();
        AtomicLong peakMemoryBytes = new AtomicLong(0);

        Thread outputReader = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append('\n');
                    logConsumer.accept(logPrefix + ": " + line);
                }
            } catch (IOException e) {
                logConsumer.accept(logPrefix + "_ERROR: " + e.getMessage());
            }
        });
        outputReader.setDaemon(true);
        outputReader.start();

        // Monitor peak RSS memory of the child process
        long pid = process.pid();
        Thread memoryMonitor = new Thread(() -> {
            Path statusPath = Path.of("/proc/" + pid + "/status");
            while (process.isAlive()) {
                try {
                    List<String> lines = Files.readAllLines(statusPath);
                    for (String l : lines) {
                        if (l.startsWith("VmRSS:")) {
                            String kb = l.replaceAll("[^0-9]", "");
                            long bytes = Long.parseLong(kb) * 1024;
                            peakMemoryBytes.updateAndGet(prev -> Math.max(prev, bytes));
                            break;
                        }
                    }
                    Thread.sleep(100);
                } catch (Exception ignored) {
                    break;
                }
            }
        }, "memory-monitor-" + pid);
        memoryMonitor.setDaemon(true);
        memoryMonitor.start();

        boolean timedOut = !process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        int exitCode;

        if (timedOut) {
            logConsumer.accept(logPrefix + "_TIMEOUT: Killing process tree after " + timeoutSeconds + " seconds");
            destroyProcessTree(process, logConsumer, logPrefix);
            exitCode = -999;
        } else {
            exitCode = process.exitValue();
        }

        outputReader.join(2000);
        memoryMonitor.join(500);

        return new ProcessExecutionResult(output.toString(), exitCode, timedOut, peakMemoryBytes.get());
    }

    private List<String> applySoftMemoryLimit(List<String> command) {
        if (memorySoftLimitMb <= 0) {
            return command;
        }

        long memorySoftLimitKb = memorySoftLimitMb * 1024;
        StringBuilder shellCommand = new StringBuilder("ulimit -Sv ")
                .append(memorySoftLimitKb)
                .append(" && exec ");

        for (int i = 0; i < command.size(); i++) {
            if (i > 0) {
                shellCommand.append(' ');
            }
            shellCommand.append(shellQuote(command.get(i)));
        }

        List<String> wrapped = new ArrayList<>();
        wrapped.add("sh");
        wrapped.add("-lc");
        wrapped.add(shellCommand.toString());
        return wrapped;
    }

    private void destroyProcessTree(Process process, Consumer<String> logConsumer, String logPrefix)
            throws InterruptedException {
        ProcessHandle handle = process.toHandle();

        handle.descendants()
                .sorted((left, right) -> Long.compare(right.pid(), left.pid()))
                .forEach(descendant -> destroyHandle(descendant, logConsumer, logPrefix));

        destroyHandle(handle, logConsumer, logPrefix);
        process.waitFor(2, TimeUnit.SECONDS);
    }

    private void destroyHandle(ProcessHandle handle, Consumer<String> logConsumer, String logPrefix) {
        if (!handle.isAlive()) {
            return;
        }

        logConsumer.accept(logPrefix + "_KILL: terminating pid=" + handle.pid());
        handle.destroy();

        try {
            handle.onExit().get(500, TimeUnit.MILLISECONDS);
        } catch (Exception ignored) {
            if (handle.isAlive()) {
                logConsumer.accept(logPrefix + "_KILL: force-killing pid=" + handle.pid());
                handle.destroyForcibly();
            }
        }
    }

    private String shellQuote(String value) {
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }

    public record ProcessExecutionResult(String output, int exitCode, boolean timedOut, long peakMemoryBytes) {
    }
}
