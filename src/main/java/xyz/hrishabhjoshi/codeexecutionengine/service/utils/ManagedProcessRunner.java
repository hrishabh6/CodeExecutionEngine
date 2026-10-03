package xyz.hrishabhjoshi.codeexecutionengine.service.utils;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
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

    public record SeparatedProcessExecutionResult(
            String stdout,
            String stderr,
            boolean outputTruncated,
            int exitCode,
            boolean timedOut,
            long peakMemoryBytes,
            long wallClockMs) {
    }

    /**
     * Runs a process with separate bounded stdout/stderr capture, optional stdin, and no child output in logs.
     */
    public SeparatedProcessExecutionResult runSeparated(
            List<String> command,
            Path workingDirectory,
            byte[] stdin,
            long timeoutSeconds,
            int maxCombinedOutputBytes,
            boolean skipMemoryLimit,
            String logLabel) throws IOException, InterruptedException {

        long started = System.currentTimeMillis();
        ProcessBuilder processBuilder = new ProcessBuilder(
                skipMemoryLimit ? command : applySoftMemoryLimit(command));
        processBuilder.directory(workingDirectory.toFile());
        processBuilder.redirectErrorStream(false);

        Process process = processBuilder.start();

        if (stdin != null && stdin.length > 0) {
            try (OutputStream processStdin = process.getOutputStream()) {
                processStdin.write(stdin);
            } catch (IOException ignored) {
                // Broken pipe if process exits early
            }
        } else {
            process.getOutputStream().close();
        }

        AtomicInteger budget = new AtomicInteger(Math.max(maxCombinedOutputBytes, 0));
        AtomicBoolean truncated = new AtomicBoolean(false);
        StringBuilder stdoutBuilder = new StringBuilder();
        StringBuilder stderrBuilder = new StringBuilder();

        Thread stdoutReader = new Thread(() -> drainStream(
                process.getInputStream(), stdoutBuilder, budget, truncated), logLabel + "-stdout");
        Thread stderrReader = new Thread(() -> drainStream(
                process.getErrorStream(), stderrBuilder, budget, truncated), logLabel + "-stderr");
        stdoutReader.setDaemon(true);
        stderrReader.setDaemon(true);
        stdoutReader.start();
        stderrReader.start();

        AtomicLong peakMemoryBytes = new AtomicLong(0);
        long pid = process.pid();
        Thread memoryMonitor = new Thread(() -> monitorMemory(process, pid, peakMemoryBytes),
                logLabel + "-memory");
        memoryMonitor.setDaemon(true);
        memoryMonitor.start();

        boolean timedOut = !process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        int exitCode;
        if (timedOut) {
            destroyProcessTree(process, line -> {
            }, logLabel);
            exitCode = -999;
        } else {
            exitCode = process.exitValue();
        }

        stdoutReader.join(2000);
        stderrReader.join(2000);
        memoryMonitor.join(500);

        return new SeparatedProcessExecutionResult(
                stdoutBuilder.toString(),
                stderrBuilder.toString(),
                truncated.get(),
                exitCode,
                timedOut,
                peakMemoryBytes.get(),
                System.currentTimeMillis() - started);
    }

    private void drainStream(
            InputStream stream,
            StringBuilder capture,
            AtomicInteger remainingBudget,
            AtomicBoolean truncated) {
        byte[] buffer = new byte[4096];
        try (InputStream in = stream) {
            int read;
            while ((read = in.read(buffer)) != -1) {
                int allowed = remainingBudget.get();
                if (allowed <= 0) {
                    truncated.set(true);
                    continue;
                }
                int toCopy = Math.min(read, allowed);
                capture.append(new String(buffer, 0, toCopy, StandardCharsets.UTF_8));
                remainingBudget.addAndGet(-toCopy);
                if (toCopy < read) {
                    truncated.set(true);
                }
            }
        } catch (IOException ignored) {
            // Stream closed
        }
    }

    private void monitorMemory(Process process, long pid, AtomicLong peakMemoryBytes) {
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
    }
}
