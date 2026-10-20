package xyz.hrishabhjoshi.codeexecutionengine.core.v2;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExecutionV2LuaScriptLoadTest {

    @Test
    void loadsAllV2ScriptsFromClasspath() {
        assertTrue(LuaScriptLoader.enqueueScript().contains("IDEMPOTENT"));
        assertTrue(LuaScriptLoader.claimFinalizeScript().contains("CLAIMED"));
        assertTrue(LuaScriptLoader.heartbeatScript().contains("STALE"));
        assertFalse(LuaScriptLoader.completeScript().isBlank());
    }
}
