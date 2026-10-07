package com.localai.workspace.performance

import android.content.Context
import android.os.Build
import android.os.SystemClock
import com.localai.workspace.data.ApplicationModelPreparation
import com.localai.workspace.data.WorkspaceRepository
import com.localai.workspace.domain.inference.*
import com.localai.workspace.domain.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.UUID

/** App-owned diagnostics, serialized through the SAME gate and pool as real chat. */
class PerformanceDiagnostics(
    private val context: Context, private val scope: CoroutineScope, private val workspace: WorkspaceRepository,
    private val runtimes: InferenceRuntimeRegistry, private val pool: ChatRuntimePool, private val gate: Mutex,
    private val preparation: ApplicationModelPreparation, private val settings: PerformanceSettings,
    storageFile: File? = null,
    private val anotherDiagnosticRunning: () -> Boolean = { false },
) {
    private val store = BenchmarkStore(storageFile ?: File(context.filesDir, "diagnostics/gemma-benchmarks.json"))
    private val mutableRecords = MutableStateFlow<List<BenchmarkRecord>>(emptyList())
    val records = mutableRecords.asStateFlow()
    private val mutableRunning = MutableStateFlow(false)
    val running = mutableRunning.asStateFlow()
    private val mutableStatus = MutableStateFlow("Idle · PHYSICAL_DEVICE_TEST_REQUIRED")
    val status = mutableStatus.asStateFlow()
    private val mutableLast = MutableStateFlow<Pair<String, RuntimeMetrics>?>(null)
    val lastGeneration = mutableLast.asStateFlow()
    private var job: Job? = null
    @Volatile private var active: InferenceRuntime? = null
    init { scope.launch { mutableRecords.value = store.read() } }

    fun recordGeneration(modelId: String, metrics: RuntimeMetrics) { mutableLast.value = modelId to metrics }
    fun applyProfile(profile: PerformanceProfile) {
        if (mutableRunning.value || anotherDiagnosticRunning()) return
        settings.update(profile)
        preparation.retry()
    }
    fun cancel() { active?.cancelGeneration(); job?.cancel() }
    fun report(): String = store.export(mutableRecords.value)
    fun clipboardReport(): String {
        var selected = mutableRecords.value.takeLast(50)
        var report = store.export(selected)
        while (report.toByteArray().size > 200_000 && selected.isNotEmpty()) {
            selected = selected.drop(1); report = store.export(selected)
        }
        return report
    }

    fun run(modelId: String, iterations: Int = 1, modes: Set<BenchmarkMode> = BenchmarkMode.entries.toSet()) {
        if (mutableRunning.value || modes.isEmpty() || anotherDiagnosticRunning()) return
        mutableRunning.value = true
        val profile = settings.state.value
        job = scope.launch {
            var lease: ChatRuntimePool.Lease? = null
            try {
                val model = workspace.allModels.first().firstOrNull { it.id == modelId } ?: error("MODEL_NOT_FOUND")
                val descriptor = model.toDescriptor()
                require(descriptor.runtime == RuntimeType.LITERT_LM) { "LITERT_MODEL_REQUIRED" }
                preparation.pauseForBenchmark()
                val queueStart = SystemClock.elapsedRealtime()
                gate.withLock {
                    val queueMs = SystemClock.elapsedRealtime() - queueStart
                    val runtime = pool.lease(runtimes.runtimeFor(descriptor))
                    lease = runtime; active = runtime
                    val runId = UUID.randomUUID().toString()
                    val conversationId = "benchmark-$runId"
                    val config = ModelLoadConfig(ModelSource(descriptor.localPath, descriptor.displayName, model.sourceUri,
                        descriptor.format, expectedSizeBytes = model.fileSize, sha256 = model.fileHash),
                        contextSize = (model.configuredContext ?: model.declaredContext ?: 4096).coerceIn(256, 8192),
                        threads = minOf(4, Runtime.getRuntime().availableProcessors()),
                        maxOutputTokens = model.maxOutputTokens.coerceIn(1, 8192), temperature = model.temperature,
                        topP = model.topP, topK = model.topK, repeatPenalty = model.repeatPenalty, seed = model.seed,
                        preferredAccelerator = profile.backend, warmupEnabled = profile.warmup,
                        speculativeEnabled = profile.speculative, conversation = ConversationPrompt("", conversationId = conversationId))
                    val snapshot = BenchmarkConfiguration(profile.backend.name, config.threads!!, config.contextSize,
                        config.maxOutputTokens, config.temperature, config.topK, config.topP, config.repeatPenalty, config.seed,
                        profile.speculative, profile.warmup)
                    var explicitRetry = true
                    suspend fun execute(case: BenchmarkCase, mode: BenchmarkMode, iteration: Int,
                        history: List<ChatMessage>): String? {
                        if (mode == BenchmarkMode.COLD) pool.releaseForDiagnostics() // Setup, before the cold request boundary.
                        val accepted = SystemClock.elapsedRealtime()
                        val startedAt = System.currentTimeMillis()
                        val before = DeviceMeasurements.capture(context)
                        var first: Long? = null
                        var measured = RuntimeMetrics()
                        var outcome = BenchmarkResult.SUCCESS
                        var errorCode: String? = null
                        val answer = StringBuilder() // Ephemeral only; never serialized or logged.
                        mutableStatus.value = "${profile.backend} / spec=${profile.speculative} / $mode / ${case.id} / $iteration"
                        try {
                            val buildStart = SystemClock.elapsedRealtime()
                            val conversation = ConversationPrompt(case.prompt, history, conversationId = conversationId)
                            val buildMs = SystemClock.elapsedRealtime() - buildStart
                            val retry = explicitRetry
                            explicitRetry = false
                            runtime.load(config.copy(conversation = conversation, retryExperimental = retry))
                            measured = runtime.metrics()
                            runtime.generate(GenerationRequest(case.prompt, maxOutputTokens = config.maxOutputTokens,
                                contextSize = config.contextSize, temperature = config.temperature, topP = config.topP,
                                topK = config.topK, repeatPenalty = config.repeatPenalty, seed = config.seed,
                                conversation = conversation)).collect { event ->
                                when (event) {
                                    is GenerationEvent.Token -> { answer.append(event.text); if (first == null) first = SystemClock.elapsedRealtime() - accepted }
                                    is GenerationEvent.Metrics -> measured = event.metrics
                                    is GenerationEvent.Error -> throw GenerationException(event.error)
                                    GenerationEvent.Cancelled -> throw CancellationException()
                                    else -> Unit
                                }
                            }
                            check(answer.isNotEmpty()) { "NO_OUTPUT" }
                            measured = measured.copy(contextBuildMs = buildMs)
                        } catch (cancelled: CancellationException) {
                            outcome = BenchmarkResult.CANCELLED; errorCode = "CANCELLED"
                            throw cancelled
                        } catch (error: Throwable) {
                            measured = runtime.metrics()
                            val diagnostic = (error as? GenerationException)?.diagnostic
                            errorCode = diagnostic?.code ?: error.javaClass.simpleName
                            outcome = if (errorCode.contains("TIMEOUT")) BenchmarkResult.TIMEOUT else BenchmarkResult.ERROR
                        } finally {
                            val after = DeviceMeasurements.capture(context)
                            measured = RequestTimings(accepted, 0, measured.contextBuildMs, first)
                                .merge(measured, SystemClock.elapsedRealtime(), outcome.name).copy(
                                appPssBeforeBytes = before.pssBytes, appPssAfterBytes = after.pssBytes,
                                finishState = outcome.name)
                            val record = BenchmarkRecord(id = UUID.randomUUID().toString(), runId = runId, startedAtEpochMs = startedAt,
                                modelId = model.id, modelFile = File(model.localPath).name, modelSha256 = model.fileHash,
                                modelDisplayName = model.displayName,
                                device = "${Build.MANUFACTURER} ${Build.MODEL}", androidApi = Build.VERSION.SDK_INT,
                                iteration = iteration, mode = mode, testId = case.id, configuration = snapshot,
                                metrics = measured, result = outcome, errorCode = errorCode,
                                suiteGateWaitMs = queueMs,
                                validationPassed = if (outcome == BenchmarkResult.SUCCESS) case.validator?.invoke(answer.toString()) else null)
                            withContext(NonCancellable) { mutableRecords.value = store.append(record) }
                            recordGeneration(model.id, measured)
                        }
                        return answer.toString().takeIf { outcome == BenchmarkResult.SUCCESS && it.isNotBlank() }
                    }
                    suspend fun prime(iteration: Int) {
                        val start = SystemClock.elapsedRealtime()
                        try {
                            val retry = explicitRetry
                            explicitRetry = false
                            runtime.load(config.copy(retryExperimental = retry))
                        } catch (error: Throwable) {
                            val code = (error as? GenerationException)?.diagnostic?.code ?: error.javaClass.simpleName
                            val outcome = when {
                                error is CancellationException -> BenchmarkResult.CANCELLED
                                code.contains("TIMEOUT") -> BenchmarkResult.TIMEOUT
                                else -> BenchmarkResult.ERROR
                            }
                            val record = BenchmarkRecord(id = UUID.randomUUID().toString(), runId = runId,
                                startedAtEpochMs = System.currentTimeMillis(), modelId = model.id, modelFile = File(model.localPath).name,
                                modelDisplayName = model.displayName,
                                modelSha256 = model.fileHash, device = "${Build.MANUFACTURER} ${Build.MODEL}", androidApi = Build.VERSION.SDK_INT,
                                iteration = iteration, mode = BenchmarkMode.WARM, testId = "engine_preparation", configuration = snapshot,
                                metrics = RequestTimings(start).merge(runtime.metrics(), SystemClock.elapsedRealtime(), outcome.name),
                                result = outcome, errorCode = code, suiteGateWaitMs = queueMs)
                            withContext(NonCancellable) { mutableRecords.value = store.append(record) }
                            throw error
                        }
                    }
                    for (iteration in 1..iterations.coerceIn(1, 10)) {
                        for (mode in listOf(BenchmarkMode.COLD, BenchmarkMode.WARM)) {
                            if (mode !in modes) continue
                            if (mode == BenchmarkMode.WARM) {
                                pool.releaseForDiagnostics()
                                prime(iteration)
                            }
                            for (case in GemmaTextSuite.independent) {
                                if (mode == BenchmarkMode.WARM) runtime.resetConversation("NEW_CONVERSATION")
                                execute(case, mode, iteration, emptyList())
                            }
                        }
                        if (BenchmarkMode.CONTINUATION in modes) {
                            if (BenchmarkMode.WARM !in modes) {
                                prime(iteration)
                            }
                            runtime.resetConversation("NEW_CONVERSATION")
                            var history = emptyList<ChatMessage>()
                            for ((index, case) in GemmaTextSuite.continuation.withIndex()) {
                                // Seed is a new conversation, not a falsely labelled KV continuation.
                                val response = execute(case, if (index == 0) BenchmarkMode.WARM else BenchmarkMode.CONTINUATION,
                                    iteration, history) ?: break
                                history = history + listOf(ChatMessage(MessageRole.USER, case.prompt), ChatMessage(MessageRole.ASSISTANT, response))
                            }
                        }
                    }
                }
                mutableStatus.value = "Finished · results saved locally"
            } catch (cancelled: CancellationException) { mutableStatus.value = "Cancelled · completed records preserved" }
            catch (error: Throwable) { mutableStatus.value = "Failed: ${(error as? GenerationException)?.diagnostic?.code ?: error.javaClass.simpleName}" }
            finally {
                withContext(NonCancellable) { lease?.close()?.join() }
                active = null; mutableRunning.value = false
                preparation.resumeAfterBenchmark()
            }
        }
    }
}
