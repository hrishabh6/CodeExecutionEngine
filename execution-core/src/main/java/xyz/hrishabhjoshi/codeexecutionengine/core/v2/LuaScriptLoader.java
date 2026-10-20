package xyz.hrishabhjoshi.codeexecutionengine.core.v2;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public final class LuaScriptLoader {

    private LuaScriptLoader() {
    }

    public static String load(String resourcePath) {
        try (InputStream in = LuaScriptLoader.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IllegalStateException("Missing Lua resource: " + resourcePath);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load Lua resource: " + resourcePath, e);
        }
    }

    public static String enqueueScript() {
        return load("redis/v2/enqueue.lua");
    }

    public static String claimFinalizeScript() {
        return load("redis/v2/claim_finalize.lua");
    }

    public static String heartbeatScript() {
        return load("redis/v2/heartbeat.lua");
    }

    public static String completeScript() {
        return load("redis/v2/complete.lua");
    }
}
