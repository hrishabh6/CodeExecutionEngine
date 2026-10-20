package xyz.hrishabhjoshi.codeexecutionengine.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import xyz.hrishabhjoshi.codeexecutionengine.core.generation.SourceWorkspaceGenerator;

@Configuration
public class ExecutionCoreConfiguration {

    @Bean
    SourceWorkspaceGenerator sourceWorkspaceGenerator() {
        return SourceWorkspaceGenerator.withDefaultRegistry();
    }
}
