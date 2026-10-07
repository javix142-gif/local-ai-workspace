package com.localai.workspace.inference

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Message
import android.os.Messenger
import android.os.Process
import android.os.PowerManager
import android.os.SystemClock
import com.google.ai.edge.litertlm.Capabilities
import com.google.ai.edge.litertlm.ExperimentalApi
import com.localai.workspace.BuildConfig
import com.localai.workspace.domain.inference.GenerationError
import com.localai.workspace.domain.inference.GenerationException
import com.localai.workspace.domain.inference.GenerationProgress
import com.localai.workspace.domain.inference.GenerationStage
import com.localai.workspace.domain.inference.InferenceRuntime
import com.localai.workspace.domain.inference.InferenceTimeouts
import com.localai.workspace.domain.inference.InferenceWatchdog
import com.localai.workspace.domain.model.AcceleratorType
import com.localai.workspace.domain.model.GenerationEvent
import com.localai.workspace.domain.model.GenerationRequest
import com.localai.workspace.domain.model.ModelCapability
import com.localai.workspace.domain.model.ModelCompatibilityStatus
import com.localai.workspace.domain.model.ModelFormat
import com.localai.workspace.domain.model.ModelLoadConfig
import com.localai.workspace.domain.model.ModelMetadata
import com.localai.workspace.domain.model.ModelSource
import com.localai.workspace.domain.model.RuntimeCapabilities
import com.localai.workspace.domain.model.RuntimeMetrics
import com.localai.workspace.domain.model.RuntimeType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.selects.onTimeout
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.UUID
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicReference

/** UI-process adapter. All blocking inference JNI is isolated in the :litert worker. */
@OptIn(ExperimentalApi::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class LiteRtLmInferenceRuntime(
    private val context: Context,
    private val timeouts: InferenceTimeouts = InferenceTimeouts(),
    private val nowMs: () -> Long = { SystemClock.elapsedRealtime() },
) : InferenceRuntime, AutoCloseable {
    companion object { const val RUNTIME_ID = "litert-lm-android" }

    private inner class Pending(val id: String, initialStage: GenerationStage) {
        val events = Channel<Bundle>(256)
        val guard = Any()
        val watchdog = InferenceWatchdog(timeouts, nowMs).apply { enter(initialStage) }
        val failure = AtomicReference<GenerationError?>(null)
        val snapshot = Bundle()
        var checkpoint = "LITERT_SERVICE_BIND_START"
        var checkpointAt = nowMs()
        var finished = false
        var deadline: IndependentInferenceDeadline? = null
        var wakeLock: PowerManager.WakeLock? = null
        @Volatile var cancelRequested = false
        @Volatile var repetitionDetected = false
    }

    private val bindingGuard = Any()
    private val ipcThread = HandlerThread("LocalAI-LiteRT-IPC", Process.THREAD_PRIORITY_DEFAULT).apply { start() }
    private val ipcHandler = Handler(ipcThread.looper)
    private val connectionExecutor = Executor { task -> check(ipcHandler.post(task)) { "LiteRT IPC thread is closed" } }
    private val operationMutex = Mutex()
    private val _progress = MutableStateFlow(GenerationProgress())
    @Volatile private var pending: Pending? = null
    @Volatile private var remote: Messenger? = null
    @Volatile private var workerPid = 0
    @Volatile private var connection: ServiceConnection? = null
    private var modelName: String? = null
    private var lastMetrics = RuntimeMetrics(backend = RUNTIME_ID)
    private var loadedCapabilities = RuntimeCapabilities(
        RUNTIME_ID, true, false, false, false, false,
        accelerators = setOf(AcceleratorType.CPU, AcceleratorType.GPU), speculativeDecoding = true, backendVersion = LiteRtIpc.VERSION,
    )

    private val replies = Messenger(Handler(ipcThread.looper) { message ->
        val data = Bundle(message.data)
        val operation = pending
        if (operation != null && data.getString("id") == operation.id && operation.failure.get() == null) {
            val pid = data.getInt("pid")
            if (pid > 0 && pid != Process.myPid()) workerPid = pid
            try {
                synchronized(operation.guard) {
                    if (operation.failure.get() == null && !operation.finished) {
                        val kind = data.getString("kind")
                        updateSnapshot(operation, data)
                        when (kind) {
                            "state" -> {
                                val stage = GenerationStage.valueOf(requireNotNull(data.getString("stage")))
                                operation.watchdog.enter(stage)
                                _progress.value = _progress.value.copy(stage = stage, event = data.getString("event"),
                                    elapsedMs = data.getLong("elapsedMs"), detail = data.getString("label"),
                                    bytesCompleted = data.getLong("verifiedBytes").takeIf { data.containsKey("verifiedBytes") },
                                    bytesTotal = data.getLong("sizeBytes").takeIf { data.containsKey("sizeBytes") })
                            }
                            "diagnostic" -> _progress.value = _progress.value.copy(elapsedMs = data.getLong("elapsedMs"),
                                bytesCompleted = data.getLong("verifiedBytes").takeIf { data.containsKey("verifiedBytes") },
                                bytesTotal = data.getLong("sizeBytes").takeIf { data.containsKey("sizeBytes") })
                            "tool" -> {
                                _progress.value = _progress.value.copy(detail = "Tool: ${data.getString("toolId")} · ${data.getString("toolStatus")}", event = "LITERT_TOOL")
                            }
                            "token" -> {
                                operation.watchdog.outputReceived()
                                _progress.value = _progress.value.copy(stage = GenerationStage.GENERATING,
                                    outputChunks = data.getInt("chunks"), detail = null, bytesCompleted = null, bytesTotal = null)
                            }
                            "cancel_requested" -> { operation.cancelRequested = true; operation.watchdog.cancelRequested() }
                            "repetition_detected" -> {
                                operation.repetitionDetected = true
                                operation.watchdog.cancelRequested()
                                _progress.value = _progress.value.copy(detail = "Stopping repeated output in the native runtime")
                            }
                            "done", "error", "cancelled" -> { operation.finished = true; operation.deadline?.close(); releaseWakeLock(operation) }
                        }
                    }
                }
                if (operation.failure.get() == null && operation.events.trySend(data).isFailure) {
                    failOperation(operation, GenerationError(_progress.value.stage, "CALLBACK_BUFFER_OVERFLOW",
                        "LiteRT output exceeded the callback buffer."), allowFinished = true)
                }
            } catch (error: Throwable) {
                failOperation(operation, GenerationError(_progress.value.stage, "IPC_PROTOCOL_ERROR",
                    "LiteRT returned an invalid diagnostic event.", technicalDetail = error.javaClass.name), allowFinished = true)
            }
        }
        true
    })

    override val runtimeType = RuntimeType.LITERT_LM
    override val runtimeId = RUNTIME_ID
    override fun supports(format: ModelFormat) = format == ModelFormat.LITERT_LM
    // An API-level attempt, not a claim of device/driver compatibility.
    override fun availableAccelerators() = setOf(AcceleratorType.CPU, AcceleratorType.GPU)
    override fun observeProgress() = _progress.asStateFlow()

    override suspend fun inspectModel(source: ModelSource): ModelMetadata = withContext(Dispatchers.IO) {
        val file = File(source.absolutePath)
        require(file.isFile && file.canRead()) { "LiteRT-LM file is not readable" }
        LiteRtModelIntegrity.verify(file, null, null)
        val (capabilities,evidence) = inspectCapabilityEvidence(source)
        loadedCapabilities = RuntimeCapabilities(
            runtimeId = RUNTIME_ID, textGeneration = ModelCapability.TEXT in capabilities,
            embeddings = false, vision = ModelCapability.VISION in capabilities,
            toolCalling = ModelCapability.TOOL_CALLING in capabilities, promptCaching = false,
            audio = ModelCapability.AUDIO in capabilities, thinking = ModelCapability.THINKING in capabilities,
            speculativeDecoding = true, accelerators = availableAccelerators(), backendVersion = LiteRtIpc.VERSION,
        )
        LiteRtTrace.event("LITERT_INSPECT_OK", GenerationStage.IDLE, source.displayName,
            fields = mapOf("sizeBytes" to file.length(), "capabilities" to capabilities.map { it.name }))
        ModelMetadata(
            format = ModelFormat.LITERT_LM.name,
            // File names are labels, not evidence of architecture.
            architecture = null, family = null,
            quantization = Regex("(?i)(INT8|INT4|BF16|F16)").find(file.name)?.value?.uppercase(),
            parameterLabel = null, declaredContextLength = null, tokenizerChatTemplate = null,
            fileSizeBytes = file.length(),
            compatibility = if (ModelCapability.TEXT in capabilities) ModelCompatibilityStatus.COMPATIBLE else ModelCompatibilityStatus.UNSUPPORTED_FEATURE,
            warning = if (ModelCapability.TEXT in capabilities) null else "The LiteRT-LM bundle does not declare text input.",
            supportsTextGeneration = ModelCapability.TEXT in capabilities,
            supportsVision = ModelCapability.VISION in capabilities,
            supportsToolCalling = ModelCapability.TOOL_CALLING in capabilities,
            supportsAudio = ModelCapability.AUDIO in capabilities,
            supportsThinking = ModelCapability.THINKING in capabilities,
            supportsSpeculativeDecoding = ModelCapability.SPECULATIVE_DECODING in capabilities,
            capabilities = capabilities, accelerators = availableAccelerators(), backendVersion = LiteRtIpc.VERSION,
            capabilityEvidence = evidence,
        )
    }

    internal suspend fun inspectCapabilityEvidence(source: ModelSource): Pair<Set<ModelCapability>,Map<String,String>> = withContext(Dispatchers.IO) {
        val file=File(source.absolutePath)
        require(file.isFile && file.canRead()) { "Model file unavailable" }
        val bundle = LiteRtBundleMetadata.read(file)
        var evidence: Map<String,String> = emptyMap()
        val capabilities = Capabilities(file.path).use { caps ->
            val modalities = caps.inputModalities()
            val tools = LiteRtCapabilityResolver.resolve(bundle.toolsDeclared,caps.supportsFunctionCalling(),bundle.legacyGemmaContract)
            val thinking = LiteRtCapabilityResolver.resolve(bundle.thinkingDeclared,caps.supportsThinking(),bundle.legacyGemmaContract)
            evidence = tools.metadata("tools") + thinking.metadata("thinking") + mapOf("tools.sdkAdvertised" to caps.supportsFunctionCalling().toString(), "thinking.sdkAdvertised" to caps.supportsThinking().toString(), "capability.schema" to "0.2.2", "capability.templateSha256" to bundle.templateSha256.orEmpty())
            buildSet {
                if (modalities.text) add(ModelCapability.TEXT)
                if (modalities.vision) add(ModelCapability.VISION)
                if (modalities.audio) add(ModelCapability.AUDIO)
                if (modalities.text && (modalities.vision || modalities.audio)) add(ModelCapability.MULTIMODAL)
                if (tools.available) add(ModelCapability.TOOL_CALLING)
                if (thinking.available) add(ModelCapability.THINKING)
                if (caps.hasSpeculativeDecodingSupport()) add(ModelCapability.SPECULATIVE_DECODING)
            }
        }
        capabilities to evidence
    }

    private val backendPolicy = com.localai.workspace.performance.ExperimentalBackendPolicy()
    private val failedSpeculative = mutableMapOf<String, String>()
    private val failedExperimentMetrics = mutableMapOf<String, RuntimeMetrics>()
    private var warmupFailureForProfile: Pair<String, Pair<String, Long?>>? = null
    override suspend fun load(config: ModelLoadConfig): Unit = withContext(Dispatchers.IO) { operationMutex.withLock {
        require(config.preferredAccelerator in setOf(AcceleratorType.CPU, AcceleratorType.GPU)) { "NPU is not enabled" }
        val identity = "${config.preferredAccelerator}:${config.model.absolutePath}:${config.model.sha256}:${config.contextSize}:${config.threads ?: minOf(4, Runtime.getRuntime().availableProcessors())}:${config.enableVision}:${config.enableAudio}:${config.speculativeEnabled}"
        if (config.retryExperimental) {
            backendPolicy.retry(identity); failedSpeculative.remove(identity); failedExperimentMetrics.remove(identity)
            warmupFailureForProfile = null
        }
        if (warmupFailureForProfile?.first != identity || !config.warmupEnabled) warmupFailureForProfile = null
        var effective = config.copy(preferredAccelerator = backendPolicy.backend(config.preferredAccelerator, identity),
            speculativeEnabled = config.speculativeEnabled && identity !in failedSpeculative,
            warmupEnabled = config.warmupEnabled && warmupFailureForProfile == null)
        var fallback = backendPolicy.recordedFailure(identity) ?: failedSpeculative[identity]
        var attempts = 0
        while (true) {
            val attemptStarted = SystemClock.elapsedRealtime()
            try { loadOnce(effective); break }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (first: GenerationException) {
                if (++attempts >= 3) throw first
                if (first.diagnostic.stage == GenerationStage.WARMING_MODEL && effective.warmupEnabled) {
                    warmupFailureForProfile = identity to (first.diagnostic.code to lastMetrics.warmupDurationMs)
                    effective = effective.copy(warmupEnabled = false)
                } else if (effective.preferredAccelerator == AcceleratorType.GPU || effective.speculativeEnabled) {
                    fallback = "${effective.preferredAccelerator.name}/spec=${effective.speculativeEnabled}: ${first.diagnostic.code}"
                    if (effective.preferredAccelerator == AcceleratorType.GPU) backendPolicy.recordFailure(identity, fallback)
                    if (effective.speculativeEnabled) failedSpeculative[identity] = fallback
                    failedExperimentMetrics[identity] = lastMetrics.copy(
                        failedBackendPreparationMs = SystemClock.elapsedRealtime() - attemptStarted,
                        backendFailureDetail = first.diagnostic.technicalDetail.takeIf { first.diagnostic.stage == GenerationStage.LOADING_MODEL })
                    LiteRtTrace.event("LITERT_BACKEND_FALLBACK", first.diagnostic.stage, config.model.displayName,
                        fields = mapOf("requested" to config.preferredAccelerator.name, "effective" to "CPU", "errorCode" to first.diagnostic.code))
                    effective = effective.copy(preferredAccelerator = AcceleratorType.CPU, speculativeEnabled = false)
                } else throw first
            }
        }
        val failed = failedExperimentMetrics[identity]
        val warmupFailure = warmupFailureForProfile?.second
        lastMetrics = lastMetrics.copy(backendRequested = config.preferredAccelerator.name,
            backendEffective = effective.preferredAccelerator.name, backendFallbackReason = fallback,
            speculativeEnabled = config.speculativeEnabled,
            warmupDurationMs = warmupFailure?.second ?: lastMetrics.warmupDurationMs,
            failedBackendInitializationMs = failed?.failedBackendInitializationMs,
            failedBackendPreparationMs = failed?.failedBackendPreparationMs, backendFailureDetail = failed?.backendFailureDetail,
            warmupStatus = if (warmupFailure != null) "FAILED: ${warmupFailure.first}" else lastMetrics.warmupStatus)
    } }

    private suspend fun loadOnce(config: ModelLoadConfig) {
        modelName = config.model.displayName
        lastMetrics = RuntimeMetrics(backend = RUNTIME_ID, backendRequested = config.preferredAccelerator.name,
            speculativeEnabled = config.speculativeEnabled)
        val data = Bundle().apply {
            putString("path", config.model.absolutePath)
            putString("name", config.model.displayName)
            config.model.expectedSizeBytes?.let { putLong("size", it) }
            putString("hash", config.model.sha256)
            putInt("context", config.contextSize)
            putInt("threads", config.threads ?: minOf(4, Runtime.getRuntime().availableProcessors()))
            putInt("maxOutput", config.maxOutputTokens)
            putFloat("temperature", config.temperature)
            putFloat("topP", config.topP)
            putFloat("repeatPenalty", config.repeatPenalty)
            putInt("topK", config.topK)
            putInt("seed", config.seed)
            putBoolean("diagnostic", config.diagnosticMode)
            putBoolean("vision", !config.diagnosticMode && config.enableVision)
            putBoolean("nextHasImage", config.nextHasImage)
            putBoolean("audio", !config.diagnosticMode && config.enableAudio)
            putBoolean("nextHasAudio", config.nextHasAudio)
            putStringArrayList("enabledTools", ArrayList(config.enabledTools))
            putBoolean("validationScope", config.validationScope)
            putString("projectId", config.projectId)
            putString("messageId", config.messageId)
            putString("accelerator", config.preferredAccelerator.name)
            putBoolean("warmup", config.warmupEnabled)
            putBoolean("speculative", config.speculativeEnabled)
            LiteRtConversationPayload.write(this, config.conversation)
        }
        exchange(LiteRtIpc.LOAD, data, GenerationStage.LOADING_MODEL) { bundle ->
            if (bundle.getString("kind") == "done") lastMetrics = LiteRtIpc.decodeMetrics(bundle)
        }
    }

    override fun generate(request: GenerationRequest): Flow<GenerationEvent> = flow {
        operationMutex.withLock {
            try {
                val data = Bundle().apply {
                    putString("prompt", request.conversation?.userMessage ?: request.prompt)
                    putString("image", request.imagePath)
                    putString("audio", request.audioPath)
                    putInt("maxOutput", request.maxOutputTokens)
                    putFloat("repeatPenalty", request.repeatPenalty)
                }
                exchange(LiteRtIpc.GENERATE, data, GenerationStage.PREPARING_PROMPT) { bundle ->
                    when (bundle.getString("kind")) {
                        "state" -> emit(GenerationEvent.StateChanged(_progress.value))
                        "token" -> {
                            val text = bundle.getString("text").orEmpty()
                            if (text.isNotEmpty()) emit(GenerationEvent.Token(text, SystemClock.elapsedRealtime()))
                        }
                        "done" -> {
                            lastMetrics = withClientDecisions(LiteRtIpc.decodeMetrics(bundle))
                            emit(GenerationEvent.Metrics(lastMetrics))
                            emit(GenerationEvent.Completed)
                        }
                    }
                }
            } catch (cancel: CancellationException) {
                if (kotlinx.coroutines.currentCoroutineContext().isActive) emit(GenerationEvent.Cancelled)
                else throw cancel
            } catch (error: Throwable) {
                val diagnostic = (error as? GenerationException)?.diagnostic ?: GenerationError(
                    _progress.value.stage, "RUNTIME_ERROR", "LiteRT generation failed.",
                    technicalDetail = error.javaClass.name,
                )
                lastMetrics = lastMetrics.copy(errorCategory = diagnostic.code)
                _progress.value = _progress.value.copy(stage = GenerationStage.ERROR, error = diagnostic)
                emit(GenerationEvent.Error(diagnostic))
            }
        }
    }.flowOn(Dispatchers.IO)

    /** Messenger queues IPC; no inference JNI executes on this thread. */
    override fun cancelGeneration() {
        val operation = pending ?: return
        operation.cancelRequested = true
        operation.watchdog.cancelRequested()
        _progress.value = _progress.value.copy(cancellationRequested = true)
        LiteRtTrace.event("LITERT_CANCEL_REQUESTED", _progress.value.stage, modelName)
        try { remote?.send(Message.obtain(null, LiteRtIpc.CANCEL).apply {
            data = Bundle().apply { putString("id", operation.id) }
            replyTo = replies
        }) } catch (error: android.os.RemoteException) { operation.events.close(error) }
    }

    override suspend fun unload(): Unit = withContext(Dispatchers.IO) { operationMutex.withLock {
        warmupFailureForProfile = null
        if (remote == null) return@withLock
        try { exchange(LiteRtIpc.UNLOAD, Bundle(), GenerationStage.UNLOADING) {} }
        finally { withContext(NonCancellable) { resetWorker("unload") } }
    } }

    override suspend fun resetConversation(): Unit = resetConversation("SESSION_LOST")
    override suspend fun resetConversation(reason: String): Unit = withContext(Dispatchers.IO) { operationMutex.withLock {
        // Do not start a worker merely to reset an unloaded model.
        if (remote == null) return@withLock
        exchange(LiteRtIpc.RESET_CONVERSATION, Bundle().apply { putString("resetReason", reason) }, GenerationStage.UNLOADING) {}
    } }

    override fun capabilities() = loadedCapabilities
    override fun metrics() = lastMetrics
    private fun withClientDecisions(native: RuntimeMetrics) = native.copy(backendRequested = lastMetrics.backendRequested,
        backendFallbackReason = lastMetrics.backendFallbackReason, speculativeEnabled = lastMetrics.speculativeEnabled,
        warmupStatus = lastMetrics.warmupStatus, warmupDurationMs = lastMetrics.warmupDurationMs,
        failedBackendInitializationMs = lastMetrics.failedBackendInitializationMs,
        failedBackendPreparationMs = lastMetrics.failedBackendPreparationMs, backendFailureDetail = lastMetrics.backendFailureDetail)

    private suspend fun exchange(
        command: Int,
        data: Bundle,
        initialStage: GenerationStage,
        onEvent: suspend (Bundle) -> Unit,
    ) {
        val operation = Pending(UUID.randomUUID().toString(), initialStage)
        pending = operation
        lastMetrics = lastMetrics.copy(modelRetainedAfterStop = false)
        _progress.value = GenerationProgress(initialStage)
        var terminal = false
        try {
            acquireWakeLock(operation, command)
            operation.deadline = IndependentInferenceDeadline(operation.watchdog,
                onExpired = { expired ->
                    failOperation(operation, if (operation.repetitionDetected && expired.code == "CANCEL_TIMEOUT") {
                        expired.copy(code = "REPETITION_LOOP", message = "Repeated generation did not acknowledge native cancellation within ${timeouts.cancelMs} ms. Worker reset requested; ready to retry.")
                    } else expired)
                },
                onFailure = { failOperation(operation, GenerationError(_progress.value.stage, "WATCHDOG_ERROR",
                    "The LiteRT deadline monitor failed; worker reset requested.", technicalDetail = it.javaClass.name)) },
                threadFactory = java.util.concurrent.ThreadFactory { task ->
                    Thread(task, "LocalAI-LiteRT-deadline")
                })
            val endpoint = connect()
            data.putString("id", operation.id)
            endpoint.send(Message.obtain(null, command).apply { this.data = data; replyTo = replies })
            while (true) {
                operation.failure.get()?.let { throw GenerationException(it) }
                val event: Bundle? = select {
                    operation.events.onReceiveCatching { it.getOrThrow() }
                    onTimeout(200) { null }
                }
                operation.failure.get()?.let { throw GenerationException(it) }
                if (event != null) {
                    val kind = event.getString("kind")
                    if (kind == "capabilities") {
                        if (event.containsKey("speculative")) lastMetrics = lastMetrics.copy(speculativeSupported = event.getBoolean("speculative"))
                        loadedCapabilities = loadedCapabilities.copy(
                            textGeneration = event.getBoolean("text"), vision = event.getBoolean("vision"),
                            audio = event.getBoolean("audio"), toolCalling = event.getBoolean("tools"),
                            thinking = event.getBoolean("thinking"),
                        )
                    } else if (kind == "cancel_requested") {
                        operation.cancelRequested = true
                    } else if (kind == "error") {
                        if (event.containsKey("performanceMetrics")) lastMetrics = withClientDecisions(LiteRtIpc.decodeMetrics(event))
                        terminal = event.getBoolean("engineRetained") && event.getString("code") == "REPETITION_LOOP"
                        lastMetrics = lastMetrics.copy(modelRetainedAfterStop = terminal,
                            warmupDurationMs = if (event.containsKey("warmupMs")) event.getLong("warmupMs") else lastMetrics.warmupDurationMs,
                            failedBackendInitializationMs = if (event.containsKey("failedInitializationMs")) event.getLong("failedInitializationMs") else null)
                        throw GenerationException(enrichError(operation, GenerationError(
                            GenerationStage.valueOf(event.getString("stage") ?: initialStage.name),
                            event.getString("code") ?: "NATIVE_ERROR", event.getString("message") ?: "LiteRT native error.",
                            event.getLong("elapsedMs"), event.getString("detail"),
                            checkpoint = event.getString("checkpoint"),
                        )))
                    } else if (kind == "cancelled") {
                        if (event.containsKey("performanceMetrics")) lastMetrics = withClientDecisions(LiteRtIpc.decodeMetrics(event))
                        terminal = event.getBoolean("engineRetained")
                        lastMetrics = lastMetrics.copy(modelRetainedAfterStop = terminal)
                        _progress.value = GenerationProgress(GenerationStage.CANCELLED)
                        throw CancellationException("Native LiteRT cancellation completed")
                    }
                    if (kind !in setOf("hello", "diagnostic")) onEvent(event)
                    if (kind == "done") {
                        if (operation.cancelRequested) throw CancellationException("Cancelled as the native operation completed")
                        terminal = true
                        break
                    }
                }
            }
        } catch (error: Throwable) {
            val cancelTimeout = error is GenerationException && error.diagnostic.code == "CANCEL_TIMEOUT"
            if ((operation.cancelRequested && !cancelTimeout) || error is CancellationException) {
                _progress.value = GenerationProgress(GenerationStage.CANCELLED)
                if (error !is CancellationException) throw CancellationException("LiteRT operation cancelled").apply { initCause(error) }
            } else {
                val diagnostic = operation.failure.get() ?: (error as? GenerationException)?.diagnostic ?: enrichError(operation, GenerationError(
                    _progress.value.stage, "WORKER_DISCONNECTED",
                    "LiteRT worker stopped before completion. Check model/device memory and retry.",
                    technicalDetail = error.javaClass.name,
                ))
                _progress.value = GenerationProgress(GenerationStage.ERROR, error = diagnostic)
                if (error !is GenerationException) throw GenerationException(diagnostic, error)
            }
            throw error
        } finally {
            synchronized(operation.guard) { operation.finished = true }
            operation.deadline?.close()
            releaseWakeLock(operation)
            if (!terminal) withContext(NonCancellable) {
                resetWorker(if (operation.cancelRequested) "cancel" else "failure")
            }
            operation.events.close()
            if (pending === operation) pending = null
        }
    }

    private suspend fun connect(): Messenger {
        remote?.let { return it }
        val ready = CompletableDeferred<Messenger>()
        withContext(Dispatchers.IO) {
            val newConnection = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                    if (connection !== this) return
                    val endpoint = Messenger(binder)
                    remote = endpoint
                    ready.complete(endpoint)
                }
                override fun onServiceDisconnected(name: ComponentName) {
                    if (connection !== this) return
                    remote = null
                    pending?.events?.close(IllegalStateException("LiteRT worker disconnected"))
                }
                override fun onBindingDied(name: ComponentName) = onServiceDisconnected(name)
                override fun onNullBinding(name: ComponentName) { ready.completeExceptionally(IllegalStateException("LiteRT service has no binder")) }
            }
            connection = newConnection
            check(context.bindService(Intent(context, LiteRtLmService::class.java), Context.BIND_AUTO_CREATE, connectionExecutor, newConnection)) {
                "Cannot bind LiteRT service"
            }
        }
        val endpoint = withTimeoutOrNull(timeouts.bindMs) { ready.await() } ?: throw GenerationException(
            GenerationError(GenerationStage.LOADING_MODEL, "SERVICE_BIND_TIMEOUT", "LiteRT service did not connect.", timeouts.bindMs),
        )
        // Receive the actual child PID before starting JNI, so a hard block remains recoverable.
        val operation = checkNotNull(pending)
        endpoint.send(Message.obtain(null, LiteRtIpc.HELLO).apply {
            data = Bundle().apply { putString("id", operation.id) }; replyTo = replies
        })
        val hello = withTimeoutOrNull(timeouts.bindMs) { operation.events.receive() }
        check(hello?.getString("kind") == "hello" && workerPid > 0 && workerPid != Process.myPid()) { "LiteRT worker handshake failed" }
        return endpoint
    }

    private suspend fun resetWorker(reason: String) = withContext(Dispatchers.IO) {
        killWorkerImmediately(reason)
        val oldConnection = synchronized(bindingGuard) { connection.also { connection = null } }
        oldConnection?.let { binding ->
            try { context.unbindService(binding) }
            catch (_: IllegalArgumentException) { /* Binding failed before registration. */ }
        }
    }

    private fun killWorkerImmediately(reason: String) {
        val pid = synchronized(bindingGuard) { workerPid.also { workerPid = 0; remote = null } }
        if (pid > 0 && pid != Process.myPid()) {
            LiteRtTrace.event("LITERT_WORKER_RESET", _progress.value.stage, modelName, fields = mapOf("workerPid" to pid, "reason" to reason))
            Process.killProcess(pid)
        }
    }

    private fun updateSnapshot(operation: Pending, data: Bundle) {
        data.getString("checkpoint")?.let { checkpoint ->
            if (operation.checkpoint != checkpoint) { operation.checkpoint = checkpoint; operation.checkpointAt = nowMs() }
        }
        for (key in listOf("label", "mode", "cacheMode", "workerStack", "formatVersion", "fileHash", "abi", "historyRole", "clientVersion", "sampler")) {
            data.getString(key)?.let { operation.snapshot.putString(key, it.take(1600)) }
        }
        for (key in listOf("sizeBytes", "verifiedBytes", "availableRam", "totalRam", "workerPssKb", "storageAvailable", "workerUptimeMs", "loadMs", "prepareMs", "sessionMs", "ttftMs", "outputChunks")) {
            if (data.containsKey(key)) operation.snapshot.putLong(key, data.getLong(key))
        }
        for (key in listOf("threads", "context", "maxOutput", "androidVersion")) {
            if (data.containsKey(key)) operation.snapshot.putInt(key, data.getInt(key))
        }
        for (key in listOf("engineReused", "sessionReused")) if (data.containsKey(key)) operation.snapshot.putBoolean(key, data.getBoolean(key))
    }

    private fun enrichError(operation: Pending, error: GenerationError): GenerationError = synchronized(operation.guard) {
        val structural = buildString {
            append("App: ${BuildConfig.VERSION_NAME}; SDK: ${LiteRtIpc.VERSION}; CPU\n")
            append("Checkpoint age: ${nowMs() - operation.checkpointAt} ms\n")
            operation.snapshot.keySet().sorted().forEach { key ->
                val value = when (key) {
                    "threads", "context", "maxOutput", "androidVersion" -> operation.snapshot.getInt(key)
                    "label", "mode", "cacheMode", "workerStack", "formatVersion", "fileHash", "abi", "historyRole", "clientVersion", "sampler" -> operation.snapshot.getString(key)
                    "engineReused", "sessionReused" -> operation.snapshot.getBoolean(key)
                    else -> operation.snapshot.getLong(key)
                }
                append("$key=$value\n")
            }
        }
        error.copy(checkpoint = error.checkpoint ?: operation.checkpoint,
            technicalDetail = listOfNotNull(error.technicalDetail, structural).joinToString("\n"))
    }

    private fun failOperation(operation: Pending, failure: GenerationError, allowFinished: Boolean = false) {
        val diagnostic = synchronized(operation.guard) {
            if ((operation.finished && !allowFinished) || operation.failure.get() != null) return
            val error = enrichError(operation, failure)
            operation.failure.set(error)
            operation.finished = true
            _progress.value = GenerationProgress(GenerationStage.ERROR, error = error)
            error
        }
        try {
            // SIGKILL goes out on the independent timer/IPC thread, before any UI or Room work.
            killWorkerImmediately(diagnostic.code)
        } catch (error: Throwable) {
            LiteRtTrace.event("LITERT_RESET_ERROR", diagnostic.stage, modelName, fields = mapOf("errorType" to error.javaClass.name))
        } finally {
            releaseWakeLock(operation)
            operation.events.close(GenerationException(diagnostic))
            LiteRtTrace.event(diagnostic.code, diagnostic.stage, modelName, diagnostic.elapsedMs,
                mapOf("checkpoint" to diagnostic.checkpoint))
        }
    }

    private fun acquireWakeLock(operation: Pending, command: Int) {
        val leaseMs = timeouts.bindMs * 2 + when (command) {
            LiteRtIpc.LOAD -> timeouts.modelLoadMs + timeouts.sessionMs + timeouts.unloadMs
            LiteRtIpc.GENERATE -> timeouts.promptMs + timeouts.totalGenerationMs
            else -> timeouts.unloadMs
        } + 1_000
        operation.wakeLock = context.getSystemService(PowerManager::class.java).newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK, "LocalAI:LiteRT",
        ).apply { setReferenceCounted(false); acquire(leaseMs) }
    }

    private fun releaseWakeLock(operation: Pending) {
        val lock = synchronized(operation.guard) { operation.wakeLock.also { operation.wakeLock = null } }
        try { if (lock?.isHeld == true) lock.release() }
        catch (error: RuntimeException) { LiteRtTrace.event("LITERT_WAKE_LOCK_RELEASE_ERROR", _progress.value.stage,
            fields = mapOf("errorType" to error.javaClass.name)) }
    }

    override fun close() {
        pending?.let { operation ->
            operation.deadline?.close()
            releaseWakeLock(operation)
            operation.events.close(CancellationException("LiteRT runtime closed"))
        }
        killWorkerImmediately("runtime_close")
        val oldConnection = synchronized(bindingGuard) { connection.also { connection = null } }
        oldConnection?.let {
            try { context.unbindService(it) }
            catch (_: IllegalArgumentException) { /* A failed bind has no registration to remove. */ }
        }
        ipcThread.quitSafely()
    }
}
