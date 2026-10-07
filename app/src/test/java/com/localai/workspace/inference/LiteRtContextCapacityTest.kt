package com.localai.workspace.inference

import org.junit.Assert.*
import org.junit.Test

class LiteRtContextCapacityTest {
    @Test fun realCacheCountAndRenderedBytesAllowSafeAsciiTurnThatOldMultiplierRejected() {
        assertTrue(LiteRtContextCapacity.canContinue(100, "x".repeat(1000), 256, 2048))
        assertTrue(100 + 1000 * 3 + 128 + 256 > 2048)
    }
    @Test fun unicodeIsBudgetedInUtf8BytesRatherThanUtf16Characters() {
        assertFalse(LiteRtContextCapacity.canContinue(100, "😀".repeat(400), 256, 2048))
        assertTrue(LiteRtContextCapacity.canContinue(100, "😀".repeat(300), 256, 2048))
    }
    @Test fun unavailableCountOrRenderingAlwaysRejectsReuse() {
        assertFalse(LiteRtContextCapacity.canContinue(null, "hello", 256, 4096))
        assertFalse(LiteRtContextCapacity.canContinue(-1, "hello", 256, 4096))
        assertFalse(LiteRtContextCapacity.canContinue(100, null, 256, 4096))
        assertFalse(LiteRtContextCapacity.canContinue(100, "", 256, 4096))
    }
    @Test fun exactLimitIsInclusiveAndLargeCountsCannotOverflow() {
        assertTrue(LiteRtContextCapacity.canContinue(100, "x".repeat(100), 256, 584))
        assertFalse(LiteRtContextCapacity.canContinue(100, "x".repeat(100), 256, 583))
        assertFalse(LiteRtContextCapacity.canContinue(Int.MAX_VALUE, "hello", 256, 4096))
    }
}
