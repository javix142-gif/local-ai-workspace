package com.localai.workspace.performance

import com.localai.workspace.diagnostics.NormalGenerationSnapshot

data class GenerationTimelineRow(
    val ordinal: Int,
    val phase: String,
    val elapsedSinceRequestMs: Long?,
    val intervalSincePreviousMs: Long?,
)

/** Presents the existing monotonic request checkpoints without inferring missing stage boundaries. */
object GenerationTimelinePresentation {
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
