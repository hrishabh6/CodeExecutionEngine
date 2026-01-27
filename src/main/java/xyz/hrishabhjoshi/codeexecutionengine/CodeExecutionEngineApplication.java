package xyz.hrishabhjoshi.codeexecutionengine;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableAsync
@EnableScheduling
@EntityScan(basePackages = "xyz.hrishabhjoshi.codeexecutionengine.model")
@EnableJpaRepositories(basePackages = "xyz.hrishabhjoshi.codeexecutionengine.repository")
public class CodeExecutionEngineApplication {

    public static void main(String[] args) {
        SpringApplication.run(CodeExecutionEngineApplication.class, args);
    }
}