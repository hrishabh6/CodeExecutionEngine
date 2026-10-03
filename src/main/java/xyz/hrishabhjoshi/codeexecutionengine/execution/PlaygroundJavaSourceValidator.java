package xyz.hrishabhjoshi.codeexecutionengine.execution;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Component
public class PlaygroundJavaSourceValidator {

    private static final Pattern PACKAGE_DECL = Pattern.compile("(?m)^\\s*package\\s+\\w");
    private static final Pattern PUBLIC_MAIN_CLASS = Pattern.compile("\\bpublic\\s+class\\s+Main\\b");

    public void validate(String source) {
        if (source == null || source.isBlank()) {
            throw new ExecutionRequestRejectedException("INVALID_SOURCE", "Java source is required");
        }
        if (PACKAGE_DECL.matcher(source).find()) {
            throw new ExecutionRequestRejectedException(
                    "INVALID_SOURCE",
                    "Package declarations are not supported in Playground Java V1");
        }
        if (!PUBLIC_MAIN_CLASS.matcher(source).find()) {
            throw new ExecutionRequestRejectedException(
                    "INVALID_SOURCE",
                    "Java Playground requires public class Main with public static void main");
        }
    }
}
