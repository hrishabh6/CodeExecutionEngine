package xyz.hrishabhjoshi.codeexecutionengine.complexityprofile;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComplexityProfileSandboxManifestTest {

    @Test
    void baseManifestDefinesSaNetworkPolicyAndProfileSelector() throws Exception {
        Path repoRoot = Path.of(System.getProperty("user.dir")).getParent();
        String yaml = Files.readString(repoRoot.resolve("k8s/base/cxe-complexity-profile-sandbox.yaml"));
        assertTrue(yaml.contains("cxe-complexity-profile-executor"));
        assertTrue(yaml.contains("automountServiceAccountToken: false"));
        assertTrue(yaml.contains("component: complexity-profile-job"));
        assertTrue(yaml.contains("port: 6379"));
        assertTrue(yaml.contains("port: 53"));
        assertTrue(yaml.contains("kube-dns") || yaml.contains("k8s-app"));
        assertFalse(yaml.contains("hostPath"));
        assertFalse(yaml.contains("privileged"));
    }

    @Test
    void redisAclConfigMapHasPolicyOnlyNoCommittedPassword() throws Exception {
        Path repoRoot = Path.of(System.getProperty("user.dir")).getParent();
        String yaml = Files.readString(repoRoot.resolve("k8s/base/cxe-complexity-profile-redis-acl.yaml"));
        assertTrue(yaml.contains("cxe-profile-sandbox"));
        assertTrue(yaml.contains("~execution:complexity-profile:sandbox:"));
        assertFalse(yaml.contains("user cxe-profile-sandbox on >"), "ACL user line must not embed a password hash");
    }

    @Test
    void playgroundManifestUnchangedStillPresent() throws Exception {
        Path repoRoot = Path.of(System.getProperty("user.dir")).getParent();
        String yaml = Files.readString(repoRoot.resolve("k8s/base/cxe-playground-sandbox.yaml"));
        assertTrue(yaml.contains("cxe-playground-executor"));
        assertTrue(yaml.contains("component: playground-execution-job"));
    }
}
