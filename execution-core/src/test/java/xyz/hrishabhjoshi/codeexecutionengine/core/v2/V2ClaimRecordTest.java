package xyz.hrishabhjoshi.codeexecutionengine.core.v2;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class V2ClaimRecordTest {

    @Test
    void roundTripEncoding() {
        V2ClaimRecord original = new V2ClaimRecord("runner-1", "token-abc", 2L, 1_700_000_000L);
        V2ClaimRecord decoded = V2ClaimRecord.decode(original.encode());
        assertEquals(original, decoded);
    }
}
