package xyz.hrishabhjoshi.codeexecutionengine.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@EnableConfigurationProperties({ExecutionV2Properties.class, ExecutionRoutingProperties.class})
public class ExecutionV2Configuration {
}
