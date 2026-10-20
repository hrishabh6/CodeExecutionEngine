package xyz.hrishabhjoshi.codeexecutionengine.core.runtime;

import java.util.Locale;
import java.util.Map;

public record ExecutionRuntimeSnapshot(
        long compilationTimeoutSeconds,
        String resolvedExtraClasspath,
        Map<String, LanguageRuntimeSpec> languages) {

    public LanguageRuntimeSpec requireLanguage(String language) {
        LanguageRuntimeSpec runtime = languages.get(language.toLowerCase(Locale.ROOT));
        if (runtime == null) {
            throw new IllegalArgumentException("No runtime configuration found for language: " + language);
        }
        return runtime;
    }
}
