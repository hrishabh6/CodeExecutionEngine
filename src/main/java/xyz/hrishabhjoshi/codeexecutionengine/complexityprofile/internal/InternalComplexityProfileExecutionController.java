package xyz.hrishabhjoshi.codeexecutionengine.complexityprofile.internal;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import xyz.hrishabhjoshi.codeexecutionengine.complexityprofile.dto.ComplexityProfileExecutionDtos.ComplexityProfilePollResponse;
import xyz.hrishabhjoshi.codeexecutionengine.complexityprofile.dto.ComplexityProfileExecutionDtos.ComplexityProfileSubmitRequest;
import xyz.hrishabhjoshi.codeexecutionengine.complexityprofile.dto.ComplexityProfileExecutionDtos.ComplexityProfileSubmitResponse;
import xyz.hrishabhjoshi.codeexecutionengine.complexityprofile.service.ComplexityProfileOrchestrationService;
import xyz.hrishabhjoshi.codeexecutionengine.config.ComplexityProfileExecutionProperties;

@RestController
@RequestMapping("/api/v1/internal/execution/complexity-profiles")
@RequiredArgsConstructor
public class InternalComplexityProfileExecutionController {

    private final ComplexityProfileOrchestrationService orchestrationService;
    private final InternalServiceAuth internalServiceAuth;
    private final ComplexityProfileExecutionProperties properties;

    @PostMapping
    public ResponseEntity<ComplexityProfileSubmitResponse> submit(
            @Valid @RequestBody ComplexityProfileSubmitRequest body,
            HttpServletRequest request) {
        if (!properties.isEnabled()) {
            return ResponseEntity.notFound().build();
        }
        internalServiceAuth.requireInternal(request);
        return ResponseEntity.accepted().body(orchestrationService.submit(body));
    }

    @GetMapping("/{executionId}")
    public ResponseEntity<ComplexityProfilePollResponse> poll(
            @PathVariable String executionId,
            HttpServletRequest request) {
        if (!properties.isEnabled()) {
            return ResponseEntity.notFound().build();
        }
        internalServiceAuth.requireInternal(request);
        return orchestrationService.poll(executionId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
