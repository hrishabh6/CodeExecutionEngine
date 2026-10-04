package xyz.hrishabhjoshi.codeexecutionengine.complexityprofile.harness;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComplexityProfileMutableStaticScannerTest {

    @Test
    void allowsStaticFinalConstants() throws Exception {
        class Solution {
            static final int LIMIT = 10;
            int sum(int[] nums) {
                return LIMIT;
            }
        }
        assertFalse(ComplexityProfileMutableStaticScanner.hasUnsupportedMutableStaticState(Solution.class));
    }

    @Test
    void flagsMutableStaticFields() throws Exception {
        class Solution {
            static int hits = 0;
            int sum(int[] nums) {
                return hits++;
            }
        }
        assertTrue(ComplexityProfileMutableStaticScanner.hasUnsupportedMutableStaticState(Solution.class));
    }
}
