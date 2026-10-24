package xyz.hrishabhjoshi.codeexecutionengine.config;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Classpath for child {@code javac}/{@code java} processes. Those processes do
 * not see jars nested inside the Spring Boot executable archive.
 */
public final class RunnerClasspath {

    public static final String ENV_NAME = "EXECUTION_EXTRA_CLASSPATH";
    public static final String IMAGE_LIBS_GLOB = "/app/libs/*";

    private static final List<String> REQUIRED_JAR_PREFIXES = List.of(
            "jackson-annotations-",
            "jackson-core-",
            "jackson-databind-");

    private RunnerClasspath() {
    }

    public static String resolve(String configuredExtraClasspath) {
        return resolve(configuredExtraClasspath, IMAGE_LIBS_GLOB, System.getProperty("java.class.path", ""));
    }

    static String resolve(String configuredExtraClasspath, String imageLibsGlob, String systemClasspath) {
        String configured = expand(configuredExtraClasspath);
        if (containsRequiredJars(configured)) {
            return jacksonEntries(configured);
        }

        String imageLibs = expand(imageLibsGlob);
        if (containsRequiredJars(imageLibs)) {
            return jacksonEntries(imageLibs);
        }

        String jvmJackson = jacksonEntries(systemClasspath);
        if (containsRequiredJars(jvmJackson)) {
            return jvmJackson;
        }

        throw new IllegalStateException(
                "Runner dependencies are missing from the javac/java classpath. "
                        + "Required image jars under /app/libs: jackson-annotations, jackson-core, jackson-databind. "
                        + "This is an infrastructure failure, not a user compilation error.");
    }

    public static String expand(String classpath) {
        if (classpath == null || classpath.isBlank()) {
            return "";
        }
        List<String> entries = new ArrayList<>();
        for (String token : classpath.split(java.util.regex.Pattern.quote(File.pathSeparator))) {
            String trimmed = token.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (isWildcard(trimmed)) {
                entries.addAll(jarFiles(wildcardDirectory(trimmed)));
            } else {
                entries.add(trimmed);
            }
        }
        return String.join(File.pathSeparator, entries);
    }

    public static boolean containsRequiredJars(String classpath) {
        if (classpath == null || classpath.isBlank()) {
            return false;
        }
        for (String prefix : REQUIRED_JAR_PREFIXES) {
            boolean found = false;
            for (String entry : classpath.split(java.util.regex.Pattern.quote(File.pathSeparator))) {
                String name = Path.of(entry).getFileName().toString();
                if (name.startsWith(prefix) && name.endsWith(".jar")) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                return false;
            }
        }
        return true;
    }

    private static boolean isWildcard(String token) {
        return "*".equals(token) || token.endsWith("/*") || token.endsWith(File.separator + "*");
    }

    private static Path wildcardDirectory(String token) {
        if ("*".equals(token)) {
            return Path.of(".");
        }
        return Path.of(token.substring(0, token.length() - 2));
    }

    private static List<String> jarFiles(Path directory) {
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        try (Stream<Path> listing = Files.list(directory)) {
            return listing
                    .filter(path -> Files.isRegularFile(path) && path.getFileName().toString().endsWith(".jar"))
                    .map(path -> path.toAbsolutePath().toString())
                    .sorted()
                    .toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    private static String jacksonEntries(String classpath) {
        List<String> matches = new ArrayList<>();
        if (classpath == null || classpath.isBlank()) {
            return "";
        }
        for (String entry : classpath.split(java.util.regex.Pattern.quote(File.pathSeparator))) {
            String name = Path.of(entry).getFileName().toString();
            if (name.startsWith("jackson-") && name.endsWith(".jar")) {
                matches.add(entry);
            }
        }
        return String.join(File.pathSeparator, matches);
    }
}
