package com.localai.workspace.inference

import android.app.ActivityManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Debug
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message as IpcMessage
import android.os.Messenger
import android.os.Process
import android.os.StatFs
import android.os.SystemClock
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Capabilities
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ExperimentalApi
import com.google.ai.edge.litertlm.ExperimentalFlags
import com.google.ai.edge.litertlm.LogSeverity
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.MessageCallback
import com.google.ai.edge.litertlm.Role
import com.google.ai.edge.litertlm.ThinkingConfig
import com.localai.workspace.domain.inference.GenerationStage
import com.localai.workspace.domain.inference.RepetitionLoopDetector
import com.localai.workspace.domain.model.RuntimeMetrics
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.CancellationException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * Native calls run here, never in the app/UI process. Kotlin cancellation cannot interrupt
 * Engine.initialize() in JNI. The client can terminate this same-UID worker on a deadline.
 */
@OptIn(ExperimentalApi::class)
class LiteRtLmService : Service() {
    private class Operation(val id: String, val reply: Messenger) {
        val cancelled = AtomicBoolean(false)
        val startedAt = SystemClock.elapsedRealtime()
        val startedUptime = SystemClock.uptimeMillis()
        val guard = Any()
        val snapshot = Bundle()
        @Volatile var stage = GenerationStage.LOADING_MODEL
        @Volatile var checkpoint = "LITERT_IPC_DISPATCH"
        @Volatile var label: String? = null
        @Volatile var nativeGenerationSettled = false
        @Volatile var nativeErrorObserved = false
        var engineRetained = false
        fun elapsedMs() = SystemClock.elapsedRealtime() - startedAt
    }

    private val chatTools by lazy { NativeChatTools(this) }
    private var toolData = Bundle()
    private val nativeThread = AtomicReference<Thread>()
    private val nativeWorker = Executors.newSingleThreadExecutor { task -> Thread(task, "LocalAI-LiteRT-native").also { nativeThread.set(it) } }
    private val cancelWorker = Executors.newSingleThreadExecutor { task -> Thread(task, "LocalAI-LiteRT-cancel") }
    private val diagnosticWorker = Executors.newSingleThreadScheduledExecutor { task -> Thread(task, "LocalAI-LiteRT-diagnostics") }
    private val nativeResourceLock = Any()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val messenger = Messenger(Handler(Looper.getMainLooper()) { message ->
        handle(message)
        true
    })
    @Volatile private var active: Operation? = null
    private val resources = LiteRtEngineResources<Engine, Conversation>(
        nativeResourceLock, Engine::isInitialized, { it.isAlive },
    )
    private data class VerifiedModel(val formatVersion: String, val capabilities: Bundle, val metadata: LiteRtBundleMetadata)
    private var verifiedModel: VerifiedModel? = null
    private val verificationCache = LiteRtVerificationCache()
    private val conversationReuse = LiteRtConversationReuse()
    private var modelDiagnostics = Bundle()
    private var modelName: String? = null
    private var diagnosticMode = false
    private var thinkingOverride: Boolean? = null
    private var activeThinkingConfig: ThinkingConfig? = null
    private var previousThinking: Boolean? = null
    private var previousTools: Set<String>? = null
    private var ordinaryFinalText = false
    private var metrics = RuntimeMetrics(backend = LiteRtLmInferenceRuntime.RUNTIME_ID)
    private val warmup = com.localai.workspace.performance.EngineWarmupState()
    private var resetReason: String? = null

    override fun onBind(intent: Intent): IBinder = messenger.binder

    private fun handle(message: IpcMessage) {
        // Copy all fields while Handler still owns the pooled Message. Workers may run
        // only after Handler has recycled it (what=0, replyTo/data=null).
        val request = LiteRtIpc.snapshot(message) ?: return
        val command = request.command
        val reply = request.reply
        val id = request.id
        val data = request.data
        LiteRtTrace.event("LITERT_IPC_RECEIVED", active?.stage ?: GenerationStage.IDLE, modelName,
            fields = mapOf("operation" to id, "command" to command, "commandName" to LiteRtIpc.commandName(command)))
        if (command == LiteRtIpc.HELLO) {
            send(reply, id, "hello", Bundle().apply { putInt("pid", Process.myPid()) })
            return
        }
        if (command == LiteRtIpc.CANCEL) {
            val operation = active?.takeIf { it.id == id } ?: return
            operation.cancelled.set(true)
            trace(operation, "LITERT_CANCEL", mapOf("nativeRequested" to true))
            send(operation, "cancel_requested")
            cancelWorker.execute {
                synchronized(nativeResourceLock) {
                    val current = resources.conversation
                    if (current == null) {
                        // Engine creation has no cancellation API. Termination releases native handles.
                        trace(operation, "LITERT_WORKER_RESET", mapOf("reason" to "cancel_during_load"))
                        Process.killProcess(Process.myPid())
                    } else {
                        try { current.cancelProcess() }
                        catch (error: Throwable) { trace(operation, "LITERT_CANCEL_ERROR", mapOf("errorType" to error.javaClass.name)) }
                    }
                }
            }
            return
        }
        if (command !in setOf(LiteRtIpc.LOAD, LiteRtIpc.GENERATE, LiteRtIpc.UNLOAD, LiteRtIpc.RESET_CONVERSATION)) {
            LiteRtTrace.event("LITERT_IPC_ERROR", GenerationStage.IDLE, modelName,
                fields = mapOf("operation" to id, "command" to command, "code" to "IPC_UNKNOWN_COMMAND"))
            send(reply, id, "error", Bundle().apply {
                putString("stage", GenerationStage.IDLE.name)
                putString("code", "IPC_UNKNOWN_COMMAND")
                putString("message", "LiteRT service received an unsupported IPC command ($command).")
            })
            return
        }
        if (active != null) {
            send(reply, id, "error", Bundle().apply {
                putString("stage", GenerationStage.LOADING_MODEL.name)
                putString("code", "RUNTIME_BUSY")
                putString("message", "Another LiteRT native operation is still active.")
            })
            return
        }
        val operation = Operation(id, reply)
        active = operation
        val heartbeat = diagnosticWorker.scheduleWithFixedDelay({
            if (active === operation) {
                try {
                    sampleDevice(operation)
                    val stack = nativeThread.get()?.stackTrace?.take(6)?.joinToString("\n") {
                        "${it.className}.${it.methodName}:${if (it.isNativeMethod) "native" else it.lineNumber}"
                    }
                    synchronized(operation.guard) { operation.snapshot.putString("workerStack", stack) }
                    send(operation, "diagnostic")
                    trace(operation, "LITERT_LOAD_HEARTBEAT", mapOf("checkpoint" to operation.checkpoint))
                } catch (error: Throwable) {
                    trace(operation, "LITERT_DIAGNOSTIC_ERROR", mapOf("errorType" to error.javaClass.name))
                }
            }
        }, 1, 5, TimeUnit.SECONDS)
        nativeWorker.execute {
            try {
                trace(operation, "LITERT_IPC_DISPATCH", mapOf("command" to command, "commandName" to LiteRtIpc.commandName(command)))
                when (command) {
                    LiteRtIpc.LOAD -> load(operation, data)
                    LiteRtIpc.GENERATE -> generate(operation, data)
                    LiteRtIpc.RESET_CONVERSATION -> {
                        phase(operation, GenerationStage.UNLOADING, "LITERT_SESSION_RESET_START")
                        resources.closeConversation()
                        chatTools.endValidationScope()
                        conversationReuse.invalidate()
                        resetReason = data.getString("resetReason")?.takeIf { it in setOf("CHAT_CHANGED", "CONFIG_CHANGED", "SESSION_LOST", "NEW_CONVERSATION", "APPLICATION_PREPARATION") }
                        modelDiagnostics = Bundle()
                        metrics = RuntimeMetrics(backend = LiteRtLmInferenceRuntime.RUNTIME_ID)
                        phase(operation, GenerationStage.IDLE, "LITERT_SESSION_RESET_OK")
                        trace(operation, "LITERT_ENGINE_RETAINED", mapOf("initialized" to (resources.engine?.isInitialized() == true)))
                    }
                    LiteRtIpc.UNLOAD -> {
                        phase(operation, GenerationStage.UNLOADING, "LITERT_UNLOAD_START")
                        closeResources()
                        chatTools.endValidationScope()
                        phase(operation, GenerationStage.IDLE, "LITERT_UNLOAD")
                    }
                }
                if (operation.cancelled.get()) throw CancellationException()
                // Clear BEFORE terminal IPC so an immediate retry cannot race with the old command.
                active = null
                send(operation, "done", LiteRtIpc.encodeMetrics(metrics))
            } catch (error: Throwable) {
                conversationReuse.invalidate()
                val failedStage = operation.stage
                if (command == LiteRtIpc.GENERATE) {
                    val after = com.localai.workspace.performance.DeviceMeasurements.capture(this)
                    val snapshot = synchronized(operation.guard) { Bundle(operation.snapshot) }
                    metrics = metrics.copy(
                        timeToFirstTokenMs = if (snapshot.containsKey("ttftMs")) snapshot.getLong("ttftMs") else null,
                        outputChunks = if (snapshot.containsKey("outputChunks")) snapshot.getLong("outputChunks").toInt() else null,
                        workerPssAfterBytes = after.pssBytes, availableRamAfterBytes = after.availableRamBytes,
                        totalRamBytes = after.totalRamBytes, thermalAfter = after.thermal,
                        finishState = if (operation.cancelled.get() || error is CancellationException) "CANCELLED" else "ERROR")
                }
                trace(operation, "LITERT_ERROR", mapOf("errorType" to error.javaClass.name, "cancelled" to operation.cancelled.get()))
                try {
                    // Never reuse a cancelled conversation's KV state. Retain only its engine
                    // after an actual native terminal callback and successful session teardown.
                    if (operation.nativeGenerationSettled && !operation.nativeErrorObserved &&
                        (operation.cancelled.get() || error is RepetitionLoopException)) {
                        resources.closeConversation()
                        operation.engineRetained = resources.engine?.isInitialized() == true
                    } else closeResources()
                }
                catch (cleanup: Throwable) { trace(operation, "LITERT_CLEANUP_ERROR", mapOf("errorType" to cleanup.javaClass.name)) }
                active = null
                if (operation.cancelled.get() || error is CancellationException) {
                    phase(operation, GenerationStage.CANCELLED, "LITERT_CANCELLED")
                    send(operation, "cancelled", Bundle().apply {
                        if (command == LiteRtIpc.GENERATE) putAll(LiteRtIpc.encodeMetrics(metrics))
                        putBoolean("engineRetained", operation.engineRetained)
                    })
                } else {
                    send(operation, "error", diagnostic(error, failedStage, operation.elapsedMs()).apply {
                        if (command == LiteRtIpc.GENERATE) putAll(LiteRtIpc.encodeMetrics(metrics))
                        putBoolean("engineRetained", operation.engineRetained)
                    })
                }
            } finally {
                heartbeat.cancel(false)
            }
        }
    }

    private fun load(operation: Operation, data: Bundle) {
        modelName = data.getString("name")
        phase(operation, GenerationStage.LOADING_MODEL, "LITERT_VERIFY_START")
        val modelRoot = File(filesDir, "models").canonicalFile
        val file = File(requireNotNull(data.getString("path"))).canonicalFile
        require(file.path.startsWith(modelRoot.path + File.separator)) { "Model outside private storage" }
        require(file.isFile && file.canRead()) { "Model file is not readable" }
        diagnosticMode = data.getBoolean("diagnostic")
        val validatedConfig = LiteRtConversationPayload.config(data, diagnosticMode)
        val requestedHistory = if (diagnosticMode) emptyList() else LiteRtConversationPayload.history(data)
        requestedHistory.mapNotNull { it.imagePath }.forEach { LiteRtVisionInput.validate(filesDir, it) }
        require(requestedHistory.none { it.imagePath != null } || data.getBoolean("vision")) { "Visual history requires vision" }
        requestedHistory.mapNotNull { it.audioPath }.forEach { com.localai.workspace.audio.WavInput.validate(filesDir, it) }
        require(requestedHistory.none { it.audioPath != null } || data.getBoolean("audio")) { "Audio history requires audio" }
        val requestedTools = data.getStringArrayList("enabledTools").orEmpty().toSet()
        val toolsChanged = previousTools != null && previousTools != requestedTools
        previousTools = requestedTools
        val thinkingChanged = previousThinking != null && previousThinking != validatedConfig.thinkingConfig?.enableThinking
        thinkingOverride = validatedConfig.thinkingConfig?.enableThinking
        previousThinking = thinkingOverride
        ordinaryFinalText = !diagnosticMode && data.getBoolean("structuredConversation") && thinkingOverride == false
        val expectedSize = data.getLong("size").takeIf { it > 0 }
        val expectedHash = data.getString("hash")?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
        val threads = data.getInt("threads", minOf(4, Runtime.getRuntime().availableProcessors())).coerceIn(1, 8)
        val context = data.getInt("context", 1024).coerceIn(256, 8192)
        val cache = File(cacheDir, "litert-${LiteRtIpc.VERSION}").apply { mkdirs() }
        require(cache.isDirectory && cache.canWrite()) { "LiteRT cache is not writable" }
        val key = LiteRtEngineKey(
            canonicalPath = file.path, sizeBytes = file.length(), lastModifiedMs = file.lastModified(),
            expectedSizeBytes = expectedSize, sha256 = expectedHash, contextSize = context,
            threads = threads, vision = data.getBoolean("vision"), audio = data.getBoolean("audio"),
            // Smoke still forces a fresh engine; keep XNNPACK's private disk cache
            // to avoid allocating another multi-GB in-memory weight cache.
            cacheDirectory = cache.path,
            backendVersion = LiteRtIpc.VERSION, diagnosticMode = diagnosticMode,
            accelerator = data.getString("accelerator") ?: "CPU",
            speculative = data.getBoolean("speculative"),
        )
        synchronized(operation.guard) {
            operation.snapshot.putString("accelerator", key.accelerator)
            operation.snapshot.putLong("sizeBytes", file.length())
            operation.snapshot.putString("mode", if (diagnosticMode) "CPU_SMOKE" else "NORMAL_CHAT")
            operation.snapshot.putString("cacheMode", "private")
            operation.snapshot.putInt("threads", threads)
            operation.snapshot.putInt("context", context)
            operation.snapshot.putInt("maxOutput", data.getInt("maxOutput", 128))
            operation.snapshot.putString("fileHash", expectedHash)
        }
        sampleDevice(operation)
        val device = synchronized(operation.guard) { Bundle(operation.snapshot) }
        trace(operation, "LITERT_DEVICE", mapOf(
            "android" to Build.VERSION.SDK_INT, "abi" to Build.SUPPORTED_ABIS.firstOrNull(),
            "totalRam" to device.getLong("totalRam"), "availableRam" to device.getLong("availableRam"),
            "cacheStorageAvailable" to device.getLong("storageAvailable"), "backendVersion" to LiteRtIpc.VERSION,
        ))
        val reuseExpected = resources.canReuse(key)
        val cachedRole = verifiedModel?.metadata?.historyRole
        val sampler = checkNotNull(validatedConfig.samplerConfig)
        fun settings(role: Role) = LiteRtConversationReuse.Settings(
            data.getString("systemInstruction"), sampler.temperature.toFloat(), sampler.topP.toFloat(), sampler.topK,
            sampler.seed, data.getInt("maxOutput", 128).coerceIn(1, 8192), role.value,
            enabled = data.getBoolean("structuredConversation") && !diagnosticMode && thinkingOverride == false && data.getStringArrayList("enabledTools").isNullOrEmpty(),
            conversationId = data.getString("conversationId"), modelIdentity = file.path + ":" + expectedHash,
            contextSize = context,
            repeatPenalty = data.getFloat("repeatPenalty", 1f), thinking = thinkingOverride == true,
        )
        var rebuildReason = if (resources.key?.let { it.canonicalPath != key.canonicalPath || it.sha256 != key.sha256 } == true) "MODEL_CHANGED"
            else if (thinkingChanged) "THINKING_CHANGED"
            else if (toolsChanged) "CONFIG_CHANGED"
            else if (resources.key?.contextSize?.let { it != key.contextSize } == true) "CONTEXT_CHANGED"
            else if ((resources.key?.vision == false && key.vision) || (resources.key?.audio == false && key.audio)) "MODALITY_CHANGED"
            else if (!reuseExpected) "ENGINE_CHANGED"
            else if (cachedRole != null) conversationReuse.reason(settings(cachedRole), requestedHistory).let {
                if (it == "SESSION_LOST") resetReason ?: it else it
            } else "SESSION_LOST"
        var cachedTokens: Int? = null
        var continueSession = reuseExpected && cachedRole != null && resources.conversation?.isAlive == true &&
            conversationReuse.canContinue(settings(cachedRole), requestedHistory)
        if (continueSession && (data.getBoolean("nextHasImage") || data.getBoolean("nextHasAudio"))) {
            continueSession = false
            rebuildReason = "MODALITY_CHANGED"
        }
        if (continueSession) {
            // Exact history equality is necessary but keep enough real cache capacity for
            // the next message/output. This bound is conservative, not a tokenizer metric.
            val count = try { resources.conversation!!.getTokenCount() }
            catch (error: Throwable) {
                trace(operation, "LITERT_CACHE_COUNT_UNAVAILABLE", mapOf("errorType" to error.javaClass.name)); -1
            }
            cachedTokens = count.takeIf { it >= 0 }
            // No tokenizer API is exposed. Render the next turn without sending it and
            // reserve one token per UTF-8 byte plus template/BOS safety space. This is
            // an upper-bound admission estimate, NEVER a measured prompt token count.
            val next = data.getString("nextInput")
            val renderedNext = next?.let { resources.conversation!!.renderMessageIntoString(Message.user(it),
                mapOf("enable_thinking" to false)) }
            continueSession = LiteRtContextCapacity.canContinue(cachedTokens, renderedNext, data.getInt("maxOutput", 128), context)
            if (!continueSession) rebuildReason = if (count < 0) "SESSION_LOST" else "CAPACITY_EXCEEDED"
            trace(operation, "LITERT_SESSION_REUSE_CHECK", mapOf("cachedTokens" to count, "withinContext" to continueSession))
        }
        if (!continueSession) conversationReuse.invalidate()
        if (resources.engine != null && !continueSession) {
            phase(operation, if (reuseExpected) GenerationStage.CREATING_SESSION else GenerationStage.UNLOADING,
                if (reuseExpected) "LITERT_SESSION_CLOSE_START" else "LITERT_UNLOAD_START")
        }
        var loadMs: Long? = null
        var loadStart = 0L
        val reused = resources.prepareEngine(key, preserveConversation = continueSession, create = {
            verifiedModel = null
            synchronized(operation.guard) { operation.snapshot.putLong("verifiedBytes", 0) }
            phase(operation, GenerationStage.LOADING_MODEL, "LITERT_VERIFY_START")
            val formatVersion = verificationCache.verify(file, expectedSize, expectedHash,
                fingerprint = {
                    try {
                        val stat = android.system.Os.stat(file.path)
                        LiteRtVerificationCache.Fingerprint(file.path, stat.st_size, file.lastModified(),
                            stat.st_ino, stat.st_dev, stat.st_ctime)
                    } catch (_: android.system.ErrnoException) { null }
                },
                onProgress = { completed, _ -> synchronized(operation.guard) { operation.snapshot.putLong("verifiedBytes", completed) } }) {
                if (operation.cancelled.get()) throw CancellationException()
            }
            if (verificationCache.lastCheckReused) trace(operation, "LITERT_INTEGRITY_REUSE", mapOf("sizeBytes" to file.length()))
            // Do not reuse a file changed while it was being inspected.
            require(file.length() == key.sizeBytes && file.lastModified() == key.lastModifiedMs) { "Model file changed during verification" }
            synchronized(operation.guard) { operation.snapshot.putString("formatVersion", formatVersion) }
            trace(operation, "LITERT_VERIFY_OK", mapOf("sizeBytes" to file.length(), "formatVersion" to formatVersion,
                "hashVerified" to (expectedHash != null)))
            phase(operation, GenerationStage.LOADING_MODEL, "LITERT_JNI_INIT_START")
            Engine.setNativeMinLogSeverity(LogSeverity.INFINITY)
            phase(operation, GenerationStage.LOADING_MODEL, "LITERT_JNI_INIT_OK")
            phase(operation, GenerationStage.LOADING_MODEL, "LITERT_CAPABILITIES_START")
            val metadata = LiteRtBundleMetadata.read(file)
            val capabilityData = Capabilities(file.path).use { caps ->
                val modalities = caps.inputModalities()
                Bundle().apply {
                    putBoolean("text", modalities.text)
                    putBoolean("vision", modalities.vision)
                    putBoolean("audio", modalities.audio)
                    putBoolean("tools", LiteRtCapabilityResolver.resolve(metadata.toolsDeclared,caps.supportsFunctionCalling(),metadata.legacyGemmaContract).available)
                    putBoolean("thinking", LiteRtCapabilityResolver.resolve(metadata.thinkingDeclared,caps.supportsThinking(),metadata.legacyGemmaContract).available)
                    putBoolean("speculative", caps.hasSpeculativeDecodingSupport())
                }
            }
            send(operation, "capabilities", capabilityData)
            require(capabilityData.getBoolean("text")) { "Model does not declare text input" }
            require(!key.audio || capabilityData.getBoolean("audio")) { "Model does not declare audio input" }
            require(!key.vision || capabilityData.getBoolean("vision")) { "Model does not declare vision input" }
            require(thinkingOverride != true || capabilityData.getBoolean("thinking")) { "Model does not declare thinking support" }
            require(!key.speculative || capabilityData.getBoolean("speculative")) { "SPECULATIVE_UNSUPPORTED_BY_BUNDLE" }
            verifiedModel = VerifiedModel(formatVersion, Bundle(capabilityData), metadata)
            trace(operation, "LITERT_HISTORY_ROLE", mapOf("processor" to metadata.modelProcessor,
                "historyRole" to metadata.historyRole?.value, "source" to "embedded_metadata"))
            phase(operation, GenerationStage.LOADING_MODEL, "LITERT_CAPABILITIES_OK")
            // Explicit flags: benchmark is otherwise false and getBenchmarkInfo() would throw.
            ExperimentalFlags.enableBenchmark = true
            ExperimentalFlags.enableSpeculativeDecoding = key.speculative
            ExperimentalFlags.overwritePromptTemplate = null
            phase(operation, GenerationStage.LOADING_MODEL, "LITERT_MODEL_OPEN_START")
            loadStart = System.nanoTime()
            Engine(EngineConfig(
                modelPath = file.path, backend = if (key.accelerator == "GPU") Backend.GPU() else Backend.CPU(threadCount = threads),
                maxNumTokens = context, cacheDir = key.cacheDirectory,
                visionBackend = if (key.vision) Backend.CPU(threadCount = threads) else null,
                audioBackend = if (key.audio) Backend.CPU(threadCount = threads) else null,
            ))
        }, initialize = { newEngine ->
            phase(operation, GenerationStage.LOADING_MODEL, "LITERT_ENGINE_INITIALIZE_START")
            try { newEngine.initialize() }
            catch (error: Throwable) {
                synchronized(operation.guard) { operation.snapshot.putLong("failedInitializationMs", (System.nanoTime() - loadStart) / 1_000_000) }
                throw error
            }
            phase(operation, GenerationStage.LOADING_MODEL, "LITERT_ENGINE_INITIALIZE_OK")
            loadMs = (System.nanoTime() - loadStart) / 1_000_000
            warmup.loaded()
            trace(operation, "LITERT_MODEL_OPEN_OK", mapOf("loadMs" to loadMs, "threads" to threads,
                "cache" to "private"))
        })
        if (reused) {
            val verified = checkNotNull(verifiedModel) { "Reused engine has no verified metadata" }
            require(thinkingOverride != true || verified.capabilities.getBoolean("thinking")) { "Model does not declare thinking support" }
            synchronized(operation.guard) { operation.snapshot.putString("formatVersion", verified.formatVersion) }
            phase(operation, GenerationStage.LOADING_MODEL, "LITERT_ENGINE_REUSE")
            send(operation, "capabilities", Bundle(verified.capabilities))
        }
        if (operation.cancelled.get()) throw CancellationException()
        val warmingThisLoad = warmup.shouldRun(data.getBoolean("warmup") && !diagnosticMode)
        if (warmingThisLoad) {
            continueSession = false
            rebuildReason = "SESSION_LOST"
            conversationReuse.invalidate()
            warmEngine(operation, data)
        }
        val nativeRole = checkNotNull(verifiedModel).metadata.historyRole
        if (nativeRole == null && requestedHistory.isNotEmpty()) throw ModelIntegrityException("HISTORY_ROLE_UNRESOLVED")
        toolData = Bundle(data)
        val enabledTools = data.getStringArrayList("enabledTools").orEmpty()
        require(enabledTools.isEmpty() || verifiedModel?.capabilities?.getBoolean("tools") == true) { "Model does not support tools" }
        val conversationConfig = LiteRtConversationPayload.config(data, diagnosticMode, nativeRole ?: Role.MODEL).copy(
            tools = if (diagnosticMode) emptyList() else chatTools.providers(data), automaticToolCalling = !diagnosticMode && enabledTools.isNotEmpty())
        var sessionMs: Long? = null
        if (continueSession) {
            phase(operation, GenerationStage.CREATING_SESSION, "LITERT_SESSION_REUSE")
        } else {
            phase(operation, GenerationStage.CREATING_SESSION, "LITERT_SESSION_CREATE_START")
            val sessionStart = System.nanoTime()
            resources.createConversation { it.createConversation(conversationConfig) }
            sessionMs = (System.nanoTime() - sessionStart) / 1_000_000
            conversationReuse.begin(settings(nativeRole ?: Role.MODEL), requestedHistory)
        }
        activeThinkingConfig = conversationConfig.thinkingConfig
        metrics = RuntimeMetrics(
            modelLoadDurationMs = loadMs, sessionCreationDurationMs = sessionMs,
            contextSize = context, backend = LiteRtLmInferenceRuntime.RUNTIME_ID,
            engineReused = reused, sessionReused = continueSession, modelPreparationDurationMs = operation.elapsedMs(),
            backendRequested = key.accelerator, backendEffective = key.accelerator,
            speculativeSupported = verifiedModel?.capabilities?.getBoolean("speculative"),
            speculativeEnabled = key.speculative, speculativeActive = key.speculative,
            warmupDurationMs = warmup.durationMs, warmupStatus = warmup.state.name,
            conversationRebuildReason = if (continueSession) "REUSED" else rebuildReason, cachedTokenCount = cachedTokens,
            warmupPerformedThisPreparation = warmingThisLoad,
            configuredCpuThreads = if (key.accelerator == "CPU") threads else null,
            thinkingEffective = conversationConfig.thinkingConfig?.enableThinking,
            automaticToolCallingActive = conversationConfig.automaticToolCalling && enabledTools.isNotEmpty(),
            visionBackendInitialized = resources.engine?.isInitialized() == true && resources.key?.vision == true,
            audioBackendInitialized = resources.engine?.isInitialized() == true && resources.key?.audio == true,
        )
        resetReason = null
        modelDiagnostics = synchronized(operation.guard) { Bundle(operation.snapshot) }.apply {
            putBoolean("engineReused", reused); putBoolean("sessionReused", continueSession)
            putLong("prepareMs", metrics.modelPreparationDurationMs!!)
            loadMs?.let { putLong("loadMs", it) }
            sessionMs?.let { putLong("sessionMs", it) }
            putString("historyRole", nativeRole?.value)
            putString("clientVersion", "0.17.1-history-role.1")
            putString("sampler", "temperature=${data.getFloat("temperature", .3f)},topP=${data.getFloat("topP", .95f)},topK=${data.getInt("topK", 40)},seed=${data.getInt("seed", 0)}")
        }
        trace(operation, "LITERT_SESSION_CREATE_OK", mapOf("sessionMs" to sessionMs, "engineReused" to reused,
            "prepareMs" to metrics.modelPreparationDurationMs, "historyTurns" to conversationConfig.initialMessages.size,
            "thinkingEnabled" to thinkingOverride, "sessionReused" to continueSession, "historyRole" to nativeRole?.value))
    }

    /** A real one-token inference in a disposable conversation. No chat/history/Room writes. */
    private fun warmEngine(operation: Operation, data: Bundle) {
        val started = SystemClock.elapsedRealtime()
        warmup.start()
        phase(operation, GenerationStage.WARMING_MODEL, "LITERT_WARMUP_START")
        val temporaryData = Bundle(data).apply {
            putStringArrayList("historyRoles", arrayListOf()); putStringArrayList("historyContents", arrayListOf()); putStringArrayList("historyImages", arrayListOf())
            putString("systemInstruction", null); putInt("maxOutput", 1)
        }
        val terminal = CountDownLatch(1)
        val failure = AtomicReference<Throwable?>(null)
        val output = AtomicBoolean(false)
        resources.createConversation { it.createConversation(LiteRtConversationPayload.config(temporaryData, false,
            verifiedModel?.metadata?.historyRole ?: Role.MODEL)) }
        val temporary = checkNotNull(resources.conversation)
        try {
            temporary.sendMessageAsync(Message.user("Hola"), object : MessageCallback {
                override fun onMessage(message: Message) {
                    if (message.contents.contents.filterIsInstance<Content.Text>().any { it.text.isNotEmpty() }) output.set(true)
                }
                override fun onDone() { operation.nativeGenerationSettled = true; terminal.countDown() }
                override fun onError(throwable: Throwable) {
                    operation.nativeErrorObserved = true; failure.set(throwable); terminal.countDown()
                }
            }, maxOutputToken = 1, thinkingConfig = ThinkingConfig(false), extraContext = mapOf("enable_thinking" to false))
            val timedOut = !terminal.await(30, TimeUnit.SECONDS)
            if (timedOut) {
                temporary.cancelProcess()
                if (!terminal.await(5, TimeUnit.SECONDS)) throw ModelIntegrityException("WARMUP_UNSETTLED")
            }
            if (operation.cancelled.get()) {
                warmup.finish(com.localai.workspace.performance.WarmupState.CANCELLED, SystemClock.elapsedRealtime() - started)
                throw CancellationException()
            }
            if (failure.get() != null) throw ModelIntegrityException("WARMUP_FAILED")
            warmup.finish(when {
                timedOut -> com.localai.workspace.performance.WarmupState.TIMEOUT
                output.get() -> com.localai.workspace.performance.WarmupState.WARMED
                else -> com.localai.workspace.performance.WarmupState.FAILED
            }, SystemClock.elapsedRealtime() - started)
        } catch (error: Throwable) {
            if (warmup.state == com.localai.workspace.performance.WarmupState.WARMING) {
                warmup.finish(com.localai.workspace.performance.WarmupState.FAILED, SystemClock.elapsedRealtime() - started)
            }
            throw error
        } finally {
            // Only close a settled temporary native operation. A watchdog resets a stuck
            // process; never release a handle while native callbacks can still use it.
            if (operation.nativeGenerationSettled || operation.nativeErrorObserved) resources.closeConversation()
            if (!operation.cancelled.get()) operation.nativeGenerationSettled = false
            synchronized(operation.guard) {
                warmup.durationMs?.let { operation.snapshot.putLong("warmupMs", it) }
                operation.snapshot.putString("warmupStatus", warmup.state.name)
            }
        }
        phase(operation, GenerationStage.CREATING_SESSION, "LITERT_WARMUP_DONE")
    }

    private fun generate(operation: Operation, data: Bundle) {
        val before = com.localai.workspace.performance.DeviceMeasurements.capture(this)
        metrics = metrics.copy(workerPssBeforeBytes = before.pssBytes, availableRamBeforeBytes = before.availableRamBytes,
            totalRamBytes = before.totalRamBytes, thermalBefore = before.thermal)
        synchronized(operation.guard) { operation.snapshot.putAll(modelDiagnostics) }
        chatTools.begin(toolData)
        chatTools.notify = { toolId, status, callId -> send(operation, "tool", Bundle().apply {
            putString("toolId", toolId); putString("toolStatus", status); putString("callId", callId) }) }
        val requestedAt = System.currentTimeMillis()
        val current = resources.conversation ?: error("No LiteRT conversation is loaded")
        val prompt = requireNotNull(data.getString("prompt"))
        require(prompt.isNotBlank() && prompt.length <= 100_000) { "Prompt is empty or exceeds the IPC budget" }
        val imagePath = data.getString("image")
        val audioPath = data.getString("audio")
        val contents = LiteRtVisionInput.contents(filesDir, prompt, imagePath, resources.key?.vision == true, audioPath, resources.key?.audio == true)
        if (audioPath != null) {
            val (_, info) = com.localai.workspace.audio.WavInput.validate(filesDir, audioPath)
            phase(operation, GenerationStage.PREPARING_PROMPT, "LITERT_AUDIO_INPUT_READY")
            operation.label = "Audio input: present; preprocess: success; backend: CPU; duration: ${info.durationMs} ms; bytes: ${info.dataBytes}"
            send(operation, "state", Bundle().apply { putString("event", "LITERT_AUDIO_INPUT_READY") })
        }
        if (imagePath != null) {
            val image = LiteRtVisionInput.validate(filesDir, imagePath)
            val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeFile(image.path, bounds)
            require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Prepared image could not be decoded; no text-only fallback" }
            phase(operation, GenerationStage.PREPARING_PROMPT, "LITERT_VISION_INPUT_READY")
            operation.label = "Vision input: present · preprocess: success · backend: CPU · ${bounds.outWidth} × ${bounds.outHeight} · ${image.length()} bytes"
            send(operation, "state", Bundle().apply { putString("event", "LITERT_VISION_INPUT_READY") })
            trace(operation, "LITERT_VISION_INPUT_READY", mapOf("visionInput" to "present", "visionBackend" to "CPU",
                "imageWidth" to bounds.outWidth, "imageHeight" to bounds.outHeight, "preparedBytes" to image.length()))
        }
        val userMessage = Message.user(contents)
        metrics = metrics.copy(visionInputPresent = contents.contents.any { it is Content.ImageFile || it is Content.ImageBytes },
            audioInputPresent = contents.contents.any { it is Content.AudioFile || it is Content.AudioBytes })
        val extra: Map<String, Any> = thinkingOverride?.let { mapOf("enable_thinking" to it) } ?: emptyMap()
        phase(operation, GenerationStage.PREPARING_PROMPT, "LITERT_PROMPT_FORMAT_START")
        // Inspect rendering without sending the rendered string. sendMessageAsync renders exactly once.
        val rendered = current.renderMessageIntoString(userMessage, extra)
        require(rendered.isNotEmpty()) { "Model chat template rendered an empty prompt" }
        trace(operation, "LITERT_PROMPT_FORMAT_OK", mapOf(
            "role" to "user", "inputChars" to prompt.length, "renderedChars" to rendered.length,
            "templateSource" to "model_bundle", "diagnostic" to diagnosticMode,
        ))
        var start = 0L
        val prefillAt = System.currentTimeMillis()
        val terminal = CountDownLatch(1)
        val failure = AtomicReference<Throwable?>(null)
        val repetition = RepetitionLoopDetector()
        val completedAnswer = StringBuilder()
        val chunks = AtomicInteger(0)
        val firstNano = AtomicReference<Long?>(null)
        val firstWall = AtomicReference<Long?>(null)
        val nativeDone = AtomicBoolean(false)
        val thinkingCallbacks = ThinkingCallbackMetrics()
        val controls = LiteRtDecodeControls.create(data.getFloat("repeatPenalty", 1f), ordinaryFinalText)
        trace(operation, "LITERT_DECODE_CONTROLS", mapOf("repeatPenalty" to controls.penalty.repetitionPenalty,
            "penaltyWindow" to controls.penalty.windowSize, "noRepeatNgram" to controls.noRepeat?.noRepeatNgramSize,
            "ngramWindow" to controls.noRepeat?.windowSize, "sampler" to modelDiagnostics.getString("sampler")))
        phase(operation, GenerationStage.PREFILLING, "LITERT_PREFILL_START")
        trace(operation, "LITERT_DECODE_SCHEDULED", mapOf("boundary" to "combined_conversation_api"))
        start = System.nanoTime() // Native TTFT starts at the actual async send boundary.
        current.sendMessageAsync(
            userMessage,
            object : MessageCallback {
                override fun onMessage(message: Message) {
                    try {
                        if (failure.get() != null || operation.cancelled.get()) return
                        // Message.toString() omits channels. Count/forward actual text chunks explicitly.
                        val text = message.contents.contents.filterIsInstance<Content.Text>().joinToString("") { it.text }
                        val output = thinkingCallbacks.accept(text, message.channels, (System.nanoTime() - start) / 1_000_000)
                        if (output.isEmpty()) {
                            trace(operation, "LITERT_CALLBACK_EMPTY", mapOf("channelCount" to message.channels.size))
                            return
                        }
                        val loop = repetition.append(output)
                        if (loop != null && failure.compareAndSet(null, RepetitionLoopException())) {
                            phase(operation, GenerationStage.GENERATING, "LITERT_REPETITION_DETECTED")
                            trace(operation, "LITERT_REPETITION_LOOP", mapOf("blockChars" to loop.blockCharacters, "repetitions" to loop.repetitions))
                            send(operation, "repetition_detected")
                            cancelWorker.execute {
                                synchronized(nativeResourceLock) {
                                    try { current.cancelProcess(); trace(operation, "LITERT_REPETITION_CANCEL_SENT") }
                                    catch (error: Throwable) { trace(operation, "LITERT_CANCEL_ERROR", mapOf("errorType" to error.javaClass.name)) }
                                }
                            }
                            // Await real native onDone/onError. The client resets a stuck worker after 5s.
                            return
                        }
                        if (firstNano.compareAndSet(null, System.nanoTime())) {
                            firstWall.set(System.currentTimeMillis())
                            synchronized(operation.guard) { operation.snapshot.putLong("ttftMs", (firstNano.get()!! - start) / 1_000_000) }
                            phase(operation, GenerationStage.GENERATING, "LITERT_FIRST_TOKEN")
                            trace(operation, "LITERT_DECODE_START", mapOf("boundary" to "first_output_callback"))
                        }
                        val count = chunks.incrementAndGet()
                        if (completedAnswer.length + output.length <= 100_000) completedAnswer.append(output)
                        else conversationReuse.invalidate()
                        synchronized(operation.guard) { operation.snapshot.putLong("outputChunks", count.toLong()) }
                        trace(operation, "LITERT_TOKEN", mapOf("outputChunks" to count, "generatedTokens" to null, "chars" to output.length))
                        send(operation, "token", Bundle().apply { putString("text", output); putInt("chunks", count) })
                    } catch (error: Throwable) {
                        failure.compareAndSet(null, error)
                        terminal.countDown()
                    }
                }
                override fun onDone() {
                    operation.nativeGenerationSettled = true
                    nativeDone.set(failure.get() == null && !operation.cancelled.get())
                    // onDone also occurs at an output cap. This API does not expose
                    // the actual EOS token or a native finish reason.
                    trace(operation, if (nativeDone.get()) "LITERT_NATIVE_DONE" else "LITERT_NATIVE_STOPPED",
                        mapOf("source" to "onDone", "outputChunks" to chunks.get()))
                    terminal.countDown()
                }
                override fun onError(throwable: Throwable) {
                    operation.nativeErrorObserved = true
                    trace(operation, "LITERT_CALLBACK_ERROR", mapOf("errorType" to throwable.javaClass.name))
                    failure.compareAndSet(null, throwable)
                    terminal.countDown()
                }
            },
            extraContext = extra,
            maxOutputToken = data.getInt("maxOutput", 128).coerceIn(1, 8192),
            repetitionPenaltyConfig = controls.penalty,
            noRepeatNgramConfig = controls.noRepeat,
            thinkingConfig = activeThinkingConfig,
        )
        trace(operation, "LITERT_NATIVE_SEND_RETURNED")
        // Only this native worker waits. IPC/control and the UI remain independently schedulable.
        terminal.await()
        metrics = thinkingCallbacks.applyTo(metrics, data.getInt("maxOutput", 128), thinkingOverride == true).copy(thinkingTokenBudget = activeThinkingConfig?.thinkingTokenBudget)
        failure.get()?.let { throw it }
        if (operation.cancelled.get()) throw CancellationException()
        val totalMs = (System.nanoTime() - start) / 1_000_000
        val benchmark = try { current.getBenchmarkInfo() }
        catch (error: Throwable) {
            trace(operation, "LITERT_METRICS_UNAVAILABLE", mapOf("errorType" to error.javaClass.name))
            null // An unsupported metric must never turn a successful text response into an error.
        }
        val prefillTps = benchmark?.lastPrefillTokensPerSecond?.takeIf { it.isFinite() && it > 0 }
        val prefillTokens = benchmark?.lastPrefillTokenCount?.takeIf { it >= 0 }
        val after = com.localai.workspace.performance.DeviceMeasurements.capture(this)
        metrics = metrics.copy(
            promptTokens = prefillTokens,
            outputTokens = benchmark?.lastDecodeTokenCount?.takeIf { it >= 0 },
            timeToFirstTokenMs = firstNano.get()?.let { (it - start) / 1_000_000 },
            prefillTokensPerSecond = prefillTps,
            decodeTokensPerSecond = benchmark?.lastDecodeTokensPerSecond?.takeIf { it.isFinite() && it > 0 },
            prefillDurationMs = if (prefillTokens != null && prefillTps != null) (prefillTokens / prefillTps * 1000).toLong() else null,
            prefillDurationSource = if (prefillTokens != null && prefillTps != null) "DERIVED_FROM_NATIVE_COUNT_AND_RATE" else null,
            generationRequestedAt = requestedAt, prefillStartedAt = prefillAt,
            prefillCompletedAt = null, firstTokenAt = firstWall.get(),
            totalGenerationDurationMs = totalMs, outputChunks = chunks.get(), eosObserved = null,
            nativeCompletionObserved = nativeDone.get(),
            outputLimitReached = benchmark?.lastDecodeTokenCount?.let { it >= data.getInt("maxOutput", 128).coerceIn(1, 8192) },
            workerPssBeforeBytes = before.pssBytes, workerPssAfterBytes = after.pssBytes,
            availableRamBeforeBytes = before.availableRamBytes, availableRamAfterBytes = after.availableRamBytes,
            totalRamBytes = after.totalRamBytes, thermalBefore = before.thermal, thermalAfter = after.thermal,
            finishState = "SUCCESS",
        )
        if (chunks.get() == 0) throw ThinkingOutputException(if (thinkingOverride == true) ThinkingFailure.reason(metrics) ?: "NO_TOKENS" else "NO_TOKENS")
        conversationReuse.completed(prompt, completedAnswer.toString(), imagePath, audioPath)
        trace(operation, "LITERT_METRICS", mapOf(
            "promptTokens" to metrics.promptTokens, "generatedTokens" to metrics.outputTokens,
            "ttftMs" to metrics.timeToFirstTokenMs, "prefillMs" to metrics.prefillDurationMs,
            "prefillBoundaryTimestamp" to null, "decodeTps" to metrics.decodeTokensPerSecond,
            "totalMs" to totalMs, "nativeDone" to nativeDone.get(), "eos" to null,
            "outputLimitReached" to metrics.outputLimitReached,
        ))
        trace(operation, "LITERT_PREFILL_OK", mapOf("source" to "post_completion_benchmark",
            "prefillMs" to metrics.prefillDurationMs, "promptTokens" to metrics.promptTokens,
            "nativeBoundaryTimestamp" to null))
        phase(operation, GenerationStage.COMPLETED, "LITERT_COMPLETED")
    }

    private fun closeResources() {
        activeThinkingConfig = null
        verifiedModel = null
        conversationReuse.invalidate()
        modelDiagnostics = Bundle()
        resources.close()
    }

    private fun phase(operation: Operation, stage: GenerationStage, event: String) {
        operation.stage = stage
        operation.checkpoint = event
        operation.label = when (event) {
            "LITERT_VERIFY_START" -> "Checking model file and SHA-256"
            "LITERT_JNI_INIT_START" -> "Loading the LiteRT native library"
            "LITERT_CAPABILITIES_START" -> "Reading native model capabilities"
            "LITERT_MODEL_OPEN_START" -> "Preparing engine configuration"
            "LITERT_ENGINE_INITIALIZE_START" -> "Initializing the engine"
            "LITERT_ENGINE_REUSE" -> "Reusing the initialized engine"
            "LITERT_WARMUP_START" -> "Model loaded · warming a temporary conversation"
            "LITERT_WARMUP_DONE" -> "Model warmed"
            "LITERT_SESSION_CLOSE_START" -> "Closing the previous conversation"
            "LITERT_SESSION_CREATE_START" -> "Creating the model conversation"
            "LITERT_SESSION_REUSE" -> "Reusing the completed native conversation and prompt cache"
            "LITERT_PROMPT_FORMAT_START" -> "Applying the embedded chat template"
            "LITERT_REPETITION_DETECTED" -> "Stopping repeated output in the native runtime"
            else -> null
        }
        trace(operation, event)
        send(operation, "state", Bundle().apply { putString("event", event) })
    }

    private fun trace(operation: Operation, event: String, fields: Map<String, Any?> = emptyMap()) =
        LiteRtTrace.event(event, operation.stage, modelName, operation.elapsedMs(), fields + mapOf("operation" to operation.id,
            "accelerator" to synchronized(operation.guard) { operation.snapshot.getString("accelerator") }))

    private fun send(operation: Operation, kind: String, data: Bundle = Bundle()) {
        val snapshot = synchronized(operation.guard) { Bundle(operation.snapshot) }
        data.putAll(snapshot)
        data.putString("checkpoint", operation.checkpoint)
        data.putString("label", operation.label)
        data.putString("stage", operation.stage.name)
        data.putLong("elapsedMs", operation.elapsedMs())
        send(operation.reply, operation.id, kind, data)
    }

    private fun sampleDevice(operation: Operation) {
        val memory = ActivityManager.MemoryInfo()
        getSystemService(ActivityManager::class.java).getMemoryInfo(memory)
        val pss = Debug.getPss()
        synchronized(operation.guard) {
            operation.snapshot.putLong("availableRam", memory.availMem)
            operation.snapshot.putLong("totalRam", memory.totalMem)
            if (pss > 0) operation.snapshot.putLong("workerPssKb", pss)
            operation.snapshot.putLong("storageAvailable", StatFs(cacheDir.path).availableBytes)
            operation.snapshot.putLong("workerUptimeMs", SystemClock.uptimeMillis() - operation.startedUptime)
            operation.snapshot.putString("abi", Build.SUPPORTED_ABIS.firstOrNull())
            operation.snapshot.putInt("androidVersion", Build.VERSION.SDK_INT)
        }
    }

    private fun send(reply: Messenger, id: String, kind: String, data: Bundle) {
        data.putString("id", id)
        data.putString("kind", kind)
        data.putInt("pid", Process.myPid())
        try { reply.send(IpcMessage.obtain(null, LiteRtIpc.EVENT).apply { this.data = data }) }
        catch (_: android.os.RemoteException) {
            // The UI process has gone away. Do not keep a model/callback alive in a zombie worker.
            Process.killProcess(Process.myPid())
        }
    }

    private fun diagnostic(error: Throwable, stage: GenerationStage, elapsed: Long) = Bundle().apply {
        putString("stage", stage.name)
        putLong("elapsedMs", elapsed)
        val code = when {
            error is OutOfMemoryError -> "OUT_OF_MEMORY"
            error is LinkageError -> "JNI_LINKAGE_ERROR"
            error is ThinkingOutputException -> error.reasonCode
            error.message == "NO_OUTPUT_FROM_NATIVE_CALLBACK" -> "NO_TOKENS"
            error is IllegalArgumentException -> "MODEL_OR_CONFIG_INVALID"
            error is ModelIntegrityException -> error.code
            error is RepetitionLoopException -> "REPETITION_LOOP"
            else -> "NATIVE_ERROR"
        }
        putString("code", code)
        putString("message", if (code == "REPETITION_LOOP") "LiteRT repeated the same passage. Native generation was stopped; ready to retry."
            else "LiteRT failed during ${stage.name.lowercase()}; $code.")
        val status = Regex("Status Code: ([0-9]+)").find(error.message.orEmpty())?.groupValues?.get(1)
        val nativeReason = if (stage in setOf(GenerationStage.LOADING_MODEL, GenerationStage.CREATING_SESSION)) {
            // Load/session failures have no user prompt; hide private filenames/paths nonetheless.
            error.message.orEmpty().replace(Regex("/[^\\s;,]+"), "<private-path>")
                .replace(Regex("\"[^\"]*\"|'[^']*'"), "<redacted>").take(512)
        } else "Native message omitted because it may contain conversation text."
        // Raw native messages can contain entire prompts. Expose types/status/frames only.
        putString("detail", "${error.javaClass.name}; nativeStatus=${status ?: "unavailable"}\n$nativeReason\n" +
            error.stackTrace.take(8).joinToString("\n") { "${it.className}.${it.methodName}:${it.lineNumber}" })
    }

    override fun onDestroy() {
        diagnosticWorker.shutdownNow()
        // onDestroy must not block the Android main thread on a JNI destructor.
        mainHandler.postDelayed({ Process.killProcess(Process.myPid()) }, 5_000)
        nativeWorker.execute { try { closeResources(); chatTools.close() } finally { Process.killProcess(Process.myPid()) } }
        super.onDestroy()
    }

}

internal class RepetitionLoopException : IllegalStateException("Native generation repeated consecutive substantial blocks")

/** Streamed integrity check, shared with unit tests; it never loads model bytes into RAM. */
internal object LiteRtModelIntegrity {
    fun verify(file: File, expectedSize: Long?, expectedHash: String?,
        onProgress: (Long, Long) -> Unit = { _, _ -> }, checkCancelled: () -> Unit = {}): String {
        if (expectedSize != null && file.length() != expectedSize) throw ModelIntegrityException("MODEL_SIZE_MISMATCH")
        val header = ByteArray(20)
        file.inputStream().use { input ->
            if (input.read(header) != header.size) throw ModelIntegrityException("MODEL_HEADER_TRUNCATED")
        }
        if (String(header, 0, 8, Charsets.US_ASCII) != "LITERTLM") throw ModelIntegrityException("MODEL_MAGIC_INVALID")
        val version = java.nio.ByteBuffer.wrap(header, 8, 12).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        val major = version.int
        val minor = version.int
        val patch = version.int
        if (major != 1) throw ModelIntegrityException("MODEL_FORMAT_VERSION_UNSUPPORTED")
        if (!expectedHash.isNullOrBlank()) {
            val digest = MessageDigest.getInstance("SHA-256")
            var completed = 0L
            onProgress(0, file.length())
            file.inputStream().buffered(1024 * 1024).use { input ->
                val buffer = ByteArray(1024 * 1024)
                while (true) {
                    checkCancelled()
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                    completed += count
                    onProgress(completed, file.length())
                }
            }
            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            if (!actual.equals(expectedHash, ignoreCase = true)) throw ModelIntegrityException("MODEL_HASH_MISMATCH")
        }
        return "$major.$minor.$patch"
    }
}

internal class ModelIntegrityException(val code: String) : Exception(code)
