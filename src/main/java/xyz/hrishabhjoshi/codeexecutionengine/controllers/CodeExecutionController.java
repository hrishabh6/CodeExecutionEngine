package xyz.hrishabhjoshi.codeexecutionengine.controllers;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import xyz.hrishabhjoshi.codeexecutionengine.CodeExecutionManager;
import xyz.hrishabhjoshi.codeexecutionengine.dto.CodeExecutionResultDTO;
import xyz.hrishabhjoshi.codeexecutionengine.dto.CodeSubmissionDTO;

@RestController
@RequestMapping("/api/v1/code-execution")
public class CodeExecutionController {

    @Autowired
    private CodeExecutionManager codeExecutionManager;

    @PostMapping("/run")
    public ResponseEntity<CodeExecutionResultDTO> runCode(@RequestBody CodeSubmissionDTO submissionDto) {
        // Create a lambda function for logging that prints to the console.
        // In a production environment, you would use a proper logging framework.
        CodeExecutionResultDTO result = codeExecutionManager.runCodeWithTestcases(submissionDto, System.out::println);
        return ResponseEntity.ok(result);
    }
}