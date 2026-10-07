package com.localai.workspace.inference

import com.localai.workspace.domain.inference.GenerationError
import com.localai.workspace.domain.inference.GenerationProgress
import com.localai.workspace.domain.inference.GenerationStage
import com.localai.workspace.domain.inference.InferenceTimeouts
import com.localai.workspace.domain.inference.InferenceWatchdog
import org.junit.Assert.*
import org.junit.Test

class InferenceWatchdogTest {
    @Test fun warmupDeadlineIsDistinctFromEngineInitialization() {
        var clock = 0L
        val watchdog = InferenceWatchdog(nowMs = { clock })
        watchdog.enter(GenerationStage.WARMING_MODEL)
        clock = 44_999; assertNull(watchdog.expired())
        clock = 45_000; assertEquals("WARMUP_TIMEOUT", watchdog.expired()?.code)
    }
    private val limits = InferenceTimeouts(
        modelLoadMs = 100, sessionMs = 100, promptMs = 100, firstTokenMs = 100,
        decodeIdleMs = 100, totalGenerationMs = 500, cancelMs = 50, unloadMs = 100,
    )

    @Test fun identifiesThePhaseOfEveryStalledNativeOperation() {
        val stages = mapOf(
            GenerationStage.LOADING_MODEL to "MODEL_LOAD_TIMEOUT",
            GenerationStage.CREATING_SESSION to "SESSION_CREATE_TIMEOUT",
            GenerationStage.PREPARING_PROMPT to "PROMPT_FORMAT_TIMEOUT",
            GenerationStage.PREFILLING to "FIRST_TOKEN_TIMEOUT",
            GenerationStage.GENERATING to "DECODE_TIMEOUT",
            GenerationStage.UNLOADING to "UNLOAD_TIMEOUT",
        )
        stages.forEach { (stage, code) ->
            var clock = 0L
            val watchdog = InferenceWatchdog(limits) { clock }
            watchdog.enter(stage)
            clock = 99
            assertNull(watchdog.expired())
            clock = 100
            val error = watchdog.expired()!!
            assertEquals(stage, error.stage)
            assertEquals(code, error.code)
        }
    }

    @Test fun emptyCallbacksAndRepeatedStageUpdatesDoNotHideMissingFirstToken() {
        var clock = 0L
        val watchdog = InferenceWatchdog(limits) { clock }
        watchdog.enter(GenerationStage.PREFILLING)
        clock = 80
        watchdog.enter(GenerationStage.PREFILLING)
        clock = 100
        assertEquals("FIRST_TOKEN_TIMEOUT", watchdog.expired()?.code)
    }

    @Test fun actualOutputResetsOnlyTheDecodeInactivityDeadline() {
        var clock = 0L
        val watchdog = InferenceWatchdog(limits) { clock }
        watchdog.enter(GenerationStage.PREFILLING)
        clock = 80
        watchdog.outputReceived()
        clock = 170
        assertNull(watchdog.expired())
        watchdog.outputReceived()
        clock = 269
        assertNull(watchdog.expired())
        clock = 270
        assertEquals("DECODE_TIMEOUT", watchdog.expired()?.code)
    }

    @Test fun endlessOutputStillHasATotalDeadline() {
        var clock = 0L
        val watchdog = InferenceWatchdog(limits) { clock }
        watchdog.enter(GenerationStage.PREFILLING)
        for (i in 1..5) { clock = i * 90L; watchdog.outputReceived(); assertNull(watchdog.expired()) }
        clock = 500
        assertEquals("GENERATION_TIMEOUT", watchdog.expired()?.code)
    }

    @Test fun cancellationMustFinishEvenWhenTheNativeOperationIsBlocked() {
        var clock = 0L
        val watchdog = InferenceWatchdog(limits) { clock }
        watchdog.enter(GenerationStage.LOADING_MODEL)
        clock = 20
        watchdog.cancelRequested()
        clock = 69
        watchdog.cancelRequested()
        assertNull(watchdog.expired())
        clock = 70
        assertEquals("CANCEL_TIMEOUT", watchdog.expired()?.code)
    }

    @Test fun completedAndIdleStatesNeverPretendToBeGenerating() {
        var clock = 0L
        val watchdog = InferenceWatchdog(limits) { clock }
        watchdog.enter(GenerationStage.COMPLETED)
        clock = 100_000
        assertNull(watchdog.expired())
        assertEquals("Ready", GenerationProgress().label)
        val failure = GenerationError(GenerationStage.PREFILLING, "FIRST_TOKEN_TIMEOUT", "No native output", 180_000)
        assertTrue(failure.displayText().contains("PREFILLING"))
        assertTrue(failure.displayText().contains("180000"))
        assertFalse(GenerationProgress(GenerationStage.ERROR, error = failure).label.startsWith("Generating"))
    }
}
