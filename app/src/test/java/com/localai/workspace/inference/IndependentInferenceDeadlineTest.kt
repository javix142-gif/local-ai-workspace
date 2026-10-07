package com.localai.workspace.inference

import com.localai.workspace.domain.inference.GenerationError
import com.localai.workspace.domain.inference.GenerationStage
import com.localai.workspace.domain.inference.InferenceTimeouts
import com.localai.workspace.domain.inference.InferenceWatchdog
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class IndependentInferenceDeadlineTest {
    @Test fun stalledConsumerCannotPreventTheIndependentDeadlineFromFiring() {
        val watchdog = InferenceWatchdog(InferenceTimeouts(modelLoadMs = 50))
        watchdog.enter(GenerationStage.LOADING_MODEL)
        val delivered = CountDownLatch(1)
        val failure = AtomicReference<GenerationError>()
        IndependentInferenceDeadline(watchdog, { failure.set(it); delivered.countDown() }, { throw AssertionError(it) }).use {
            // The consumer does no polling and never calls expired(). The timer must act anyway.
            assertTrue(delivered.await(3, TimeUnit.SECONDS))
            assertEquals("MODEL_LOAD_TIMEOUT", failure.get().code)
        }
    }

    @Test fun deadlineCallbackExceptionsAreReportedRatherThanSilentlyStoppingTheMonitor() {
        val watchdog = InferenceWatchdog(InferenceTimeouts(modelLoadMs = 1))
        watchdog.enter(GenerationStage.LOADING_MODEL)
        val reported = CountDownLatch(1)
        val cause = IllegalStateException("test reset failure")
        val observed = AtomicReference<Throwable>()
        IndependentInferenceDeadline(watchdog, { throw cause }, { observed.set(it); reported.countDown() }).use {
            assertTrue(reported.await(3, TimeUnit.SECONDS))
            assertSame(cause, observed.get())
        }
    }
}
