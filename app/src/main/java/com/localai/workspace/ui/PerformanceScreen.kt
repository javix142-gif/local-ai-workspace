package com.localai.workspace.ui

import android.content.ClipData
import android.content.ClipboardManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.localai.workspace.AppGraph
import com.localai.workspace.data.ApplicationModelPreparation
import com.localai.workspace.domain.inference.GenerationStage
import com.localai.workspace.domain.model.AcceleratorType
import com.localai.workspace.domain.model.RuntimeType
import com.localai.workspace.domain.model.toDescriptor
import com.localai.workspace.performance.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PerformanceScreen(graph: AppGraph, back: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val profile by graph.performanceSettings.state.collectAsStateWithLifecycle()
    val prepared by graph.modelPreparation.state.collectAsStateWithLifecycle()
    val running by graph.performance.running.collectAsStateWithLifecycle()
    val status by graph.performance.status.collectAsStateWithLifecycle()
    val records by graph.performance.records.collectAsStateWithLifecycle()
    val latest by graph.performance.lastGeneration.collectAsStateWithLifecycle()
    val chatTrace by graph.normalGenerationTrace.state.collectAsStateWithLifecycle()
    val importDiagnostics by graph.documents.lastDiagnostics.collectAsStateWithLifecycle()
    val retrievalIndexDiagnostics by graph.retrieval.indexingDiagnostics.collectAsStateWithLifecycle()
    val models by graph.workspace.allModels.collectAsStateWithLifecycle(emptyList())
    var iterations by rememberSaveable { mutableStateOf("1") }
    var mode by rememberSaveable { mutableStateOf("ALL") }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var reportStatus by remember { mutableStateOf<String?>(null) }
    val model = models.firstOrNull { it.id == (selected ?: prepared.modelId) }
        ?: models.firstOrNull { ApplicationModelPreparation.isUsable(it) && it.toDescriptor().runtime == RuntimeType.LITERT_LM }
    val metrics = latest?.takeIf { it.first == model?.id }?.second ?: prepared.metrics?.takeIf { prepared.modelId == model?.id }
    val engineMetrics = prepared.metrics?.takeIf { prepared.modelId == model?.id }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            val report = withContext(Dispatchers.IO) { graph.performance.report() }
            reportStatus = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openOutputStream(uri)?.use { it.write(report.toByteArray()) }
                    ?: error("Cannot open export destination") }.fold({ "Report exported" }, { "Export failed" })
            }
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        TopAppBar(title = { Text("Local AI Performance") }, navigationIcon = { TextButton(onClick = back) { Text("Back") } })
        Text("Developer diagnostics · experiments are opt-in. Native results require your Motorola.")
        models.filter { ApplicationModelPreparation.isUsable(it) && it.toDescriptor().runtime == RuntimeType.LITERT_LM }.forEach { item ->
            FilterChip(selected = model?.id == item.id, enabled = !running,
                onClick = { selected = item.id; graph.modelPreparation.select(item.id) }, label = { Text(item.displayName) })
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("MODEL: ${model?.displayName ?: "No LiteRT model imported"}")
                Text("RUNTIME: LiteRT-LM 0.17.1")
                Text("BACKEND: requested ${profile.backend}; initialized ${engineMetrics?.backendEffective ?: "NOT TESTED"}")
                Text("VISION: CPU (unchanged)")
                val state = when {
                    running -> "BENCHMARK RUNNING (temporary conversations)"
                    prepared.progress.stage == GenerationStage.WARMING_MODEL -> "LOADED / WARMING"
                    prepared.preparing -> "LOADING"
                    prepared.error != null -> "ERROR"
                    prepared.ready && prepared.metrics?.warmupStatus == "WARMED" -> "READY / MODEL WARMED"
                    prepared.ready -> "READY / MODEL LOADED"
                    else -> "UNLOADED"
                }
                Text("MODEL STATE: $state")
                Text("WARMUP: ${engineMetrics?.warmupStatus ?: "NOT REQUESTED"}; ${engineMetrics?.warmupDurationMs ?: "unavailable"} ms")
                Text("CONVERSATION: ${metrics?.conversationRebuildReason ?: "NOT TESTED"}")
                Text("Warm-up performed in this preparation: ${metrics?.warmupPerformedThisPreparation ?: "unavailable"}")
                Text("SPECULATIVE: " + SpeculativeCapabilityState(metrics?.speculativeSupported, true,
                    profile.speculative, metrics?.speculativeActive).label)
                (engineMetrics?.backendFallbackReason ?: metrics?.backendFallbackReason)?.let { Text("FALLBACK: $it", color = MaterialTheme.colorScheme.error) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = profile.backend == AcceleratorType.CPU, enabled = !running,
                onClick = { graph.performance.applyProfile(profile.copy(backend = AcceleratorType.CPU)) }, label = { Text("CPU") })
            FilterChip(selected = profile.backend == AcceleratorType.GPU, enabled = !running,
                onClick = { graph.performance.applyProfile(profile.copy(backend = AcceleratorType.GPU)) }, label = { Text("GPU Experimental") })
        }
        Text("GPU compatibility and speed are NOT TESTED until initialization and benchmark succeed. Failed GPU attempts fall back visibly to CPU.")
        Row { Checkbox(checked = profile.warmup, enabled = !running,
            onCheckedChange = { graph.performance.applyProfile(profile.copy(warmup = it)) }); Text("Real warm-up (temporary conversation)") }
        Row { Checkbox(checked = profile.speculative, enabled = !running,
            onCheckedChange = { graph.performance.applyProfile(profile.copy(speculative = it)) }); Text("Speculative Experimental (bundle checked at load)") }
        Row { Checkbox(checked = profile.measureUiDelivery, enabled = !running,
            onCheckedChange = { graph.performance.applyProfile(profile.copy(measureUiDelivery = it)) }); Text("Measure adapter callback → UI state") }
        Text("No external draft is selected by this app. Acceptance rate: unavailable in this SDK. Active means the explicit flag was accepted at engine initialization.")
        Text("NORMAL CHAT TRACE", style = MaterialTheme.typography.titleMedium)
        chatTrace?.let { trace ->
            Text("Run: ${trace.runId}\nPhase: ${trace.phase}\nWorker checkpoint: ${trace.workerCheckpoint ?: "unavailable"}\nNative first callback: ${trace.firstCallbackMs ?: "unavailable"} ms\nFirst UI content: ${trace.firstVisibleUiMs ?: "unavailable"} ms\nNative in flight: ${trace.nativeInFlight ?: "unknown"}\nGate locked / owned: ${trace.gateLocked} / ${trace.gateOwned}\nCancelled: ${trace.cancelled}\nError class: ${trace.errorClass ?: "none"}\nProcess restart observed: ${trace.processRestartObserved}")
            TextButton(onClick = { (context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Normal chat trace", graph.normalGenerationTrace.report())) }) { Text("Copy normal chat trace") }
        } ?: Text("No normal generation recorded")
        Text("LAST DOCUMENT PROCESSING", style = MaterialTheme.typography.titleMedium)
        importDiagnostics?.let { item ->
            Text(buildString {
                append("Status: ${item.status} · Format: ${item.format} · Input: ${item.sourceBytes ?: "unavailable"} bytes\n")
                append("Copy/hash: ${item.copyHashMs ?: "unavailable"} ms · Parse: ${item.parseMs ?: "unavailable"} ms · Chunk: ${item.chunkMs ?: "unavailable"} ms\n")
                append("Database metadata/segments: ${item.metadataCommitMs ?: "unavailable"} / ${item.segmentCommitMs ?: "unavailable"} ms\n")
                append("Post-import callback: ${item.postImportCallbackMs ?: "unavailable"} ms · Total: ${item.totalMs ?: "unavailable"} ms\n")
                append("Pages: ${item.pageCount ?: "unavailable"} · Segments: ${item.segmentCount ?: "unavailable"}\n")
                if(item.failureStage!=null) append("Failure stage/type: ${item.failureStage} / ${item.failureType ?: "unknown"}")
            }, style = MaterialTheme.typography.bodySmall)
        } ?: Text("No document import measured")
        Text("Legacy EG1 background indexing: ${retrievalIndexDiagnostics.status} · ${retrievalIndexDiagnostics.durationMs ?: "unavailable"} ms · " +
            "segments ${retrievalIndexDiagnostics.processedSegments}/${retrievalIndexDiagnostics.candidateSegments} · " +
            "memories ${retrievalIndexDiagnostics.processedMemories}/${retrievalIndexDiagnostics.candidateMemories}" +
            (retrievalIndexDiagnostics.failureType?.let { " · $it" } ?: ""), style = MaterialTheme.typography.bodySmall)
        Text("Document timings contain metadata only; post-import callback does not imply background indexing finished.", style = MaterialTheme.typography.bodySmall)
        Text("LAST GENERATION", style = MaterialTheme.typography.titleMedium)
        Text(buildString {
            fun value(label: String, item: Any?) { append("$label: ${item ?: "unavailable"}\n") }
            value("Load ms", metrics?.modelLoadDurationMs); value("Preparation ms", metrics?.modelPreparationDurationMs)
            value("Failed experimental initialization ms", metrics?.failedBackendInitializationMs)
            value("Failed experimental preparation ms", metrics?.failedBackendPreparationMs)
            value("Configured CPU text threads", metrics?.configuredCpuThreads)
            value("Warm-up ms", metrics?.warmupDurationMs); value("Queue ms", metrics?.inferenceGateWaitMs)
            value("Context pipeline ms (inclusive)", metrics?.contextBuildMs); value("Native TTFT ms", metrics?.timeToFirstTokenMs)
            value("Agent / Skill routing ms", metrics?.skillRoutingMs); value("Memory retrieval ms", metrics?.memoryRetrievalMs)
            value("Document retrieval ms", metrics?.sourceRetrievalMs); value("Conversation retrieval ms", metrics?.conversationRetrievalMs)
            value("End-to-end TTFT ms", metrics?.requestTimeToFirstTokenMs); value("Prefill tokens/s", metrics?.prefillTokensPerSecond)
            value("UI-observed TTFT ms (opt-in, active screen)", metrics?.uiObservedTimeToFirstContentMs)
            value("Decode tokens/s", metrics?.decodeTokensPerSecond); value("Output tokens", metrics?.outputTokens)
            value("Thinking requested / policy / effective", "${metrics?.thinkingRequested} / ${metrics?.thinkingPolicyDecision} / ${metrics?.thinkingEffective}")
            value("Thinking budget (SDK tokens)", metrics?.thinkingTokenBudget)
            value("Output configured / effective", "${metrics?.configuredMaxOutput} / ${metrics?.effectiveMaxOutput}")
            value("Callbacks raw / thought / final / unknown", "${metrics?.rawCallbackCount} / ${metrics?.thoughtCallbackCount} / ${metrics?.finalCallbackCount} / ${metrics?.unknownChannelCallbackCount}")
            value("Thought / final characters", "${metrics?.thoughtCharacterCount} / ${metrics?.finalCharacterCount}")
            value("First callback ms", metrics?.firstCallbackMs)
            value("First thought ms", metrics?.timeToFirstThoughtMs); value("First final ms", metrics?.timeToFirstFinalMs)
            value("Visible characters", metrics?.visibleOutputLength); value("Output limit reached", metrics?.outputLimitReached)
            value("Rebuild reason", metrics?.conversationRebuildReason)
            value("Cached tokens (native)", metrics?.cachedTokenCount); value("Total request ms", metrics?.endToEndTotalMs)
            value("App PSS before/after bytes", "${metrics?.appPssBeforeBytes} / ${metrics?.appPssAfterBytes}")
            value("Worker PSS before/after bytes", "${metrics?.workerPssBeforeBytes} / ${metrics?.workerPssAfterBytes}")
            value("Available RAM bytes", metrics?.availableRamAfterBytes); value("Total RAM bytes", metrics?.totalRamBytes)
            value("Thermal", metrics?.thermalAfter); value("Adapter → UI state ms", metrics?.callbackToUiStateMs)
        }, style = MaterialTheme.typography.bodySmall)
        Text("Routing, retrieval and context timings are also shown separately; these nested stages are not additive.", style = MaterialTheme.typography.bodySmall)
        Text("Benchmark · fixed text suite v1", style = MaterialTheme.typography.titleMedium)
        Text("Cold runs unload the engine and can take several minutes. Benchmarks use temporary conversations, unchanged model sampling, and never save prompt/answer text.")
        OutlinedTextField(value = iterations, onValueChange = { iterations = it.filter(Char::isDigit).take(2) },
            enabled = !running, label = { Text("Iterations (1–10)") })
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            listOf("ALL", "COLD", "WARM", "CONTINUATION").forEach { item ->
                FilterChip(selected = mode == item, enabled = !running, onClick = { mode = item }, label = { Text(item, style = MaterialTheme.typography.labelSmall) })
            }
        }
        Button(enabled = model != null && !running, onClick = { model?.let {
            graph.performance.run(it.id, iterations.toIntOrNull() ?: 1,
                if (mode == "ALL") BenchmarkMode.entries.toSet() else setOf(BenchmarkMode.valueOf(mode)))
        } }) { Text("Run benchmark") }
        if (running) { LinearProgressIndicator(Modifier.fillMaxWidth()); OutlinedButton(onClick = graph.performance::cancel) { Text("Cancel benchmark") } }
        Text(status)
        Text("MATRIX (latest run per cell, selected model + exact sampling/context + warm-up setting)", style = MaterialTheme.typography.titleSmall)
        val matching = records.filter { it.modelId == model?.id && it.modelSha256 == model?.fileHash && it.suiteVersion == "gemma-text-v1" &&
            it.configuration.context == (model?.configuredContext ?: model?.declaredContext ?: 4096).coerceIn(256, 8192) &&
            it.configuration.temperature == model?.temperature && it.configuration.topP == model?.topP && it.configuration.topK == model?.topK &&
            it.configuration.maxOutput == model?.maxOutputTokens && it.configuration.repetitionPenalty == model?.repeatPenalty &&
            it.configuration.seed == model?.seed && it.configuration.warmupEnabled == profile.warmup }
        BenchmarkMode.entries.forEach { state ->
            Text("$state: " + listOf("CPU" to false, "CPU" to true, "GPU" to false, "GPU" to true).joinToString(" | ") { (backend, spec) ->
                "$backend/spec=$spec: ${BenchmarkComparison.status(matching, backend, spec, state)}"
            }, style = MaterialTheme.typography.bodySmall)
        }
        matching.groupBy { "${it.configuration.backendRequested}/spec=${it.configuration.speculativeEnabled}/${it.mode}/${it.testId}" }.forEach { (key, values) ->
            val successful = values.filter { it.result == BenchmarkResult.SUCCESS && it.metrics.backendFallbackReason == null }
            Text("$key · n=${successful.size} · median native TTFT=${BenchmarkComparison.median(successful.map { it.metrics.timeToFirstTokenMs }) ?: "unavailable"} ms · " +
                "median host TTFT=${BenchmarkComparison.median(successful.map { it.metrics.requestTimeToFirstTokenMs }) ?: "unavailable"} ms", style = MaterialTheme.typography.bodySmall)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                scope.launch {
                    val report = withContext(Dispatchers.IO) { graph.performance.clipboardReport() }
                    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Local AI benchmark", report))
                    reportStatus = "Copied latest ≤50 records (≤200 KB). Export JSON for all records."
                }
            }) { Text("Copy report") }
            OutlinedButton(onClick = { export.launch("local-ai-benchmark.json") }) { Text("Export JSON") }
        }
        reportStatus?.let { Text(it) }
        Text("Saved runs: ${records.map { it.runId }.distinct().size}; records: ${records.size}. Retention: latest 500 records.")
        Text("Benchmark end-to-end boundary: benchmark accept → first state. Chat end-to-end boundary: send accepted → first streaming state. Neither is a frame-render measurement.")
    }
}
