package com.hrishabh.codeexecutionengine.controller;


import com.hrishabh.codeexecutionengine.CodeExecutionManager;
import com.hrishabh.codeexecutionengine.dto.CodeExecutionResultDTO;
import com.hrishabh.codeexecutionengine.dto.CodeSubmissionDTO;
import com.hrishabh.codeexecutionengine.dto.Status;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.function.Consumer;

@RestController
@RequestMapping("/api/v1/code-execution")
public class CodeExecutionController {

    @Autowired
    private CodeExecutionManager codeExecutionManager;

    @PostMapping("/run")
    public ResponseEntity<CodeExecutionResultDTO> runCode(@RequestBody CodeSubmissionDTO submissionDto) {
        // We need a way to capture logs from the manager and return them.
        // For this temporary setup, we'll just log them to the console.
        // A more advanced setup would stream logs back to the client.
        Consumer<String> consoleLogConsumer = System.out::println;

        try {
            CodeExecutionResultDTO result = codeExecutionManager.runCodeWithTestcases(submissionDto, consoleLogConsumer);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            consoleLogConsumer.accept("API Error: " + e.getMessage());
            return ResponseEntity.internalServerError().body(CodeExecutionResultDTO.builder()
                    .overallStatus(Status.INTERNAL_ERROR)
                    .compilationOutput("Error in execution engine API: " + e.getMessage())
                    .build());
        }
    }
}
