package com.hrishabh.codeexecutionengine.service.factory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hrishabh.codeexecutionengine.service.execution.ExecutionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class ExecutionServiceFactory {

    private final Map<String, ExecutionService> executionServiceMap = new HashMap<>();

    @Autowired
    public ExecutionServiceFactory(List<ExecutionService> services) {
        for (ExecutionService service : services) {
            executionServiceMap.put(service.getLanguage().toLowerCase(), service);
        }
    }

    public ExecutionService getService(String language) {
        return Optional.ofNullable(executionServiceMap.get(language.toLowerCase()))
                .orElseThrow(() -> new IllegalArgumentException("No ExecutionService found for language: " + language));
    }
}

