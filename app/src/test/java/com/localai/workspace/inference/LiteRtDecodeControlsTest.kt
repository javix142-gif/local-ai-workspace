package com.localai.workspace.inference

import org.junit.Assert.*
import org.junit.Test

class LiteRtDecodeControlsTest {
    @Test fun ordinaryChatUsesActualBoundedNativeControlsWithoutStrongPresencePenalty() {
        val controls = LiteRtDecodeControls.create(1.1f, true)
        assertEquals(1.1f, controls.penalty.repetitionPenalty!!, .00001f)
        assertEquals(256, controls.penalty.windowSize)
        assertEquals(8, controls.noRepeat!!.noRepeatNgramSize)
        assertEquals(256, controls.noRepeat.windowSize)
        assertNull(controls.penalty.presencePenalty)
        assertNull(controls.penalty.frequencyPenalty)
    }

    @Test fun deliberateDisableAndThinkingDiagnosticModesDoNotForceNgramConstraints() {
        val off = LiteRtDecodeControls.create(1f, true)
        assertEquals(1.0f, off.penalty.repetitionPenalty!!, 0f)
        assertNull(off.penalty.windowSize)
        assertNull(off.noRepeat)
        assertNull(LiteRtDecodeControls.create(1.2f, false).noRepeat)
        assertEquals(1.2f, LiteRtDecodeControls.create(1.2f, true).penalty.repetitionPenalty!!, .00001f)
    }

    @Test fun invalidNonFiniteParametersFailInsteadOfReachingNativeLogits() {
        for (value in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            assertThrows(IllegalArgumentException::class.java) { LiteRtDecodeControls.create(value, true) }
        }
    }
}
