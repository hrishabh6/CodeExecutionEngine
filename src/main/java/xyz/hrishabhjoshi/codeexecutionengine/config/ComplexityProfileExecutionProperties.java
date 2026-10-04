package xyz.hrishabhjoshi.codeexecutionengine.config;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "execution.complexity-profile")
public class ComplexityProfileExecutionProperties {

    private boolean enabled = false;

    private String internalServiceToken = "";

    private boolean internalAuthRequireToken = false;

    private String harnessVersion = "v1";

    private String measurementPolicyVersion = "v1";

    private Queue queue = new Queue();

    private Worker worker = new Worker();

    private Limits limits = new Limits();

    private Sandbox sandbox = new Sandbox();

    private Harness harness = new Harness();

    @Getter
    @Setter
    public static class Queue {
        private String name = "execution:complexity-profile:queue";
        private String statusPrefix = "execution:complexity-profile:status:";
        private String resultPrefix = "execution:complexity-profile:result:";
        private String jobPayloadPrefix = "execution:complexity-profile:job:";
        private long statusTtlSeconds = 3600;
        private long resultTtlSeconds = 3600;
        /** Max age for queued payloads; stale jobs are dropped without execution. */
        private long queuedJobMaxAgeSeconds = 3600;
    }

    @Getter
    @Setter
    public static class Harness {
        /** Default true: hard timeout via killable child JVM (trusted local dev). Phase 5 adds K8s sandbox. */
        private boolean useChildJvm = true;
        /** Grace beyond maxTotalProfileMs before parent destroys child process. */
        private long childJvmGraceMs = 15_000;
        /**
         * When true (in-process only), per-invocation timeout uses cooperative Future cancellation.
         * Not a hard timeout — only for unit tests with useChildJvm=false.
         */
        private boolean cooperativeInProcessTimeouts = false;
    }

    @Getter
    @Setter
    public static class Worker {
        private int count = 2;
        private long queueCapacity = 50;
        private long pollTimeoutSeconds = 5;
    }

    @Getter
    @Setter
    public static class Limits {
        private int maxCases = 32;
        private int maxWarmupsPerCase = 10;
        private int maxMeasuredRepeatsPerCase = 20;
        private int maxSourceBytes = 65536;
        private int maxCaseInputBytes = 262144;
        private int maxExpectedOutputBytes = 262144;
        private int maxSerializedOutputBytes = 262144;
        private long perInvocationTimeoutMs = 5_000;
        private long maxTotalProfileMs = 120_000;
    }

    @Getter
    @Setter
    public static class Sandbox {
        /** local-process for trusted dev; kubernetes-job for production (Phase 5). */
        private String backend = "local-process";
        private boolean productionVerified = false;
        /** local-process profiling is not a production sandbox; requires explicit dev enablement. */
        private boolean trustedDevelopmentOnly = true;
        private String jobServiceAccountName = "cxe-complexity-profile-executor";
        private long runAsUser = 65532;
        private long runAsGroup = 65532;
        private long fsGroup = 65532;
        private int maxConcurrentJobs = 4;
        private long jobActiveDeadlineSeconds = 180;
        private long jobCompletionTimeoutSeconds = 200;
        private Integer ttlSecondsAfterFinished = 300;
        private long terminationGracePeriodSeconds = 5;
        private JobResources jobResources = new JobResources();
        /** ACL-scoped prefix for sandbox Job Redis keys (payload + measurement). */
        private String sandboxRedisKeyPrefix = "execution:complexity-profile:sandbox:";
        /** Worker-only oracle storage; not granted to sandbox Redis ACL user. */
        private String trustedOracleKeyPrefix = "execution:complexity-profile:trusted:oracle:";
        private String slotKeyPrefix = "execution:complexity-profile:slot:";
        private long slotTtlGraceSeconds = 120;
        /** Dedicated ACL user for profile Job pods — must not use CXE worker Redis credentials. */
        private String sandboxRedisUsername = "";
        private String sandboxRedisPassword = "";
        /**
         * Kubernetes Secret for profile Job pod sandbox Redis credentials (username/password keys).
         * When set, Jobs use secretKeyRef instead of literal env values.
         */
        private String sandboxRedisCredentialsSecretName = "cxe-complexity-profile-sandbox-redis";
        private String sandboxRedisUsernameSecretKey = "username";
        private String sandboxRedisPasswordSecretKey = "password";
        /** When true with production-verified kubernetes-job, sandbox Redis credentials are mandatory. */
        private boolean requireSandboxRedisCredentials = true;

        @JsonIgnore
        public boolean isKubernetesJobBackend() {
            return "kubernetes-job".equalsIgnoreCase(backend == null ? "" : backend.trim());
        }
    }

    @Getter
    @Setter
    public static class JobResources {
        private String cpuRequest = "500m";
        private String memoryRequest = "512Mi";
        private String cpuLimit = "1";
        private String memoryLimit = "768Mi";
        private String ephemeralStorageRequest = "256Mi";
        private String ephemeralStorageLimit = "512Mi";
    }

    public static final String INTERNAL_SERVICE_TOKEN_HEADER = "X-Internal-Service-Token";
}
