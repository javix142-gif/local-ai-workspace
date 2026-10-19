package com.localai.workspace.performance

import com.localai.workspace.diagnostics.NormalGenerationSnapshot
import com.localai.workspace.domain.model.RuntimeMetrics
import java.util.Locale

data class GenerationTimelineRow(
    val ordinal: Int,
    val phase: String,
    val elapsedSinceRequestMs: Long?,
    val intervalSincePreviousMs: Long?,
)

/** Presents the existing monotonic request checkpoints without inferring missing stage boundaries. */
object GenerationTimelinePresentation {
    /** Metadata-only budget summary; units are explicitly estimator-specific, never native token counts. */
    fun contextCompositionRows(snapshot: NormalGenerationSnapshot): List<Pair<String, String>> {
        val summary = snapshot.contextComposition ?: return emptyList()
        return buildList {
            add("Estimated context units" to "${summary.estimatedUnits} / ${summary.availableUnits} · ${summary.estimator}")
            add("Included / omitted context items" to "${summary.includedItemCount} / ${summary.omittedItemCount}")
            if (summary.includedByKind.isNotEmpty()) {
                add("Included by kind" to summary.includedByKind.toSortedMap().entries.joinToString { "${it.key} ${it.value}" })
            }
            if (summary.omittedByReason.isNotEmpty()) {
                add("Omitted by reason" to summary.omittedByReason.toSortedMap().entries.joinToString { "${it.key} ${it.value}" })
            }
        }
    }

    /** Never joins stale benchmark/preparation metrics to a different chat trace. */
    fun correlatedMetrics(snapshot: NormalGenerationSnapshot, metrics: RuntimeMetrics?): RuntimeMetrics? =
        metrics?.takeIf { it.normalGenerationTraceId == snapshot.runId }

    /** Only real, non-null values from the exact request are shown; no native boundary is inferred. */
    fun correlatedMetricRows(snapshot: NormalGenerationSnapshot, metrics: RuntimeMetrics?): List<Pair<String, String>> {
        val actual = correlatedMetrics(snapshot, metrics) ?: return emptyList()
        return buildList {
            fun ms(label: String, value: Long?) {
                value?.takeIf { it >= 0 }?.let { add(label to "$it ms") }
            }
            ms("Agent / Skill routing", actual.skillRoutingMs)
            ms("Document retrieval", actual.sourceRetrievalMs)
            ms("Memory retrieval", actual.memoryRetrievalMs)
            ms("Conversation retrieval", actual.conversationRetrievalMs)
            ms("Context build (inclusive)", actual.contextBuildMs)
            ms("Generation gate wait", actual.inferenceGateWaitMs)
            ms("Model preparation", actual.modelPreparationDurationMs)
            ms("Native model load", actual.modelLoadDurationMs)
            ms("Native TTFT", actual.timeToFirstTokenMs)
            ms("Prefill duration", actual.prefillDurationMs)
            actual.prefillTokensPerSecond?.takeIf { it.isFinite() && it > 0 }?.let {
                add("Prefill rate" to "${String.format(Locale.ROOT, "%.2f", it)} tokens/s")
            }
            actual.decodeTokensPerSecond?.takeIf { it.isFinite() && it > 0 }?.let {
                add("Decode rate" to "${String.format(Locale.ROOT, "%.2f", it)} tokens/s")
            }
            ms("Request to first content", actual.requestTimeToFirstTokenMs)
            ms("Request end-to-end", actual.endToEndTotalMs)
        }
    }

    fun rows(snapshot: NormalGenerationSnapshot): List<GenerationTimelineRow> {
        var previous: Long? = null
        var monotonicClockValid = true
        return snapshot.events.mapIndexed { index, event ->
            val timestamp = event.elapsedRealtimeMs
            if (timestamp < snapshot.startedElapsedMs || previous?.let { timestamp < it } == true) {
                monotonicClockValid = false
            }
            val elapsed = timestamp - snapshot.startedElapsedMs
            val interval = previous?.let { timestamp - it }
            previous = timestamp
            GenerationTimelineRow(
                ordinal = index + 1,
                phase = event.phase,
                elapsedSinceRequestMs = elapsed.takeIf { monotonicClockValid },
                intervalSincePreviousMs = interval?.takeIf { monotonicClockValid },
            )
        }
    }
}
