package xyz.hrishabhjoshi.codeexecutionengine.core.v2.support;

import org.testcontainers.DockerClientFactory;

public final class DockerTestSupport {

    private DockerTestSupport() {
    }

    public static boolean isDockerAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (RuntimeException | LinkageError ignored) {
            return false;
        }
    }
}
