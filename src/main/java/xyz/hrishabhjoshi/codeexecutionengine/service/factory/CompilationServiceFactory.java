package xyz.hrishabhjoshi.codeexecutionengine.service.factory;

import xyz.hrishabhjoshi.codeexecutionengine.service.compilation.CompilationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class CompilationServiceFactory {

    private final Map<String, CompilationService> compilationServiceMap = new HashMap<>();

    @Autowired
    public CompilationServiceFactory(List<CompilationService> services) {
        for (CompilationService service : services) {
            compilationServiceMap.put(service.getLanguage().toLowerCase(), service);
        }
    }

    public CompilationService getService(String language) {
        return Optional.ofNullable(compilationServiceMap.get(language.toLowerCase()))
                .orElseThrow(() -> new IllegalArgumentException("No CompilationService found for language: " + language));
    }
}

