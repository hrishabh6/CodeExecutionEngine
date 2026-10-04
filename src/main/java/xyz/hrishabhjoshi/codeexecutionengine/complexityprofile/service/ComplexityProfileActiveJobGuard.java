package xyz.hrishabhjoshi.codeexecutionengine.complexityprofile.service;

import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.api.model.batch.v1.JobCondition;
import io.fabric8.kubernetes.api.model.batch.v1.JobStatus;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import xyz.hrishabhjoshi.codeexecutionengine.config.ComplexityProfileExecutionProperties;
import xyz.hrishabhjoshi.codeexecutionengine.config.KubernetesExecutionProperties;

@Slf4j
@Component
@RequiredArgsConstructor
public class ComplexityProfileActiveJobGuard {

    private final KubernetesClient kubernetesClient;
    private final KubernetesExecutionProperties kubernetesProperties;
    private final ComplexityProfileExecutionProperties profileProperties;

    public boolean hasCapacity() {
        int max = profileProperties.getSandbox().getMaxConcurrentJobs();
        if (max <= 0) {
            return true;
        }
        String namespace = kubernetesProperties.getNamespace();
        var jobs = kubernetesClient.batch().v1().jobs()
                .inNamespace(namespace)
                .withLabel("component", "complexity-profile-job")
                .list();
        if (jobs == null || jobs.getItems() == null) {
            return true;
        }
        long active = jobs.getItems().stream().filter(this::isActive).count();
        boolean ok = active < max;
        if (!ok) {
            log.info("[PROFILE-K8S] concurrent job cap reached active={} max={}", active, max);
        }
        return ok;
    }

    private boolean isActive(Job job) {
        if (job == null || job.getStatus() == null) {
            return true;
        }
        JobStatus status = job.getStatus();
        if (status.getSucceeded() != null && status.getSucceeded() > 0) {
            return false;
        }
        if (status.getFailed() != null && status.getFailed() > 0) {
            return false;
        }
        if (status.getConditions() != null) {
            for (JobCondition condition : status.getConditions()) {
                if ("Complete".equalsIgnoreCase(condition.getType()) && "True".equalsIgnoreCase(condition.getStatus())) {
                    return false;
                }
                if ("Failed".equalsIgnoreCase(condition.getType()) && "True".equalsIgnoreCase(condition.getStatus())) {
                    return false;
                }
            }
        }
        return true;
    }
}
