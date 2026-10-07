package com.localai.workspace.data

import com.localai.workspace.domain.model.RuntimeMetrics
import java.util.Locale

/** Benchmarks stay separate from model text. Also reads the existing locale-comma records. */
class GenerationMetricsPresentation private constructor(val raw: String, private val fields: Map<String, String>) {
    val outputLimitReached: Boolean get() = fields["outputLimitReached"] == "true"

    fun rows(): List<Pair<String, String>> = buildList {
        fun timing(key: String, label: String) {
            fields[key]?.toLongOrNull()?.takeIf { it >= 0 }?.let { add(label to "${String.format(Locale.ROOT, "%.2f", it / 1000.0)} s") }
        }
        timing("requestTtftMs", "Wait until first text")
        timing("ttftMs", "Native TTFT (async send to useful callback)")
        timing("uiObservedTtftMs", "UI-observed first content (opt-in)")
        timing("warmupMs", "Engine warm-up")
        timing("failedBackendInitializationMs", "Failed experimental initialization")
        timing("queueMs", "Inference gate")
        timing("contextMs", "Context preparation")
        timing("requestTotalMs", "End-to-end request")
        timing("totalMs", "Response generation")
        timing("loadMs", "Model initialization")
        timing("prepareMs", "Model preparation")
        timing("prefillMs", "Input processing (derived from native count/rate)")
        for ((key, label) in listOf("prompt" to "New input tokens", "output" to "Output tokens")) {
            fields[key]?.toIntOrNull()?.takeIf { it >= 0 }?.let { add(label to it.toString()) }
        }
        fields["decodeTps"]?.replace(',', '.')?.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 }?.let {
            add("Generation speed" to "${String.format(Locale.ROOT, "%.2f", it)} tokens/s")
        }
        for ((key, label) in listOf("engineReused" to "Model reused", "sessionReused" to "Conversation reused", "outputLimitReached" to "Output limit reached")) {
            fields[key]?.toBooleanStrictOrNull()?.let { add(label to if (it) "Yes" else "No") }
        }
        for ((key, label) in listOf("backendRequested" to "Backend requested", "backendEffective" to "Backend effective",
            "fallback" to "Experimental fallback", "reuseReason" to "Conversation reason", "warmupStatus" to "Warm-up state",
            "thermal" to "Thermal status", "workerPssBytes" to "Worker PSS (bytes)", "speculativeActive" to "Speculative engine flag")) {
            fields[key]?.let { add(label to it) }
        }
    }

    companion object {
        private val marker = Regex("(?:^|,)([A-Za-z][A-Za-z0-9]*)=")
        fun decode(value: String?): GenerationMetricsPresentation? {
            if (value.isNullOrBlank() || value.length > 32_000 || value.startsWith("{")) return null
            val matches = marker.findAll(value).toList()
            val fields = matches.mapIndexed { index, match ->
                match.groupValues[1] to value.substring(match.range.last + 1,
                    matches.getOrNull(index + 1)?.range?.first ?: value.length).trim()
            }.toMap()
            return GenerationMetricsPresentation(value, fields)
        }

        fun encode(metrics: RuntimeMetrics): String = with(metrics) { listOfNotNull(
            engineReused?.let { "engineReused=$it" }, sessionReused?.let { "sessionReused=$it" },
            modelPreparationDurationMs?.let { "prepareMs=$it" }, modelLoadDurationMs?.let { "loadMs=$it" },
            sessionCreationDurationMs?.let { "sessionMs=$it" }, prefillDurationMs?.let { "prefillMs=$it" },
            promptTokens?.let { "prompt=$it" }, outputTokens?.let { "output=$it" },
            timeToFirstTokenMs?.let { "ttftMs=$it" }, requestTimeToFirstTokenMs?.let { "requestTtftMs=$it" },
            prefillTokensPerSecond?.let { "prefillTps=${String.format(Locale.ROOT, "%.2f", it)}" },
            decodeTokensPerSecond?.let { "decodeTps=${String.format(Locale.ROOT, "%.2f", it)}" },
            totalGenerationDurationMs?.let { "totalMs=$it" }, outputChunks?.let { "chunks=$it" },
            eosObserved?.let { "eos=$it" }, nativeCompletionObserved?.let { "nativeDone=$it" },
            outputLimitReached?.let { "outputLimitReached=$it" },
            warmupDurationMs?.let { "warmupMs=$it" }, warmupStatus?.let { "warmupStatus=$it" },
            warmupPerformedThisPreparation?.let { "warmupPerformed=$it" },
            failedBackendInitializationMs?.let { "failedBackendInitializationMs=$it" },
            inferenceGateWaitMs?.let { "queueMs=$it" }, contextBuildMs?.let { "contextMs=$it" },
            endToEndTotalMs?.let { "requestTotalMs=$it" }, callbackToUiStateMs?.let { "uiDeliveryMs=$it" },
            uiObservedTimeToFirstContentMs?.let { "uiObservedTtftMs=$it" },
            backendRequested?.let { "backendRequested=$it" }, backendEffective?.let { "backendEffective=$it" },
            backendFallbackReason?.let { "fallback=$it" }, conversationRebuildReason?.let { "reuseReason=$it" },
            cachedTokenCount?.let { "cachedTokens=$it" }, speculativeEnabled?.let { "speculativeEnabled=$it" },
            speculativeSupported?.let { "speculativeSupported=$it" }, speculativeActive?.let { "speculativeActive=$it" },
            workerPssAfterBytes?.let { "workerPssBytes=$it" }, appPssAfterBytes?.let { "appPssBytes=$it" },
            availableRamAfterBytes?.let { "availableRamBytes=$it" }, thermalAfter?.let { "thermal=$it" },
        ).joinToString(",") }
    }
}
