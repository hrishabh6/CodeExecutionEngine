package xyz.hrishabhjoshi.codeexecutionengine.runner.sandbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public final class NsJailProcessLauncher {

    private static final Logger log = LoggerFactory.getLogger(NsJailProcessLauncher.class);

    private final String nsjailBinary;
    private final int maxOutputBytes;

    public NsJailProcessLauncher(String nsjailBinary, int maxOutputBytes) {
        this.nsjailBinary = nsjailBinary;
        this.maxOutputBytes = maxOutputBytes;
    }

    public SandboxStageResult runStage(Path workspace, List<String> innerCommand, long timeLimitSeconds) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add(nsjailBinary);
        command.add("--quiet");
        command.add("--mode");
        command.add("o");
        command.add("--cwd");
        command.add("/workspace");
        command.add("--time_limit");
        command.add(String.valueOf(timeLimitSeconds));
        command.add("--rlimit_nproc");
        command.add("16");
        command.add("--disable_clone_newcgroup");
        command.add("--disable_proc");
        command.add("--bindmount");
        command.add(workspace.toString() + ":/workspace");
        command.add("--");
        command.add("/bin/bash");
        command.add("-lc");
        command.add(String.join(" ", innerCommand));

        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(true);
        Process process = builder.start();
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (InputStream in = process.getInputStream()) {
            byte[] chunk = new byte[4096];
            int read;
            while ((read = in.read(chunk)) >= 0) {
                if (buffer.size() < maxOutputBytes) {
                    buffer.write(chunk, 0, Math.min(read, maxOutputBytes - buffer.size()));
                }
            }
        }
        boolean finished = process.waitFor(timeLimitSeconds + 5, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            return new SandboxStageResult(124, buffer.toString(StandardCharsets.UTF_8), true, true);
        }
        int exit = process.exitValue();
        boolean truncated = buffer.size() >= maxOutputBytes;
        boolean timedOut = exit == 124 || exit == 137;
        return new SandboxStageResult(exit, buffer.toString(StandardCharsets.UTF_8), timedOut, truncated);
    }

    public record SandboxStageResult(int exitCode, String output, boolean timedOut, boolean truncated) {
    }
}
