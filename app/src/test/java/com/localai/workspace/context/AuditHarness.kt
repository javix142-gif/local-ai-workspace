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

        fun recordReferences(name:String,result:ContextEvidenceExcerpt,expected:List<String>,missing:List<String> = emptyList(),forbiddenText:String?=null) {
            val passed=result.cellReferences==expected && result.missingCellReferences==missing &&
                (forbiddenText==null || !result.text.contains(forbiddenText))
            cases.put(JSONObject().put("case",name).put("expectedCellReferences",JSONArray(expected))
                .put("actualCellReferences",JSONArray(result.cellReferences))
                .put("expectedMissingCellReferences",JSONArray(missing))
                .put("actualMissingCellReferences",JSONArray(result.missingCellReferences))
                .put("incompleteReasons",JSONArray(result.incompleteReasons))
                .put("excerpt",result.text).put("invariantPass",passed))
        }
        val log10Function=listOf(
            JSONObject().put("sheet","Hoja1").put("row",1).put("cells",JSONArray().put(JSONObject().put("address","A1").put("column",1).put("value","100"))).toString(),
            JSONObject().put("sheet","Hoja1").put("row",3).put("cells",JSONArray().put(JSONObject().put("address","A3").put("column",1).put("value","2").put("formula","LOG10(A1)"))).toString(),
            JSONObject().put("sheet","Hoja1").put("row",10).put("cells",JSONArray().put(JSONObject().put("address","LOG10").put("column",8509).put("value","function-decoy"))).toString(),
        ).joinToString("\n")
        recordReferences("log10_function_call_keeps_argument",ContextEvidenceExcerptSelector.select("Explica A3",log10Function),listOf("Hoja1!A3","Hoja1!A1"),forbiddenText="function-decoy")
        val log10Cell=listOf(
            JSONObject().put("sheet","Hoja1").put("row",10).put("cells",JSONArray().put(JSONObject().put("address","LOG10").put("column",8509).put("value","cell-value"))).toString(),
        ).joinToString("\n")
        recordReferences("log10_direct_query",ContextEvidenceExcerptSelector.select("Explica LOG10",log10Cell),listOf("Hoja1!LOG10"))
        val log10Formula=listOf(
            JSONObject().put("sheet","Hoja1").put("row",3).put("cells",JSONArray().put(JSONObject().put("address","A3").put("column",1).put("value","4").put("formula","LOG10"))).toString(),
            JSONObject().put("sheet","Hoja1").put("row",10).put("cells",JSONArray().put(JSONObject().put("address","LOG10").put("column",8509).put("value","cell-value"))).toString(),
        ).joinToString("\n")
        recordReferences("log10_formula_cell_reference",ContextEvidenceExcerptSelector.select("Explica A3",log10Formula),listOf("Hoja1!A3","Hoja1!LOG10"))
        val log10Qualified=listOf(
            JSONObject().put("sheet","Hoja1").put("row",10).put("cells",JSONArray().put(JSONObject().put("address","LOG10").put("column",8509).put("value","wrong-sheet"))).toString(),
            JSONObject().put("sheet","Hoja2").put("row",10).put("cells",JSONArray().put(JSONObject().put("address","LOG10").put("column",8509).put("value","right-sheet"))).toString(),
            JSONObject().put("sheet","Hoja2").put("row",3).put("cells",JSONArray().put(JSONObject().put("address","A3").put("column",1).put("value","4").put("formula","LOG10"))).toString(),
        ).joinToString("\n")
        recordReferences("hoja2_log10_uses_qualified_sheet",ContextEvidenceExcerptSelector.select("Hoja2!A3",log10Qualified),listOf("Hoja2!A3","Hoja2!LOG10"),forbiddenText="wrong-sheet")
        val log10Missing=listOf(
            JSONObject().put("sheet","Hoja1").put("row",10).put("cells",JSONArray().put(JSONObject().put("address","LOG10").put("column",8509).put("value","wrong-sheet"))).toString(),
            JSONObject().put("sheet","Hoja1").put("row",3).put("cells",JSONArray().put(JSONObject().put("address","A3").put("column",1).put("value","4").put("formula","'Hoja2'!LOG10"))).toString(),
        ).joinToString("\n")
        recordReferences("missing_qualified_log10_is_diagnostic",ContextEvidenceExcerptSelector.select("Hoja1!A3",log10Missing),listOf("Hoja1!A3"),listOf("Hoja2!LOG10"),forbiddenText="wrong-sheet")

        val crossSheet= listOf(
            JSONObject().put("sheet","Hoja1").put("sheetOrder",0).put("row",1).put("cells",JSONArray().put(JSONObject().put("address","A1").put("column",1).put("value","999"))).toString(),
            JSONObject().put("sheet","Hoja1").put("sheetOrder",0).put("row",3).put("cells",JSONArray().put(JSONObject().put("address","A3").put("column",1).put("value","4").put("formula","'Hoja2'!A1"))).toString(),
        ).joinToString("\n")
        val qualified=ContextEvidenceExcerptSelector.select("Explica A3",crossSheet)
        val qualifiedPassed=qualified.cellReferences==listOf("Hoja1!A3") &&
            qualified.missingCellReferences==listOf("Hoja2!A1") && "Hoja1!A1" !in qualified.cellReferences
        cases.put(JSONObject().put("case","qualified_sheet_no_homonym_fallback")
            .put("expectedCellReferences",JSONArray().put("Hoja1!A3"))
            .put("expectedMissingCellReferences",JSONArray().put("Hoja2!A1"))
            .put("actualCellReferences",JSONArray(qualified.cellReferences))
            .put("actualMissingCellReferences",JSONArray(qualified.missingCellReferences))
            .put("incompleteReasons",JSONArray(qualified.incompleteReasons))
            .put("excerpt",qualified.text).put("invariantPass",qualifiedPassed))
        val ascii = "x".repeat(200) + " TARGET " + "x".repeat(200)
        val multibyte = "界".repeat(200) + " TARGET " + "界".repeat(200)
        checkCase("ascii_anchor_control", "TARGET", ascii, anchor = "TARGET", budget = 128)
        checkCase("multibyte_anchor", "TARGET", multibyte, anchor = "TARGET", budget = 128)
        checkCase("multibyte_anchor_default_budget", "TARGET", multibyte, anchor = "TARGET")

        val report = JSONObject()
            .put("scope", "INDEPENDENT_HOST_SELECTOR_HARNESS_NOT_ANDROID")
            .put("sourceCommit", System.getenv("M00_SOURCE_COMMIT") ?: "NOT_PROVIDED")
            .put("cases", cases)
        val output = File("build/reports/m00-03/SELECTOR_REPRODUCCIONES.json")
        output.parentFile?.mkdirs()
        output.writeText(report.toString(2) + "\n")
        println("M00_03_SELECTOR_REPRODUCCIONES=${report.toString()}")
    }
}
