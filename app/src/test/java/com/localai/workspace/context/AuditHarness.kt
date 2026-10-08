package com.localai.workspace.context

import android.app.Application
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** Synthetic host-only reproductions for M00-03. The report records failures without rewriting them as passes. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class AuditHarness {
    @Test fun writeSelectorReproductions() {
        fun source(formula: String? = null): String = (1..3).joinToString("\n") { row ->
            val cell = JSONObject().put("address", "A$row").put("column", 1)
                .put("value", listOf("1", "5", "4")[row - 1])
            if (row == 3 && formula != null) cell.put("formula", formula)
            JSONObject().put("sheet", "Hoja1").put("row", row).put("cells", JSONArray().put(cell)).toString()
        }

        val cases = JSONArray()
        fun checkCase(
            name: String,
            query: String,
            text: String,
            expected: Set<String> = emptySet(),
            anchor: String? = null,
            budget: Int = 480,
        ) {
            val result = ContextEvidenceExcerptSelector.select(query, text, budget)
            val passed = result.cellAddresses.containsAll(expected) && (anchor == null || result.text.contains(anchor))
            cases.put(JSONObject()
                .put("case", name)
                .put("query", query)
                .put("expectedCells", JSONArray(expected.toList()))
                .put("actualCells", JSONArray(result.cellAddresses))
                .put("reason", result.reason)
                .put("excerpt", result.text)
                .put("bytes", result.text.toByteArray(Charsets.UTF_8).size)
                .put("budget", budget)
                .put("invariantPass", passed))
        }

        checkCase("relative_formula_control", "Explica A3", source("A2-A1"), setOf("A1", "A2", "A3"))
        checkCase("range_middle_cell", "Suma A1:A3", source(), setOf("A1", "A2", "A3"))
        checkCase("absolute_formula_dependencies", "Explica A3", source("\$A\$2-\$A\$1"), setOf("A1", "A2", "A3"))
        val ascii = "x".repeat(200) + " TARGET " + "x".repeat(200)
        val multibyte = "界".repeat(200) + " TARGET " + "界".repeat(200)
        checkCase("ascii_anchor_control", "TARGET", ascii, anchor = "TARGET", budget = 128)
        checkCase("multibyte_anchor", "TARGET", multibyte, anchor = "TARGET", budget = 128)
        checkCase("multibyte_anchor_default_budget", "TARGET", multibyte, anchor = "TARGET")

        val report = JSONObject()
            .put("scope", "INDEPENDENT_HOST_SELECTOR_HARNESS_NOT_ANDROID")
            .put("sourceCommit", System.getenv("M00_SOURCE_COMMIT") ?: "7da2cc71e57aed1d213ea03b01ba8b41be849939")
            .put("cases", cases)
        val output = File("build/reports/m00-03/SELECTOR_REPRODUCCIONES.json")
        output.parentFile?.mkdirs()
        output.writeText(report.toString(2) + "\n")
        println("M00_03_SELECTOR_REPRODUCCIONES=${report.toString()}")
    }
}
