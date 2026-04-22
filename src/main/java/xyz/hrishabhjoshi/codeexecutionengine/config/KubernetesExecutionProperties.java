package xyz.hrishabhjoshi.codeexecutionengine.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "execution.kubernetes")
public class KubernetesExecutionProperties {

    private String namespace = "default";
    private String jobImage;
    private String imagePullPolicy = "IfNotPresent";
    private String serviceAccountName;
    private long activeDeadlineSeconds = 30;
    private long jobCompletionTimeoutSeconds = 40;
    private long resultPollIntervalMillis = 1000;
    private int maxAttempts = 2;
    private long retryDelayMillis = 1000;
    private int maxPayloadBytes = 65536;
    private Integer ttlSecondsAfterFinished = 120;
    private boolean deleteJobAfterRead = true;
    private Resources resources = new Resources();

    @Getter
    @Setter
    public static class Resources {
        private ResourceValues requests = new ResourceValues();
        private ResourceValues limits = new ResourceValues();
    }

    @Getter
    @Setter
    public static class ResourceValues {
        private String cpu;
        private String memory;
    }
}
