package com.localai.workspace.performance

import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.localai.workspace.domain.model.RuntimeMetrics
import java.io.File

enum class BenchmarkMode { COLD, WARM, CONTINUATION }
enum class BenchmarkResult { SUCCESS, ERROR, CANCELLED, TIMEOUT }
enum class MatrixStatus { SUPPORTED, UNSUPPORTED, FAILED, NOT_TESTED }

data class BenchmarkConfiguration(
    val backendRequested: String, val threads: Int, val context: Int, val maxOutput: Int,
    val temperature: Float, val topK: Int, val topP: Float, val repetitionPenalty: Float, val seed: Int,
    val speculativeEnabled: Boolean, val warmupEnabled: Boolean,
)

/** No prompts, responses, memory or document content. Null means not available. */
data class BenchmarkRecord(
    val schemaVersion: Int = 1, val id: String, val runId: String, val startedAtEpochMs: Long,
    val modelId: String, val modelFile: String, val modelSha256: String?,
    val runtimeVersion: String = "0.17.1", val suiteVersion: String = "gemma-text-v1",
    val device: String, val androidApi: Int, val iteration: Int, val mode: BenchmarkMode,
    val testId: String, val configuration: BenchmarkConfiguration,
    val metrics: RuntimeMetrics, val result: BenchmarkResult, val errorCode: String? = null,
    val validationPassed: Boolean? = null,
    val suiteGateWaitMs: Long? = null,
    // The host boundary is explicit: this is not ChatViewModel or frame-render TTFT.
    val ttftBoundary: String = "BENCHMARK_ACCEPT_TO_FIRST_STATE",
    val modelDisplayName: String? = null,
)

data class BenchmarkCase(val id: String, val prompt: String, val validator: ((String) -> Boolean)? = null)

object GemmaTextSuite {
    val independent = listOf(
        BenchmarkCase("trivial", "Hola"),
        BenchmarkCase("capital", "¿Cuál es la capital de Japón?", { Regex("(?i)tokio|tokyo").containsMatchIn(it) }),
        BenchmarkCase("moderate", "Explica en aproximadamente 150 palabras qué es una red neuronal.",
            { it.trim().split(Regex("\\s+")).size in 120..180 }),
        BenchmarkCase("reasoning", "Ana tiene 3 cajas con 4 lápices cada una y regala 5 lápices. ¿Cuántos le quedan? Responde con el número y una frase breve.",
            { Regex("\\b7\\b").containsMatchIn(it) }),
    )
    val continuation = listOf(
        BenchmarkCase("continuation_seed", "Recuerda este número para esta conversación: 17. Confirma brevemente."),
        BenchmarkCase("continuation_recall", "¿Qué número te pedí recordar?", { Regex("\\b17\\b").containsMatchIn(it) }),
        BenchmarkCase("continuation_reasoning", "Suma 3 a ese número. ¿Cuál es el resultado?", { Regex("\\b20\\b").containsMatchIn(it) }),
    )
}

/** Atomic private persistence, bounded retention, no Room/chat contamination. */
class BenchmarkStore(private val file: File) {
    private val gson = GsonBuilder().serializeNulls().setPrettyPrinting().create()
    @Synchronized fun read(): List<BenchmarkRecord> = runCatching {
        if (!file.isFile || file.length() > 8_000_000) emptyList()
        else gson.fromJson<List<BenchmarkRecord>>(file.readText(), object : TypeToken<List<BenchmarkRecord>>() {}.type) ?: emptyList()
    }.getOrDefault(emptyList())
    @Synchronized fun append(record: BenchmarkRecord): List<BenchmarkRecord> {
        val records = (read() + record).takeLast(500)
        file.parentFile?.mkdirs()
        val temporary = File(file.parentFile, "${file.name}.tmp")
        temporary.writeText(export(records))
        check(temporary.renameTo(file)) { "BENCHMARK_PERSISTENCE_FAILED" }
        return records
    }
    fun export(records: List<BenchmarkRecord>): String = gson.toJson(records)
}

object BenchmarkComparison {
    fun status(records: List<BenchmarkRecord>, backend: String, speculative: Boolean, mode: BenchmarkMode): MatrixStatus {
        val all = records.filter { it.configuration.backendRequested == backend &&
            it.configuration.speculativeEnabled == speculative && it.mode == mode }
        if (all.isEmpty()) return MatrixStatus.NOT_TESTED
        val latestRun = all.maxBy { it.startedAtEpochMs }.runId
        val matching = all.filter { it.runId == latestRun }
        if (matching.any { speculative && it.metrics.speculativeSupported == false }) return MatrixStatus.UNSUPPORTED
        if (matching.any { it.result != BenchmarkResult.SUCCESS || it.metrics.backendFallbackReason != null ||
                it.metrics.backendEffective != backend || it.metrics.speculativeActive != speculative }) return MatrixStatus.FAILED
        return MatrixStatus.SUPPORTED
    }
    fun median(values: List<Long?>): Long? {
        val sorted = values.filterNotNull().sorted()
        if (sorted.isEmpty()) return null
        return if (sorted.size % 2 == 1) sorted[sorted.size / 2]
        else sorted[sorted.size / 2 - 1] + (sorted[sorted.size / 2] - sorted[sorted.size / 2 - 1]) / 2
    }
}
