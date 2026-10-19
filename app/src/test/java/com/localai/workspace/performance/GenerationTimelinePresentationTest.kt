package com.localai.workspace.performance

import com.localai.workspace.diagnostics.GenerationCheckpoint
import com.localai.workspace.diagnostics.NormalGenerationSnapshot
import com.localai.workspace.domain.model.RuntimeMetrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GenerationTimelinePresentationTest {
    @Test
    fun presentsContextBudgetAsEstimatorSpecificMetadataAndOmitsNoContent() {
        val snapshot = NormalGenerationSnapshot(
            runId = "run",
            conversationId = null,
            projectPresent = false,
            startedAt = 1L,
            startedElapsedMs = 1_000L,
            contextComposition = com.localai.workspace.diagnostics.ContextCompositionSummary(
                estimator = "ESTIMATED_UTF8_BYTES_V1",
                estimatedUnits = 142,
                availableUnits = 300,
                includedItemCount = 2,
                omittedItemCount = 1,
                includedByKind = mapOf("SOURCE" to 1, "OBJECTIVE" to 1),
                omittedByReason = mapOf("TOKEN_BUDGET" to 1),
            ),
        )

        assertEquals(listOf(
            "Estimated context units" to "142 / 300 · ESTIMATED_UTF8_BYTES_V1",
            "Included / omitted context items" to "2 / 1",
            "Included by kind" to "OBJECTIVE 1, SOURCE 1",
            "Omitted by reason" to "TOKEN_BUDGET 1",
        ), GenerationTimelinePresentation.contextCompositionRows(snapshot))
        assertTrue(GenerationTimelinePresentation.contextCompositionRows(snapshot).none { (label, value) ->
            label.contains("prompt", ignoreCase = true) || value.contains("private", ignoreCase = true)
        })
        assertEquals(emptyList<Pair<String, String>>(), GenerationTimelinePresentation.contextCompositionRows(snapshot.copy(contextComposition = null)))
    }

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
