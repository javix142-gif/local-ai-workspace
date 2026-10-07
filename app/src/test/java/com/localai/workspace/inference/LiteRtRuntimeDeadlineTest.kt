package com.localai.workspace.inference

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Message
import android.os.Messenger
import com.localai.workspace.domain.inference.GenerationException
import com.localai.workspace.domain.inference.GenerationStage
import com.localai.workspace.domain.inference.InferenceTimeouts
import com.localai.workspace.domain.model.ModelFormat
import com.localai.workspace.domain.model.ModelLoadConfig
import com.localai.workspace.domain.model.ModelSource
import com.localai.workspace.domain.model.GenerationEvent
import com.localai.workspace.domain.model.GenerationRequest
import com.localai.workspace.domain.model.AcceleratorType
import com.localai.workspace.domain.model.RuntimeMetrics
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowProcess
import org.robolectric.shadows.ShadowPowerManager
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit

/** A real client with an unresponsive IPC peer; Main stays paused, and no JNI/model is used. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], application = Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
class LiteRtRuntimeDeadlineTest {
    private lateinit var peer: HangingPeerContext
    private lateinit var runtime: LiteRtLmInferenceRuntime
    private val config = ModelLoadConfig(model = ModelSource("/unused/model.litertlm", "test", format = ModelFormat.LITERT_LM),
        contextSize = 1024, maxOutputTokens = 32, diagnosticMode = true)

    @Before fun setUp() { peer = HangingPeerContext(RuntimeEnvironment.getApplication()) }
    @After fun tearDown() {
        if (::runtime.isInitialized) runtime.close()
        peer.thread.quitSafely()
        peer.thread.join(3_000)
    }

    @Test fun blockedNativeLoadTimesOutAndKillsWorkerWhileMainLooperNeverRuns() = runBlocking {
        runtime = client(InferenceTimeouts(bindMs = 5_000, modelLoadMs = 600))
        val failure = withTimeout(5_000) {
            try { runtime.load(config); throw AssertionError("Expected timeout") }
            catch (error: GenerationException) { error.diagnostic }
        }
        assertEquals("MODEL_LOAD_TIMEOUT", failure.code)
        assertEquals("LITERT_ENGINE_INITIALIZE_START", failure.checkpoint)
        assertTrue(failure.elapsedMs < 3_000)
        assertTrue(failure.technicalDetail!!.contains("nativeCreateEngine"))
        assertTrue(ShadowProcess.wasKilled(HangingPeerContext.PID))
        assertEquals(GenerationStage.ERROR, runtime.observeProgress().value.stage)
        assertFalse(peer.lastWakeLockHeld())
    }

    @Test fun ignoredNativeCancellationHasItsOwnDeadlineAndReleasesWakeLease() = runBlocking {
        runtime = client(InferenceTimeouts(bindMs = 5_000, modelLoadMs = 60_000, cancelMs = 150))
        val loading = async(Dispatchers.IO) {
            try { runtime.load(config); throw AssertionError("Expected cancellation timeout") }
            catch (error: GenerationException) { error.diagnostic }
        }
        assertTrue(peer.loadStarted.await(3, TimeUnit.SECONDS))
        runtime.cancelGeneration()
        val failure = withTimeout(5_000) { loading.await() }
        assertEquals("CANCEL_TIMEOUT", failure.code)
        assertTrue(peer.commands.contains(LiteRtIpc.CANCEL))
        assertTrue(ShadowProcess.wasKilled(HangingPeerContext.PID))
        assertFalse(peer.lastWakeLockHeld())
    }

    @Test fun retryAndUnloadCompleteAfterTimeoutWithoutMainThreadOrNewRuntime() = runBlocking {
        runtime = client(InferenceTimeouts(bindMs = 5_000, modelLoadMs = 600))
        try { withTimeout(5_000) { runtime.load(config) }; fail("Expected initial timeout") }
        catch (error: GenerationException) { assertEquals("MODEL_LOAD_TIMEOUT", error.diagnostic.code) }
        peer.hang = false
        withTimeout(5_000) { runtime.load(config); runtime.unload() }
        assertEquals(2, peer.commands.count { it == LiteRtIpc.LOAD })
        assertTrue(peer.commands.contains(LiteRtIpc.UNLOAD))
        assertFalse(peer.lastWakeLockHeld())
    }

    @Test fun repetitionWithoutNativeAcknowledgmentHasFiveSecondPolicyAndResetNotCompletion() = runBlocking {
        runtime = client(InferenceTimeouts(bindMs = 5_000, cancelMs = 150))
        peer.hang = false
        peer.repetition = true
        runtime.load(config)
        val events = mutableListOf<GenerationEvent>()
        withTimeout(5_000) { runtime.generate(GenerationRequest("test")).collect { events.add(it) } }
        val failure = events.filterIsInstance<GenerationEvent.Error>().single().error
        assertEquals("REPETITION_LOOP", failure.code)
        assertEquals("LITERT_REPETITION_DETECTED", failure.checkpoint)
        assertFalse(events.contains(GenerationEvent.Completed))
        assertFalse(events.contains(GenerationEvent.Cancelled))
        assertTrue(ShadowProcess.wasKilled(HangingPeerContext.PID))
        assertEquals(false, runtime.metrics().modelRetainedAfterStop)
        assertFalse(peer.lastWakeLockHeld())
    }

    @Test fun acknowledgedLoopIsAnErrorWithRetainedEngineAndSameClientRetry() = runBlocking {
        runtime = client(InferenceTimeouts(bindMs = 5_000, cancelMs = 150))
        peer.hang = false
        peer.repetition = true
        peer.acknowledgeLoop = true
        runtime.load(config)
        val events = mutableListOf<GenerationEvent>()
        withTimeout(5_000) { runtime.generate(GenerationRequest("test")).collect { events.add(it) } }
        assertEquals("REPETITION_LOOP", events.filterIsInstance<GenerationEvent.Error>().single().error.code)
        assertFalse(events.contains(GenerationEvent.Completed))
        assertFalse(events.contains(GenerationEvent.Cancelled))
        assertEquals(true, runtime.metrics().modelRetainedAfterStop)
        assertFalse(ShadowProcess.wasKilled(HangingPeerContext.PID))
        withTimeout(5_000) { runtime.load(config) }
        assertFalse(ShadowProcess.wasKilled(HangingPeerContext.PID))
        assertFalse(peer.lastWakeLockHeld())
    }

    @Test fun acknowledgedManualStopKeepsEngineUntilExplicitUnloadAndReleasesWakeLease() = runBlocking {
        runtime = client(InferenceTimeouts(bindMs = 5_000, cancelMs = 1_000))
        peer.hang = false
        peer.retainOnCancel = true
        runtime.load(config)
        val first = CompletableDeferred<Unit>()
        val events = mutableListOf<GenerationEvent>()
        val generation = async(Dispatchers.IO) {
            runtime.generate(GenerationRequest("test")).collect {
                events.add(it)
                if (it is GenerationEvent.Token) first.complete(Unit)
            }
        }
        withTimeout(5_000) { first.await() }
        runtime.cancelGeneration()
        withTimeout(5_000) { generation.await() }
        assertTrue(events.contains(GenerationEvent.Cancelled))
        assertFalse(events.contains(GenerationEvent.Completed))
        assertEquals(true, runtime.metrics().modelRetainedAfterStop)
        assertFalse(ShadowProcess.wasKilled(HangingPeerContext.PID))
        assertFalse(peer.lastWakeLockHeld())
        withTimeout(5_000) { runtime.unload() }
        assertTrue(ShadowProcess.wasKilled(HangingPeerContext.PID))
    }

    private fun client(timeouts: InferenceTimeouts) = LiteRtLmInferenceRuntime(peer, timeouts) { System.nanoTime() / 1_000_000 }

    @Test fun experimentalGpuFailureIsVisibleAndNotRetriedUntilExplicitRequest() = runBlocking {
        peer.hang = false; peer.rejectGpu = true
        runtime = client(InferenceTimeouts(bindMs = 5_000))
        val experimental = config.copy(preferredAccelerator = AcceleratorType.GPU)
        withTimeout(5_000) { runtime.load(experimental) }
        assertEquals("GPU", runtime.metrics().backendRequested)
        assertEquals("CPU", runtime.metrics().backendEffective)
        assertNotNull(runtime.metrics().backendFallbackReason)
        withTimeout(5_000) { runtime.load(experimental) }
        assertEquals(1, peer.accelerators.count { it == "GPU" })
        withTimeout(5_000) { runtime.load(experimental.copy(retryExperimental = true)) }
        assertEquals(2, peer.accelerators.count { it == "GPU" })
        assertEquals("CPU", runtime.metrics().backendEffective)
    }

    @Test fun unsupportedSpeculativeRetainsFunctionalCpuAndExplicitFailureState() = runBlocking {
        peer.hang = false; peer.rejectSpeculative = true
        runtime = client(InferenceTimeouts(bindMs = 5_000))
        withTimeout(5_000) { runtime.load(config.copy(speculativeEnabled = true)) }
        assertEquals(true, runtime.metrics().speculativeEnabled)
        assertEquals(false, runtime.metrics().speculativeSupported)
        assertEquals(false, runtime.metrics().speculativeActive)
        assertNotNull(runtime.metrics().backendFallbackReason)
        assertEquals("CPU", runtime.metrics().backendEffective)
    }

    @Test fun failedWarmupReloadsSafelyAndDoesNotRepeatOnEveryMessage() = runBlocking {
        peer.hang = false; peer.rejectWarmup = true
        runtime = client(InferenceTimeouts(bindMs = 5_000))
        val warming = config.copy(diagnosticMode = false, warmupEnabled = true)
        withTimeout(5_000) { runtime.load(warming) }
        assertEquals("FAILED: WARMUP_FAILED", runtime.metrics().warmupStatus)
        assertEquals(42L, runtime.metrics().warmupDurationMs)
        withTimeout(5_000) { runtime.load(warming) }
        assertEquals(1, peer.warmupRequests.count { it })
        withTimeout(5_000) { runtime.load(warming.copy(retryExperimental = true)) }
        assertEquals(2, peer.warmupRequests.count { it })
    }
    @Test fun conversationResetTransmitsAnExplicitStructuralReason() = runBlocking {
        peer.hang = false
        runtime = client(InferenceTimeouts(bindMs = 5_000))
        withTimeout(5_000) { runtime.load(config); runtime.resetConversation("CHAT_CHANGED") }
        assertEquals(listOf("CHAT_CHANGED"), peer.resetReasons.toList())
    }
    @Test fun measuredNativeTtftSurvivesGenerationFailureWithoutInventingOutputTokenCount() = runBlocking {
        peer.hang = false; peer.failGenerationWithMetrics = true
        runtime = client(InferenceTimeouts(bindMs = 5_000))
        runtime.load(config)
        val events = mutableListOf<GenerationEvent>()
        withTimeout(5_000) { runtime.generate(GenerationRequest("fixture")).collect { events += it } }
        assertTrue(events.any { it is GenerationEvent.Error })
        assertEquals(42L, runtime.metrics().timeToFirstTokenMs)
        assertEquals("ERROR", runtime.metrics().finishState)
        assertNull(runtime.metrics().outputTokens)
    }

    private class HangingPeerContext(base: Context) : ContextWrapper(base) {
        companion object { const val PID = 424_242 }
        val thread = HandlerThread("test-unresponsive-native-peer").apply { start() }
        val commands = CopyOnWriteArrayList<Int>()
        val loadStarted = CountDownLatch(1)
        @Volatile var hang = true
        @Volatile var repetition = false
        @Volatile var acknowledgeLoop = false
        @Volatile var retainOnCancel = false
        @Volatile var rejectGpu = false
        @Volatile var rejectSpeculative = false
        @Volatile var rejectWarmup = false
        @Volatile var failGenerationWithMetrics = false
        val accelerators = CopyOnWriteArrayList<String>()
        val warmupRequests = CopyOnWriteArrayList<Boolean>()
        val resetReasons = CopyOnWriteArrayList<String>()
        private val endpoint = Messenger(Handler(thread.looper) { message ->
            val command = message.what
            val id = message.data.getString("id")!!
            val reply = message.replyTo
            commands.add(command)
            fun send(kind: String, extra: Bundle = Bundle()) {
                reply.send(Message.obtain(null, LiteRtIpc.EVENT).apply {
                    data = extra.apply { putString("id", id); putString("kind", kind); putInt("pid", PID) }
                })
            }
            when (command) {
                LiteRtIpc.HELLO -> send("hello")
                LiteRtIpc.LOAD -> {
                    val accelerator = message.data.getString("accelerator") ?: "CPU"
                    val speculative = message.data.getBoolean("speculative")
                    val warming = message.data.getBoolean("warmup")
                    accelerators.add(accelerator)
                    warmupRequests.add(warming)
                    send("state", Bundle().apply {
                        putString("stage", GenerationStage.LOADING_MODEL.name)
                        putString("checkpoint", "LITERT_ENGINE_INITIALIZE_START")
                        putString("event", "LITERT_ENGINE_INITIALIZE_START")
                        putString("label", "Initializing the CPU engine")
                        putString("workerStack", "test-peer.nativeCreateEngine:native")
                    })
                    loadStarted.countDown()
                    if (!hang && rejectWarmup && warming) {
                        send("error", Bundle().apply {
                            putString("stage", GenerationStage.WARMING_MODEL.name)
                            putString("code", "WARMUP_FAILED"); putLong("warmupMs", 42)
                        })
                    } else if (!hang && ((rejectGpu && accelerator == "GPU") || (rejectSpeculative && speculative))) {
                        send("error", Bundle().apply {
                            putString("stage", GenerationStage.LOADING_MODEL.name)
                            putString("code", "NATIVE_ERROR"); putString("message", "Fixture experimental initialization failed")
                        })
                    } else if (!hang) send("done", LiteRtIpc.encodeMetrics(RuntimeMetrics(
                        backendEffective = accelerator, speculativeSupported = !rejectSpeculative, speculativeActive = speculative)))
                }
                LiteRtIpc.UNLOAD -> send("done")
                LiteRtIpc.RESET_CONVERSATION -> { resetReasons.add(message.data.getString("resetReason")!!); send("done") }
                LiteRtIpc.GENERATE -> {
                    send("state", Bundle().apply { putString("stage", GenerationStage.GENERATING.name) })
                    send("token", Bundle().apply { putString("text", "fixture output"); putInt("chunks", 1) })
                    if (failGenerationWithMetrics) send("error", LiteRtIpc.encodeMetrics(RuntimeMetrics(timeToFirstTokenMs = 42,
                        outputChunks = 1, finishState = "ERROR")).apply {
                        putString("stage", GenerationStage.GENERATING.name); putString("code", "NATIVE_ERROR")
                    })
                    if (repetition) {
                        send("state", Bundle().apply {
                            putString("stage", GenerationStage.GENERATING.name)
                            putString("checkpoint", "LITERT_REPETITION_DETECTED")
                        })
                        send("repetition_detected")
                        if (acknowledgeLoop) send("error", Bundle().apply {
                            putString("stage", GenerationStage.GENERATING.name); putString("code", "REPETITION_LOOP")
                            putString("message", "Fixture native stop acknowledged"); putBoolean("engineRetained", true)
                        })
                    }
                }
                LiteRtIpc.CANCEL -> if (retainOnCancel) send("cancelled", Bundle().apply { putBoolean("engineRetained", true) })
            }
            true
        })

        override fun bindService(service: Intent, flags: Int, executor: Executor, conn: ServiceConnection): Boolean {
            executor.execute { conn.onServiceConnected(ComponentName(this, LiteRtLmService::class.java), endpoint.binder) }
            return true
        }
        override fun unbindService(conn: ServiceConnection) = Unit
        fun lastWakeLockHeld(): Boolean = ShadowPowerManager.getLatestWakeLock()?.isHeld == true
    }
}
