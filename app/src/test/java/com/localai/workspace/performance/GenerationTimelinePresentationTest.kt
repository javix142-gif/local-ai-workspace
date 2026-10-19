package com.localai.workspace.performance

import com.localai.workspace.diagnostics.GenerationCheckpoint
import com.localai.workspace.diagnostics.NormalGenerationSnapshot
import com.localai.workspace.domain.model.RuntimeMetrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GenerationTimelinePresentationTest {
    @Test
    fun reportsElapsedAndAdjacentIntervalsInRecordedOrder() {
        val snapshot = NormalGenerationSnapshot(
            runId = "run",
            conversationId = null,
            projectPresent = false,
            startedAt = 1L,
            startedElapsedMs = 1_000L,
            events = listOf(
                GenerationCheckpoint("REQUEST_CREATED", 1_000L),
                GenerationCheckpoint("CONTEXT_BUILD_COMPLETE", 1_135L),
                GenerationCheckpoint("CONVERSATION_GATE_ACQUIRED", 1_180L),
            ),
        )

        assertEquals(
            listOf(
                GenerationTimelineRow(1, "REQUEST_CREATED", 0L, null),
                GenerationTimelineRow(2, "CONTEXT_BUILD_COMPLETE", 135L, 135L),
                GenerationTimelineRow(3, "CONVERSATION_GATE_ACQUIRED", 180L, 45L),
            ),
            GenerationTimelinePresentation.rows(snapshot),
        )
    }

    @Test
    fun marksElapsedValuesUnavailableAfterMonotonicClockReset() {
        val snapshot = NormalGenerationSnapshot(
            runId = "run",
            conversationId = null,
            projectPresent = false,
            startedAt = 1L,
            startedElapsedMs = 1_000L,
            events = listOf(
                GenerationCheckpoint("REQUEST_CREATED", 1_000L),
                GenerationCheckpoint("PROCESS_RESTART", 12L),
                GenerationCheckpoint("NEXT_PHASE", 30L),
            ),
        )

        val rows = GenerationTimelinePresentation.rows(snapshot)
        assertEquals(3, rows.size)
        assertNull(rows[1].elapsedSinceRequestMs)
        assertNull(rows[1].intervalSincePreviousMs)
        assertNull(rows[2].elapsedSinceRequestMs)
        assertNull(rows[2].intervalSincePreviousMs)
    }

    @Test
    fun onlyJoinsRuntimeMetricsToTheExactNormalChatRun() {
        val snapshot = NormalGenerationSnapshot(
            runId = "request-a",
            conversationId = null,
            projectPresent = false,
            startedAt = 1L,
            startedElapsedMs = 1_000L,
        )
        val sameRequest = RuntimeMetrics(
            normalGenerationTraceId = "request-a",
            timeToFirstTokenMs = 125,
            decodeTokensPerSecond = 4.5,
            endToEndTotalMs = 900,
        )
        val stale = sameRequest.copy(normalGenerationTraceId = "request-b")

        assertEquals(sameRequest, GenerationTimelinePresentation.correlatedMetrics(snapshot, sameRequest))
        assertEquals(listOf("Native TTFT" to "125 ms", "Decode rate" to "4.50 tokens/s", "Request end-to-end" to "900 ms"),
            GenerationTimelinePresentation.correlatedMetricRows(snapshot, sameRequest))
        assertNull(GenerationTimelinePresentation.correlatedMetrics(snapshot, stale))
        assertEquals(emptyList<Pair<String, String>>(), GenerationTimelinePresentation.correlatedMetricRows(snapshot, stale))
        assertNull(GenerationTimelinePresentation.correlatedMetrics(snapshot, null))
    }
}
