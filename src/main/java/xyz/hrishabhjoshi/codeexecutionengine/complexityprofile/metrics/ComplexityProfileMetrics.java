package xyz.hrishabhjoshi.codeexecutionengine.complexityprofile.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class ComplexityProfileMetrics {

    private final MeterRegistry registry;

    public ComplexityProfileMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void recordProfileSubmission(String outcome) {
        registry.counter(
                "complexity_profile_submissions_total",
                "outcome", normalizeTag(outcome)).increment();
    }

    public void recordQueueFull() {
        registry.counter("complexity_profile_queue_full_total").increment();
    }

    public void recordExecutionOutcome(String status) {
        registry.counter(
                "complexity_profile_execution_outcomes_total",
                "status", normalizeTag(status)).increment();
    }

    public void recordSandboxJobOutcome(String outcome) {
        registry.counter(
                "complexity_profile_sandbox_job_outcomes_total",
                "outcome", normalizeTag(outcome)).increment();
    }

    public void recordConcurrencyCapRejection() {
        registry.counter("complexity_profile_concurrency_cap_rejections_total").increment();
    }

    private static String normalizeTag(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value.trim().toUpperCase();
    }
}
