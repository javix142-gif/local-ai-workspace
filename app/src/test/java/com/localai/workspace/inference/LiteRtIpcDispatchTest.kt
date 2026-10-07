package com.localai.workspace.inference

import android.app.Application
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.Messenger
import com.localai.workspace.domain.model.RuntimeMetrics
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit

/** Exercises the real service dispatcher with Android Message pooling, without model/JNI fixtures. */
@RunWith(org.robolectric.RobolectricTestRunner::class)
@Config(sdk = [29], application = Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
class LiteRtIpcDispatchTest {
    @Test fun effectiveConfigurationAndMediaEvidenceSurviveIpcWithoutInventingMetrics() {
        val source=RuntimeMetrics(thinkingEffective=true,automaticToolCallingActive=false,
            visionInputPresent=true,visionBackendInitialized=true,audioInputPresent=false,audioBackendInitialized=false)
        val restored=LiteRtIpc.decodeMetrics(LiteRtIpc.encodeMetrics(source))
        assertEquals(source,restored);assertNull(restored.cachedTokenCount);assertNull(restored.timeToFirstTokenMs)
    }
    @Test fun conversationResetIsDispatchedAndAcknowledgedAfterMessageRecycle() {
        dispatchAfterRecycle(LiteRtIpc.RESET_CONVERSATION, "new-chat")
        assertTrue(events.any { it.getString("event") == "LITERT_SESSION_RESET_START" })
        assertTrue(events.any { it.getString("event") == "LITERT_SESSION_RESET_OK" })
        assertTrue(events.any { it.getString("kind") == "done" && it.getString("id") == "new-chat" })
        assertFalse(events.any { it.getString("kind") == "error" })
    }
    private lateinit var service: LiteRtLmService
    private lateinit var worker: ExecutorService
    private val events = mutableListOf<Bundle>()
    private val releases = mutableListOf<CountDownLatch>()

    @Before fun setUp() {
        service = Robolectric.buildService(LiteRtLmService::class.java).create().get()
        worker = executor("nativeWorker")
    }

    @After fun tearDown() {
        releases.forEach { it.countDown() }
        if (!::worker.isInitialized) return
        worker.shutdownNow()
        executor("cancelWorker").shutdownNow()
        executor("diagnosticWorker").shutdownNow()
        assertTrue(worker.awaitTermination(5, TimeUnit.SECONDS))
    }

    @Test fun loadReachesFileValidationEvenIfMessageIsRecycledBeforeWorkerRuns() {
        val root = File(service.filesDir, "models").apply { mkdirs() }
        val missing = File(root, "absent-test-model.litertlm")
        assertFalse(missing.exists())
        dispatchAfterRecycle(LiteRtIpc.LOAD, "load", Bundle().apply {
            putString("path", missing.path)
            putString("name", "test model")
        })

        // The old code failed here with "Unknown LiteRT operation" before VERIFY_START.
        assertTrue(events.any { it.getString("event") == "LITERT_VERIFY_START" })
        val failure = events.single { it.getString("kind") == "error" }
        assertEquals("load", failure.getString("id"))
        assertEquals("MODEL_OR_CONFIG_INVALID", failure.getString("code"))
        assertTrue(failure.getString("detail")!!.contains("Model file is not readable"))

        // A recoverable validation failure must release the active slot for a new command.
        events.clear()
        dispatchAfterRecycle(LiteRtIpc.UNLOAD, "retry")
        assertEquals("retry", events.single { it.getString("kind") == "done" }.getString("id"))
    }

    @Test fun queuedUnloadCompletesAfterHandlerRecyclesItsMessage() {
        dispatchAfterRecycle(LiteRtIpc.UNLOAD, "unload")
        assertTrue(events.any { it.getString("event") == "LITERT_UNLOAD_START" })
        assertTrue(events.any { it.getString("event") == "LITERT_UNLOAD" })
        assertFalse(events.any { it.getString("kind") == "error" })
        assertEquals("unload", events.single { it.getString("kind") == "done" }.getString("id"))
    }

    @Test fun snapshotsKeepEveryCommandReplyAndScalarPayloadAfterRecycleAndReuse() {
        val reply = replies()
        for (command in listOf(LiteRtIpc.HELLO, LiteRtIpc.LOAD, LiteRtIpc.GENERATE, LiteRtIpc.CANCEL, LiteRtIpc.UNLOAD, LiteRtIpc.RESET_CONVERSATION)) {
            val payload = Bundle().apply { putString("id", "request-$command"); putInt("context", 1024) }
            val message = Message.obtain(null, command).apply { data = payload; replyTo = reply }
            val request = LiteRtIpc.snapshot(message)!!
            message.recycle()
            assertEquals(0, message.what)
            assertNull(message.replyTo)
            payload.putInt("context", 8192)
            val reused = Message.obtain(null, 999).apply { data = Bundle() }
            try {
                assertEquals(command, request.command)
                assertEquals("request-$command", request.id)
                assertEquals(reply.binder, request.reply.binder)
                assertEquals(1024, request.data.getInt("context"))
            } finally { reused.recycle() }
        }
    }

    @Test fun invalidCommandIsReportedAsProtocolErrorBeforeAnyNativeTask() {
        dispatchAfterRecycle(999, "invalid")
        val failure = events.single { it.getString("kind") == "error" }
        assertEquals("IPC_UNKNOWN_COMMAND", failure.getString("code"))
        assertEquals("IDLE", failure.getString("stage"))
        assertFalse(events.any { it.getString("event") == "LITERT_VERIFY_START" })
        events.clear()
        dispatchAfterRecycle(LiteRtIpc.UNLOAD, "after-invalid")
        assertTrue(events.any { it.getString("kind") == "done" })
    }

    @Test fun warmTurnMetricsDoNotReportPreviousColdLoadOrInventUnsupportedValues() {
        val warm = RuntimeMetrics(engineReused = true, modelPreparationDurationMs = 95,
            sessionCreationDurationMs = 70, backend = LiteRtLmInferenceRuntime.RUNTIME_ID)
        val wire = LiteRtIpc.encodeMetrics(warm)
        assertFalse(wire.containsKey("loadMs"))
        assertEquals(warm, LiteRtIpc.decodeMetrics(wire))
        val cold = warm.copy(engineReused = false, modelLoadDurationMs = 49_516, modelPreparationDurationMs = 55_586)
        assertEquals(cold, LiteRtIpc.decodeMetrics(LiteRtIpc.encodeMetrics(cold)))
        val legacy = LiteRtIpc.decodeMetrics(Bundle().apply { putLong("loadMs", 49_516) })
        assertEquals(49_516L, legacy.modelLoadDurationMs)
        assertNull(legacy.engineReused)
        assertNull(legacy.modelPreparationDurationMs)
    }

    @Test fun reusedSessionAndNativeCompletionAreDistinctFromUnsupportedEos() {
        val measured = RuntimeMetrics(engineReused = true, sessionReused = true, promptTokens = 14,
            outputTokens = 128, nativeCompletionObserved = true, outputLimitReached = true)
        val wire = LiteRtIpc.encodeMetrics(measured)
        assertFalse(wire.containsKey("eos"))
        assertFalse(wire.containsKey("sessionMs"))
        assertEquals(measured, LiteRtIpc.decodeMetrics(wire))
        assertNull(LiteRtIpc.decodeMetrics(Bundle()).nativeCompletionObserved)
        assertNull(LiteRtIpc.decodeMetrics(Bundle()).outputLimitReached)
    }

    @Test fun queuedStructuredHistoryListsAreSnapshotsEvenIfSenderMutatesItsBundle() {
        val roles = arrayListOf("USER", "ASSISTANT")
        val texts = arrayListOf("hola", "Hola!")
        val payload = Bundle().apply {
            putString("id", "structured"); putStringArrayList("historyRoles", roles); putStringArrayList("historyContents", texts)
        }
        val message = Message.obtain(null, LiteRtIpc.LOAD).apply { data = payload; replyTo = replies() }
        val request = LiteRtIpc.snapshot(message)!!
        message.recycle()
        roles[0] = "SYSTEM"
        texts[0] = "changed"
        assertEquals(listOf("USER", "ASSISTANT"), request.data.getStringArrayList("historyRoles"))
        assertEquals(listOf("hola", "Hola!"), request.data.getStringArrayList("historyContents"))
    }

    private fun dispatchAfterRecycle(command: Int, id: String, payload: Bundle = Bundle()) {
        val blocked = CountDownLatch(1)
        val release = CountDownLatch(1).also { releases.add(it) }
        worker.execute { blocked.countDown(); release.await() }
        assertTrue(blocked.await(5, TimeUnit.SECONDS))
        payload.putString("id", id)
        val message = Message.obtain(null, command).apply { data = payload; replyTo = replies() }
        try {
            // Invoke the real Handler callback, then reproduce Looper.recycleUnchecked()
            // before allowing the queued native task to start. No timing/race assumptions.
            LiteRtLmService::class.java.getDeclaredMethod("handle", Message::class.java).apply {
                isAccessible = true
            }.invoke(service, message)
            message.recycle()
            assertEquals(0, message.what)
            assertNull(message.replyTo)
        } finally { release.countDown() }
        worker.submit {}.get(5, TimeUnit.SECONDS)
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun replies() = Messenger(Handler(Looper.getMainLooper()) { message ->
        events.add(Bundle(message.data))
        true
    })

    private fun executor(field: String) = LiteRtLmService::class.java.getDeclaredField(field).run {
        isAccessible = true
        get(service) as ExecutorService
    }
}
