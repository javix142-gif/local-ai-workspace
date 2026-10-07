package com.localai.workspace.performance

import com.localai.workspace.domain.model.*
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class PerformancePolicyTest {
    private val config = BenchmarkConfiguration("CPU", 4, 4096, 256, .3f, 40, .95f, 1f, 0, false, false)
    private fun record(metrics: RuntimeMetrics = RuntimeMetrics(backendEffective = "CPU", speculativeActive = false),
        result: BenchmarkResult = BenchmarkResult.SUCCESS) = BenchmarkRecord(id = "id", runId = "run", startedAtEpochMs = 1,
        modelId = "gemma", modelFile = "gemma.litertlm", modelSha256 = "a".repeat(64), device = "test", androidApi = 29,
        iteration = 1, mode = BenchmarkMode.WARM, testId = "trivial", configuration = config, metrics = metrics, result = result)
    @Test fun warmupIsRealOneAttemptPerLoadedEngine() {
        val state = EngineWarmupState()
        assertFalse(state.shouldRun(true)); state.loaded()
        assertFalse(state.shouldRun(false)); assertTrue(state.shouldRun(true))
        state.start(); assertEquals(WarmupState.WARMING, state.state)
        state.finish(WarmupState.WARMED, 12)
        assertFalse(state.shouldRun(true)); assertEquals(12L, state.durationMs)
        state.loaded(); assertTrue(state.shouldRun(true)); assertNull(state.durationMs)
    }
    @Test fun warmupFailureAndTimeoutDoNotAutomaticallyRetryEveryMessage() {
        for (outcome in listOf(WarmupState.FAILED, WarmupState.TIMEOUT, WarmupState.CANCELLED)) {
            val state = EngineWarmupState(); state.loaded(); state.start(); state.finish(outcome, 10)
            assertFalse(state.shouldRun(true)); assertEquals(outcome, state.state)
        }
    }
    @Test fun gpuFailureFallsBackAndRequiresExplicitRetry() {
        val policy = ExperimentalBackendPolicy()
        assertEquals(AcceleratorType.GPU, policy.backend(AcceleratorType.GPU, "profile"))
        policy.recordFailure("profile", "GPU_INIT_FAILED")
        assertEquals(AcceleratorType.CPU, policy.backend(AcceleratorType.GPU, "profile"))
        assertEquals("GPU_INIT_FAILED", policy.recordedFailure("profile"))
        assertEquals(AcceleratorType.GPU, policy.backend(AcceleratorType.GPU, "other"))
        policy.retry("profile"); assertEquals(AcceleratorType.GPU, policy.backend(AcceleratorType.GPU, "profile"))
    }
    @Test fun speculativeSupportDoesNotMeanEnabledOrActive() {
        assertEquals("SUPPORTED / OFF", SpeculativeCapabilityState(true, true, false, false).label)
        assertEquals("UNSUPPORTED", SpeculativeCapabilityState(false, true, true, false).label)
        assertEquals("NOT TESTED", SpeculativeCapabilityState(null, true, false, null).label)
        assertTrue(SpeculativeCapabilityState(true, true, true, true).label.startsWith("ACTIVE"))
    }
    @Test fun matrixSeparatesNotTestedUnsupportedAndFailedFallback() {
        assertEquals(MatrixStatus.NOT_TESTED, BenchmarkComparison.status(emptyList(), "CPU", false, BenchmarkMode.COLD))
        assertEquals(MatrixStatus.SUPPORTED, BenchmarkComparison.status(listOf(record()), "CPU", false, BenchmarkMode.WARM))
        assertEquals(MatrixStatus.FAILED, BenchmarkComparison.status(listOf(record(result = BenchmarkResult.ERROR)), "CPU", false, BenchmarkMode.WARM))
        val fallback = record(RuntimeMetrics(backendEffective = "CPU", backendFallbackReason = "GPU_FAILED"))
            .copy(configuration = config.copy(backendRequested = "GPU"))
        assertEquals(MatrixStatus.FAILED, BenchmarkComparison.status(listOf(fallback), "GPU", false, BenchmarkMode.WARM))
        val retry = fallback.copy(id = "retry", runId = "retry-run", startedAtEpochMs = 2,
            metrics = RuntimeMetrics(backendEffective = "GPU", speculativeActive = false))
        assertEquals(MatrixStatus.SUPPORTED, BenchmarkComparison.status(listOf(fallback, retry), "GPU", false, BenchmarkMode.WARM))
        val unsupported = fallback.copy(configuration = config.copy(speculativeEnabled = true), metrics = RuntimeMetrics(speculativeSupported = false))
        assertEquals(MatrixStatus.UNSUPPORTED, BenchmarkComparison.status(listOf(unsupported), "CPU", true, BenchmarkMode.WARM))
    }
    @Test fun missingMetricsAreNullAndMedianNeverInventsSamples() {
        assertNull(BenchmarkComparison.median(listOf(null, null)))
        assertEquals(20L, BenchmarkComparison.median(listOf(10, null, 30)))
        assertEquals(20L, BenchmarkComparison.median(listOf(10, 20, 30)))
    }
    @Test fun benchmarkSerializationIsPersistentAndContainsNoPrivateTextFields() {
        val directory = Files.createTempDirectory("benchmark").toFile()
        try {
            val file = directory.resolve("runs.json")
            val store = BenchmarkStore(file)
            val original = record(RuntimeMetrics(timeToFirstTokenMs = 10, requestTimeToFirstTokenMs = 20,
                backendEffective = "CPU", speculativeActive = false, thermalAfter = "THERMAL_STATUS_LIGHT"))
            store.append(original)
            assertEquals(original, BenchmarkStore(file).read().single())
            val json = store.export(store.read())
            assertTrue(json.contains("\"speculativeAcceptanceRate\": null"))
            assertFalse(json.contains("Hola")); assertFalse(json.contains("Private evidence"))
            assertFalse(json.contains("\"prompt\":")); assertFalse(json.contains("\"response\":"))
        } finally { directory.deleteRecursively() }
    }
    @Test fun suiteIncludesDeterministicValidationAndContinuation() {
        assertEquals(4, GemmaTextSuite.independent.size)
        assertTrue(GemmaTextSuite.independent.first { it.id == "capital" }.validator!!.invoke("Tokio"))
        assertFalse(GemmaTextSuite.independent.first { it.id == "reasoning" }.validator!!.invoke("8"))
        assertTrue(GemmaTextSuite.continuation[1].validator!!.invoke("17"))
    }
    @Test fun appTimingsNeverOverwriteNativeTtftOrInventTokenCounts() {
        val native = RuntimeMetrics(timeToFirstTokenMs = 10, promptTokens = null, decodeTokensPerSecond = null)
        val app = RequestTimings(100, gateWaitMs = 20, contextBuildMs = 30, firstStateAfterAcceptMs = 70,
            callbackToStateMs = 2).merge(native, 200)
        assertEquals(10L, app.timeToFirstTokenMs)
        assertEquals(70L, app.requestTimeToFirstTokenMs)
        assertEquals(100L, app.endToEndTotalMs)
        assertEquals(20L, app.inferenceGateWaitMs)
        assertNull(app.promptTokens); assertNull(app.decodeTokensPerSecond)
    }
}
