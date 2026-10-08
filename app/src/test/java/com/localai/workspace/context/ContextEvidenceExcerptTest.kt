package com.localai.workspace.context

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class ContextEvidenceExcerptTest {
    private fun utf8(text: String) = text.toByteArray(Charsets.UTF_8).size

    @Test fun cellQuestionKeepsTargetFormulaDependenciesAndHeaderProvenance() {
        val source = listOf(
            JSONObject().put("sheet", "Sales").put("headerCandidate", JSONArray().put(JSONObject().put("column", 1).put("label", "Amount"))).toString(),
            JSONObject().put("sheet", "Sales").put("row", 1).put("cells", JSONArray().put(cell("A1", 1, "Starting amount"))).toString(),
            JSONObject().put("sheet", "Sales").put("row", 2).put("cells", JSONArray().put(cell("A2", 1, "5"))).toString(),
            JSONObject().put("sheet", "Sales").put("row", 3).put("cells", JSONArray().put(cell("A3", 1, "4", "A2-A1"))).toString(),
        ).joinToString("\n")

        val excerpt = ContextEvidenceExcerptSelector.select("¿Cuál es el valor de la casilla A3?", source)

        assertEquals(listOf("A3", "A1", "A2"), excerpt.cellAddresses)
        assertTrue(excerpt.text.contains("header A=Amount"))
        assertTrue(excerpt.text.contains("A3=4 · formula=A2-A1"))
        assertTrue(utf8(excerpt.text) <= ContextEvidenceExcerptSelector.DEFAULT_MAX_UTF8_BYTES)
        assertNull("Cell summaries are structured provenance, not a contiguous character slice", excerpt.charStart)
    }

    @Test fun relevantSentenceIsSelectedWithExactOffsets() {
        val source = "Unrelated setup details. ".repeat(30) + "The Nebula editor is preferred for test projects. " + "Additional unrelated notes. ".repeat(30)
        val excerpt = ContextEvidenceExcerptSelector.select("¿Qué editor prefiero?", source)

        assertEquals("QUERY_MATCH", excerpt.reason)
        assertTrue(excerpt.text.contains("Nebula"))
        assertFalse(excerpt.text.contains("Unrelated setup"))
        assertEquals(source.substring(excerpt.charStart!!, excerpt.charEnd!!), excerpt.text)
    }

    @Test fun giantNoMatchParagraphIsAlwaysBounded() {
        val source = "unrelated-".repeat(2000)
        val excerpt = ContextEvidenceExcerptSelector.select("¿Dónde está Nebula?", source)

        assertEquals("TOP_RANKED_FALLBACK", excerpt.reason)
        assertTrue(excerpt.shortened)
        assertTrue(utf8(excerpt.text) <= ContextEvidenceExcerptSelector.DEFAULT_MAX_UTF8_BYTES)
        assertNotNull(excerpt.charStart)
        assertNotNull(excerpt.charEnd)
    }

    @Test fun unicodeWindowDoesNotExceedUtf8BudgetOrSplitSurrogatePairs() {
        val source = ("前🧭 dato irrelevante ").repeat(100) + "La palabra objetivo está aquí. " + ("🙂 cierre ").repeat(100)
        val excerpt = ContextEvidenceExcerptSelector.select("objetivo", source, maxUtf8Bytes = 128)

        assertTrue(excerpt.text.contains("objetivo"))
        assertTrue(utf8(excerpt.text) <= 128)
        assertFalse(excerpt.text.contains('\uFFFD'))
        assertTrue(excerpt.shortened)
    }

    @Test fun hostileJsonTextIsCompactedToCellSummaryRatherThanExpandedIntoPrompt() {
        val long = "<&'\"".repeat(300)
        val source = JSONObject().put("sheet", "Sales").put("row", 3)
            .put("cells", JSONArray().put(JSONObject().put("address", "A3").put("column", 1)
                .put("value", long).put("formula", "A2-A1"))).toString()
        val excerpt = ContextEvidenceExcerptSelector.select("A3", source)

        assertTrue(excerpt.text.contains("A3="))
        assertTrue(excerpt.text.contains("formula=A2-A1"))
        assertTrue(utf8(excerpt.text) <= ContextEvidenceExcerptSelector.DEFAULT_MAX_UTF8_BYTES)
        assertEquals(listOf("A3"), excerpt.cellAddresses)
    }

    private fun cell(address: String, column: Int, value: String, formula: String? = null) =
        JSONObject().put("address", address).put("column", column).put("value", value)
            .apply { formula?.let { put("formula", it) } }
}
