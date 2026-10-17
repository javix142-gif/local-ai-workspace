package com.localai.workspace.ui

import com.localai.workspace.semantic.v2.SemanticIndexJob
import org.junit.Assert.assertEquals
import org.junit.Test

class SemanticIndexJobLabelTest {
    @Test fun restartInterruptedIndexShowsActionableReindexMessage() {
        val job = SemanticIndexJob("index", "PROJECT:p", "space", "CANCELLED", 1, 2, 1, 1, 2, "PROCESS_RESTART_RECOVERABLE")

        assertEquals("Interrupted by app restart · re-index to reuse matching vectors", indexJobLabel(job))
    }

    @Test fun userCancelledIndexKeepsOrdinaryCancelledLabel() {
        val job = SemanticIndexJob("index", "PROJECT:p", "space", "CANCELLED", 1, 2, 1, 1, 2, "INDEX_JOB_CANCELLED")

        assertEquals("Cancelled", indexJobLabel(job))
    }
}
