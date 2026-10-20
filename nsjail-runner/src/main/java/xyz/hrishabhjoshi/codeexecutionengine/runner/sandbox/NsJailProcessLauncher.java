package xyz.hrishabhjoshi.codeexecutionengine.runner.sandbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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

    public SandboxStageResult runStage(
            Path workspace, List<String> innerCommand, long timeLimitSeconds, String extraClasspath)
            throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add(nsjailBinary);
        command.add("--quiet");
        command.add("--mode");
        command.add("o");
        command.add("--cwd");
        command.add("/workspace");
        command.add("--time_limit");
        command.add(String.valueOf(timeLimitSeconds));
        command.add("--rlimit_as");
        command.add("2048");
        command.add("--rlimit_nproc");
        command.add("128");
        command.add("--disable_clone_newcgroup");
        command.add("--disable_proc");
        appendHostToolingMounts(command);
        appendClasspathMounts(command, extraClasspath);
        command.add("--bindmount");
        command.add(workspace.toString() + ":/workspace");
        command.add("--");
        command.add("/bin/bash");
        command.add("-lc");
        command.add(wrapWithJavaLibraryPath(String.join(" ", innerCommand)));

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

    private static void appendHostToolingMounts(List<String> command) {
        bindReadOnlyIfPresent(command, Path.of("/usr"));
        bindReadOnlyIfPresent(command, Path.of("/lib"));
        bindReadOnlyIfPresent(command, Path.of("/lib64"));
        bindReadOnlyIfPresent(command, Path.of("/bin"));
        bindReadOnlyIfPresent(command, Path.of("/etc/java-21-openjdk"));
        bindReadOnlyIfPresent(command, Path.of("/etc/ssl"));
        bindReadOnlyIfPresent(command, resolveJavaHome());
    }

    private static Path resolveJavaHome() {
        String javaHome = System.getenv("JAVA_HOME");
        if (javaHome == null || javaHome.isBlank()) {
            javaHome = System.getProperty("java.home");
        }
        if (javaHome == null || javaHome.isBlank()) {
            return null;
        }
        Path home = Path.of(javaHome);
        if (home.getFileName() != null && "jre".equals(home.getFileName().toString())) {
            home = home.getParent();
        }
        return home;
    }

    private static String wrapWithJavaLibraryPath(String inner) {
        Path javaHome = resolveJavaHome();
        if (javaHome == null) {
            return inner;
        }
        String libPath = javaHome.resolve("lib").toString() + ":" + javaHome.resolve("lib/server");
        return "export JAVA_HOME=" + shellQuote(javaHome.toString())
                + " LD_LIBRARY_PATH=" + shellQuote(libPath + ":${LD_LIBRARY_PATH:-}")
                + "; " + inner;
    }

    private static String shellQuote(String value) {
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }

    public SandboxStageResult runStage(Path workspace, List<String> innerCommand, long timeLimitSeconds)
            throws IOException, InterruptedException {
        return runStage(workspace, innerCommand, timeLimitSeconds, "");
    }

    private static void appendClasspathMounts(List<String> command, String extraClasspath) {
        if (extraClasspath == null || extraClasspath.isBlank()) {
            return;
        }
        for (String entry : extraClasspath.split(java.io.File.pathSeparator)) {
            if (entry.isBlank()) {
                continue;
            }
            Path path = Path.of(entry);
            if (Files.isRegularFile(path)) {
                command.add("--bindmount_ro");
                command.add(path.toAbsolutePath() + ":" + path.toAbsolutePath());
            }
        }
    }

    private static void bindReadOnlyIfPresent(List<String> command, Path hostPath) {
        if (hostPath != null && Files.isDirectory(hostPath)) {
            command.add("--bindmount_ro");
            command.add(hostPath.toString() + ":" + hostPath.toString());
        }
    }

    public record SandboxStageResult(int exitCode, String output, boolean timedOut, boolean truncated) {
    }
}
