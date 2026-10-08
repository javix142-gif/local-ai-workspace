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
        assertEquals(listOf("Sales!A3", "Sales!A1", "Sales!A2"), excerpt.cellReferences)
        assertTrue(excerpt.text.contains("header A=Amount"))
        assertTrue(excerpt.text.contains("A3=4 · formula=A2-A1"))
        assertTrue(utf8(excerpt.text) <= ContextEvidenceExcerptSelector.DEFAULT_MAX_UTF8_BYTES)
        assertNull("Cell summaries are structured provenance, not a contiguous character slice", excerpt.charStart)
    }

    @Test fun rectangularCellRangeKeepsInteriorCells() {
        val source = listOf(1, 2, 3).joinToString("\n") { row ->
            JSONObject().put("sheet", "Hoja1").put("row", row)
                .put("cells", JSONArray().put(cell("A$row", 1, listOf("1", "5", "4")[row - 1])))
                .toString()
        }

        val excerpt = ContextEvidenceExcerptSelector.select("Suma A1:A3", source)

        assertEquals(listOf("A1", "A2", "A3"), excerpt.cellAddresses)
        assertTrue(excerpt.text.contains("A1=1"))
        assertTrue(excerpt.text.contains("A2=5"))
        assertTrue(excerpt.text.contains("A3=4"))
    }

    @Test fun absoluteAndMixedFormulaReferencesKeepDependencies() {
        val formulas = listOf("$" + "A$" + "2-$" + "A$" + "1", "A$" + "2-A$" + "1")
        for (formula in formulas) {
            val source = listOf(
                JSONObject().put("sheet", "Hoja1").put("row", 1).put("cells", JSONArray().put(cell("A1", 1, "1"))).toString(),
                JSONObject().put("sheet", "Hoja1").put("row", 2).put("cells", JSONArray().put(cell("A2", 1, "5"))).toString(),
                JSONObject().put("sheet", "Hoja1").put("row", 3).put("cells", JSONArray().put(cell("A3", 1, "4", formula))).toString(),
            ).joinToString("\n")

            val excerpt = ContextEvidenceExcerptSelector.select("Explica A3", source)

            assertEquals("formula=$formula", listOf("A3", "A1", "A2"), excerpt.cellAddresses)
            assertTrue("formula=$formula", excerpt.text.contains("A1=1"))
            assertTrue("formula=$formula", excerpt.text.contains("A2=5"))
            assertTrue("formula=$formula", excerpt.text.contains("A3=4"))
        }
    }

    @Test fun qualifiedFormulaDependencyNeverAliasesSameAddressOnCurrentSheet() {
        val source = listOf(
            row(1, cell("A1", 1, "999")),
            row(3, cell("A3", 1, "", "'Hoja2'!A1")),
        ).joinToString("\n")

        val excerpt = ContextEvidenceExcerptSelector.select("Explica A3", source)

        assertFalse(
            "Hoja1!A1 must not satisfy an explicit Hoja2!A1 dependency; selected=${excerpt.cellAddresses}, missing=${excerpt.missingCellAddresses}, text=${excerpt.text}",
            "A1" in excerpt.cellAddresses,
        )
        assertTrue("the unresolved qualified dependency must be retained", excerpt.missingCellReferences.any { it.equals("Hoja2!A1", ignoreCase = true) })
        assertTrue("the excerpt must be marked incomplete", excerpt.incompleteReasons.isNotEmpty())
    }

    @Test fun unqualifiedFormulaReferenceResolvesOnOwningSheetEvenWithHomonymousCell() {
        val source = listOf(
            sheetRow("Hoja1", 1, 0, cell("A1", 1, "999")),
            sheetRow("Hoja2", 1, 1, cell("A1", 1, "5"), cell("B1", 2, "6", "A1")),
        ).joinToString("\n")

        val excerpt = ContextEvidenceExcerptSelector.select("Explica Hoja2!B1", source)

        assertEquals(listOf("Hoja2!B1", "Hoja2!A1"), excerpt.cellReferences)
        assertTrue(excerpt.text.contains("Hoja2!A1=5"))
        assertFalse(excerpt.text.contains("Hoja1!A1=999"))
        assertTrue(excerpt.incompleteReasons.isEmpty())
    }

    @Test fun quotedSheetNamesMatchIgnoringCaseAndPreserveOriginalDisplayName() {
        val source = sheetRow("Hoja 2", 1, 0, cell("A1", 1, "cinco"))
        val excerpt = ContextEvidenceExcerptSelector.select("Explica 'HOJA 2'!\$A\$1", source)

        assertEquals(listOf("'Hoja 2'!A1"), excerpt.cellReferences)
        assertTrue(excerpt.text.contains("'Hoja 2'!A1=cinco"))
        assertTrue(excerpt.incompleteReasons.isEmpty())
    }

    @Test fun escapedApostropheSheetAndMixedRangeQualifiersResolveExactly() {
        val source = listOf(
            sheetRow("Calc", 1, 0, cell("C1", 3, "ok", "'O''Brien'!A$2:B$3")),
            sheetRow("O'Brien", 1, 1, cell("A2", 1, "a2"), cell("B2", 2, "b2")),
            sheetRow("O'Brien", 3, 1, cell("A3", 1, "a3"), cell("B3", 2, "b3")),
            sheetRow("O'Brien", 4, 1, cell("A4", 1, "wrong-sheet")),
        ).joinToString("\n")
        val excerpt = ContextEvidenceExcerptSelector.select("Calc!C1", source)

        assertEquals(listOf("Calc!C1", "'O''Brien'!A2", "'O''Brien'!B2", "'O''Brien'!A3", "'O''Brien'!B3"), excerpt.cellReferences)
        assertFalse(excerpt.text.contains("wrong-sheet"))
        assertTrue(excerpt.incompleteReasons.isEmpty())
    }

    @Test fun localAndQualifiedRectangularRangesAreInclusive() {
        val localSource = listOf(
            sheetRow("Hoja1", 1, 0, cell("A1", 1, "11"), cell("B1", 2, "12")),
            sheetRow("Hoja1", 2, 0, cell("A2", 1, "21"), cell("B2", 2, "22")),
        ).joinToString("\n")
        val qualifiedSource = listOf(
            sheetRow("Hoja 2", 1, 1, cell("A1", 1, "31"), cell("B1", 2, "32")),
            sheetRow("Hoja 2", 2, 1, cell("A2", 1, "41"), cell("B2", 2, "42")),
            sheetRow("Calc", 1, 2, cell("C1", 3, "range", "'Hoja 2'!\$A\$1:B\$2")),
        ).joinToString("\n")

        val local = ContextEvidenceExcerptSelector.select("A1:B2", localSource)
        val qualified = ContextEvidenceExcerptSelector.select("Calc!C1", qualifiedSource)

        assertEquals(listOf("Hoja1!A1", "Hoja1!B1", "Hoja1!A2", "Hoja1!B2"), local.cellReferences)
        assertEquals(listOf("Calc!C1", "'Hoja 2'!A1", "'Hoja 2'!B1", "'Hoja 2'!A2", "'Hoja 2'!B2"), qualified.cellReferences)
        assertFalse(qualified.text.contains("Hoja1!A1=11"))
    }

    @Test fun threeDimensionalRangeIncludesEveryOrderedSheetAndInteriorSheet() {
        val source = listOf(
            sheetRow("Hoja1", 1, 0, cell("A1", 1, "one")),
            sheetRow("Hoja2", 1, 1, cell("A1", 1, "two")),
            sheetRow("Hoja3", 1, 2, cell("A1", 1, "three")),
        ).joinToString("\n")
        val excerpt = ContextEvidenceExcerptSelector.select("Hoja1:Hoja3!\$A\$1", source)

        assertEquals(listOf("Hoja1!A1", "Hoja2!A1", "Hoja3!A1"), excerpt.cellReferences)
        assertTrue(excerpt.text.contains("Hoja2!A1=two"))
        assertTrue(excerpt.incompleteReasons.isEmpty())
    }

    @Test fun quotedThreeDimensionalRectangleExpandsAcrossEverySheetAndCell() {
        val source=listOf(
            sheetRow("Depto 1",1,0,cell("A1",1,"11"),cell("B1",2,"12")),
            sheetRow("Depto 1",2,0,cell("A2",1,"13"),cell("B2",2,"14")),
            sheetRow("Depto 2",1,1,cell("A1",1,"21"),cell("B1",2,"22")),
            sheetRow("Depto 2",2,1,cell("A2",1,"23"),cell("B2",2,"24")),
            sheetRow("Depto 3",1,2,cell("A1",1,"31"),cell("B1",2,"32")),
            sheetRow("Depto 3",2,2,cell("A2",1,"33"),cell("B2",2,"34")),
        ).joinToString("\n")
        val excerpt=ContextEvidenceExcerptSelector.select("'Depto 1:Depto 3'!\$A\$1:\$B\$2",source)

        assertEquals(12,excerpt.cellReferences.size)
        assertTrue(excerpt.cellReferences.contains("'Depto 2'!B2"))
        assertTrue(excerpt.text.contains("'Depto 2'!B2=24"))
        assertTrue(excerpt.incompleteReasons.isEmpty())
        assertTrue(utf8(excerpt.text)<=480)
    }

    @Test fun omittedThreeDimensionalInteriorOrderIsExplicitlyIncomplete() {
        val source = listOf(
            sheetRow("Hoja1", 1, 0, cell("A1", 1, "one")),
            sheetRow("Hoja3", 1, 2, cell("A1", 1, "three")),
        ).joinToString("\n")
        val excerpt = ContextEvidenceExcerptSelector.select("Hoja1:Hoja3!A1", source)

        assertTrue(excerpt.cellReferences.isEmpty())
        assertTrue("THREE_D_SHEET_ORDER_INCOMPLETE" in excerpt.incompleteReasons)
        assertTrue(excerpt.unresolvedCellReferences.isNotEmpty())
    }

    @Test fun conflictingThreeDimensionalSheetOrderNeverUsesAnArbitraryMapping() {
        val source = listOf(
            sheetRow("Hoja1", 1, 0, cell("A1", 1, "one")),
            sheetRow("Hoja2", 1, 1, cell("A1", 1, "two")),
            sheetRow("Hoja3", 1, 1, cell("A1", 1, "three")),
        ).joinToString("\n")
        val excerpt = ContextEvidenceExcerptSelector.select("Hoja1:Hoja2!A1", source)

        assertTrue(excerpt.cellReferences.isEmpty())
        assertFalse(excerpt.text.contains("Hoja2!A1=two"))
        assertTrue("SHEET_ORDER_METADATA_CONFLICT" in excerpt.incompleteReasons)
        assertTrue("THREE_D_SHEET_ORDER_CONFLICT" in excerpt.incompleteReasons)
        assertTrue(excerpt.unresolvedCellReferences.isNotEmpty())
    }

    @Test fun unrelatedSheetOrderConflictDoesNotWarnForSimpleCellLookup() {
        val source = listOf(
            sheetRow("Hoja1", 1, 0, cell("A1", 1, "one")),
            sheetRow("Hoja2", 1, 1, cell("A1", 1, "two")),
            sheetRow("Hoja3", 1, 1, cell("A1", 1, "three")),
        ).joinToString("\n")
        val excerpt = ContextEvidenceExcerptSelector.select("Hoja1!A1", source)

        assertEquals(listOf("Hoja1!A1"), excerpt.cellReferences)
        assertTrue(excerpt.text.contains("Hoja1!A1=one"))
        assertTrue(excerpt.incompleteReasons.isEmpty())
    }

    @Test fun missingQualifiedSheetAndMissingCellNeverFallBackToHomonymousAddress() {
        val source = listOf(
            sheetRow("Hoja1", 1, 0, cell("A1", 1, "999"), cell("A3", 1, "4", "'Hoja ausente'!A1")),
        ).joinToString("\n")
        val formula = ContextEvidenceExcerptSelector.select("Hoja1!A3", source)
        val directMissing = ContextEvidenceExcerptSelector.select("'Hoja1'!B4", source)

        assertEquals(listOf("Hoja1!A3"), formula.cellReferences)
        assertTrue(formula.missingCellReferences.contains("'Hoja ausente'!A1"))
        assertTrue("SHEET_NOT_FOUND" in formula.incompleteReasons)
        assertTrue(directMissing.missingCellReferences.contains("Hoja1!B4"))
        assertTrue(directMissing.incompleteReasons.contains("MISSING_REFERENCED_CELLS"))
    }

    @Test fun unqualifiedQueryWithAddressOnMultipleSheetsIsReportedAmbiguousWithoutValues() {
        val source = listOf(
            sheetRow("Hoja1", 1, 0, cell("A1", 1, "one")),
            sheetRow("Hoja2", 1, 1, cell("A1", 1, "two")),
        ).joinToString("\n")
        val excerpt = ContextEvidenceExcerptSelector.select("Explica A1", source)

        assertTrue(excerpt.text.isEmpty())
        assertTrue(excerpt.cellReferences.isEmpty())
        assertEquals(listOf("Hoja1!A1", "Hoja2!A1"), excerpt.ambiguousCellReferences)
        assertTrue("AMBIGUOUS_UNQUALIFIED_REFERENCE" in excerpt.incompleteReasons)
    }

    @Test fun functionLiteralExternalAndDynamicReferencesNeverBecomeLocalDependencies() {
        val formulas = listOf(
            "LOG10(\"A1\")" to null,
            "\"A1\"" to null,
            "'[Book.xlsx]Sheet1'!A1" to "EXTERNAL_WORKBOOK_REFERENCE_UNRESOLVED",
            "INDIRECT(\"A1\")" to "DYNAMIC_REFERENCE_UNRESOLVED",
            "TaxRate" to "UNSUPPORTED_DEFINED_NAME_OR_REFERENCE",
            "Table1[Sales]" to "STRUCTURED_REFERENCE_UNRESOLVED",
        )
        formulas.forEach { (formula, expectedReason) ->
            val source = listOf(
                sheetRow("Hoja1", 1, 0, cell("A1", 1, "999")),
                sheetRow("Hoja1", 3, 0, cell("A3", 1, "4", formula)),
            ).joinToString("\n")
            val excerpt = ContextEvidenceExcerptSelector.select("Explica Hoja1!A3", source)

            assertFalse("formula=$formula", "Hoja1!A1" in excerpt.cellReferences)
            assertTrue("formula=$formula", expectedReason == null || expectedReason in excerpt.incompleteReasons)
        }
    }

    @Test fun rectangularFormulaRangeIncludesAvailableRowAndColumnCells() {
        val source = listOf(
            row(1, cell("A1", 1, "1"), cell("B1", 2, "2")),
            row(2, cell("A2", 1, "3"), cell("B2", 2, "4")),
            row(3, cell("C3", 3, "10", "SUM(A1:B2)")),
        ).joinToString("\n")

        val excerpt = ContextEvidenceExcerptSelector.select("Explica C3", source)

        assertEquals(listOf("C3", "A1", "B1", "A2", "B2"), excerpt.cellAddresses)
        listOf("A1=1", "B1=2", "A2=3", "B2=4", "C3=10").forEach { assertTrue(excerpt.text.contains(it)) }
        assertTrue(excerpt.incompleteReasons.isEmpty())
    }

    @Test fun missingFormulaDependencyIsReportedWithoutInventingEvidence() {
        val source = listOf(
            row(1, cell("A1", 1, "1")),
            row(3, cell("A3", 1, "4", "A2-A1")),
            row(4, cell("A4", 1, "unrelated")),
        ).joinToString("\n")

        val excerpt = ContextEvidenceExcerptSelector.select("Explica A3", source)

        assertEquals(listOf("A3", "A1"), excerpt.cellAddresses)
        assertTrue(excerpt.text.contains("A3=4"))
        assertFalse(excerpt.text.contains("A4=unrelated"))
        assertEquals(listOf("MISSING_REFERENCED_CELLS"), excerpt.incompleteReasons)
        assertEquals(listOf("A2"), excerpt.missingCellAddresses)
    }

    @Test fun missingInteriorRangeCellIsReportedWithoutSelectingNeighboringCells() {
        val source = listOf(
            row(1, cell("A1", 1, "1")),
            row(3, cell("A3", 1, "4")),
            row(4, cell("A4", 1, "unrelated")),
        ).joinToString("\n")

        val excerpt = ContextEvidenceExcerptSelector.select("Suma A1:A3", source)

        assertEquals(listOf("A1", "A3"), excerpt.cellAddresses)
        assertTrue(excerpt.text.contains("A1=1"))
        assertTrue(excerpt.text.contains("A3=4"))
        assertFalse(excerpt.text.contains("A4=unrelated"))
        assertEquals(listOf("MISSING_REFERENCED_CELLS"), excerpt.incompleteReasons)
        assertEquals(listOf("A2"), excerpt.missingCellAddresses)
    }

    @Test fun noMatchingStructuredCellsReturnsEmptyEvidenceInsteadOfAnotherCell() {
        val source = listOf(row(1, cell("A1", 1, "unrelated")), row(2, cell("A2", 1, "also unrelated"))).joinToString("\n")

        val excerpt = ContextEvidenceExcerptSelector.select("Explain B4", source)

        assertTrue(excerpt.text.isEmpty())
        assertTrue(excerpt.cellAddresses.isEmpty())
        assertEquals(listOf("MISSING_REFERENCED_CELLS"), excerpt.incompleteReasons)
        assertEquals(listOf("B4"), excerpt.missingCellAddresses)
    }

    @Test fun cellReferenceAndByteBudgetsAreExplicitAndBounded() {
        val manyCells = (1..65).map { rowNumber -> row(rowNumber, cell("A$rowNumber", 1, "value-$rowNumber")) }.joinToString("\n")
        val referenceLimited = ContextEvidenceExcerptSelector.select("Suma A1:A65", manyCells)
        assertTrue(referenceLimited.cellAddresses.size <= 64)
        assertTrue("CELL_REFERENCE_LIMIT" in referenceLimited.incompleteReasons)
        assertTrue(utf8(referenceLimited.text) <= ContextEvidenceExcerptSelector.DEFAULT_MAX_UTF8_BYTES)

        val largeValues = (1..5).map { rowNumber -> row(rowNumber, cell("A$rowNumber", 1, "x".repeat(170))) }.joinToString("\n")
        val byteLimited = ContextEvidenceExcerptSelector.select("Suma A1:A5", largeValues)
        assertTrue(byteLimited.cellAddresses.isNotEmpty())
        assertTrue("CELL_EVIDENCE_BYTE_BUDGET" in byteLimited.incompleteReasons)
        assertTrue(utf8(byteLimited.text) <= ContextEvidenceExcerptSelector.DEFAULT_MAX_UTF8_BYTES)
    }

    @Test fun routineTextWindowingDoesNotCreateMaterialCellNotice() {
        val source = "x".repeat(200) + " TARGET " + "y".repeat(200)
        val excerpt = ContextEvidenceExcerptSelector.select("TARGET", source, 128)
        assertTrue(excerpt.text.contains("TARGET"))
        assertTrue(excerpt.incompleteReasons.isEmpty())
    }

    @Test fun multibyteQueryAnchorStaysInsideUtf8BudgetAndOffsets() {
        val source = "界".repeat(200) + " TARGET " + "界".repeat(200)
        for (budget in listOf(128, 480)) {
            val excerpt = ContextEvidenceExcerptSelector.select("TARGET", source, maxUtf8Bytes = budget)

            assertTrue("budget=$budget", excerpt.text.contains("TARGET"))
            assertTrue("budget=$budget", utf8(excerpt.text) <= budget)
            assertNotNull(excerpt.charStart)
            assertNotNull(excerpt.charEnd)
            val sourceSlice = source.substring(excerpt.charStart!!, excerpt.charEnd!!)
            assertTrue("offsets must refer to the selected anchor", sourceSlice.contains("TARGET"))
            assertFalse(sourceSlice.firstOrNull()?.let(Character::isLowSurrogate) == true)
            assertFalse(sourceSlice.lastOrNull()?.let(Character::isHighSurrogate) == true)
        }
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

    private fun row(row: Int, vararg cells: JSONObject) = JSONObject().put("sheet", "Hoja1").put("row", row)
        .put("cells", JSONArray().apply { cells.forEach(::put) }).toString()

    private fun sheetRow(sheet: String, row: Int, order: Int, vararg cells: JSONObject) = JSONObject()
        .put("sheet", sheet).put("sheetOrder", order).put("row", row)
        .put("cells", JSONArray().apply { cells.forEach(::put) }).toString()
}
