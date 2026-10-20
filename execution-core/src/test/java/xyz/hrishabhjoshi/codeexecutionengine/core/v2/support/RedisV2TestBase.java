package xyz.hrishabhjoshi.codeexecutionengine.core.v2.support;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;
import xyz.hrishabhjoshi.codeexecutionengine.core.v2.ExecutionV2Keys;

import java.util.UUID;

public abstract class RedisV2TestBase {

    private static GenericContainer<?> redis;
    private static String redisUri;

    protected V2RedisTestClient client;
    protected ExecutionV2Keys keys;

    @BeforeAll
    static void startRedisContainer() {
        Assumptions.assumeTrue(
                DockerTestSupport.isDockerAvailable(),
                "Docker unavailable — skipping Redis v2 integration tests");
        redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                .withExposedPorts(6379);
        redis.start();
        redisUri = "redis://" + redis.getHost() + ":" + redis.getMappedPort(6379);
    }

    @AfterAll
    static void stopRedisContainer() {
        if (redis != null) {
            redis.stop();
        }
    }

    @BeforeEach
    void openClient() {
        keys = new ExecutionV2Keys("execution:v2:test:" + UUID.randomUUID() + ":");
        client = new V2RedisTestClient(redisUri, keys);
    }

    protected static String statusJson(String submissionId, String executionId, String status) {
        return "{\"submissionId\":\"" + submissionId + "\",\"executionId\":\"" + executionId + "\",\"status\":\""
                + status + "\"}";
    }
}
