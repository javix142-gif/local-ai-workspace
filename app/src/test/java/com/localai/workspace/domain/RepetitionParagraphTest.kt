package com.localai.workspace.domain

import com.localai.workspace.domain.inference.RepetitionLoopDetector
import org.junit.Assert.*
import org.junit.Test

class RepetitionParagraphTest {
    private val repeated = "No es necesario preocuparse por la velocidad de los mensajes. El Local AI Workspace está diseñado para ser rápido y eficiente."

    @Test fun latestScreenshotInterleavesRepeatedParagraphsBeforeAnyThreeIdenticalCycles() {
        val first = "Los mensajes en el Local AI Workspace suelen tardar unos segundos en llegar."
        val middle = "Es importante recordar que la velocidad de los mensajes es un factor del sistema."
        val text = "$first\n\n$repeated\n\n$middle\n\n$repeated\n\nUna afirmación distinta interrumpe el bloque periódico.\n\n$repeated"
        for (size in listOf(1, 7, 32, 123)) {
            val detector = RepetitionLoopDetector()
            assertTrue("Third interleaved occurrence must stop even before final newline", text.chunked(size).any { detector.append(it) != null })
        }
    }

    @Test fun windowsCrLfAndWhitespaceVariationCannotHideTheSameParagraph() {
        val variant = repeated.replace(" ", "  ")
        val text = "$repeated\r\n\t\r\nOther words.\r\n\r\n$variant\r\n\r\nDifferent words.\r\n\r\n$repeated"
        assertTrue(RepetitionLoopDetector.containsLoop(text))
    }

    @Test fun twoQuotesAndUniqueParagraphsAreAllowedWhileShortCyclicLoopsStillStop() {
        assertFalse(RepetitionLoopDetector.containsLoop("$repeated\n\nExplanation.\n\n$repeated"))
        assertFalse(RepetitionLoopDetector.containsLoop("Gracias.\n\n".repeat(2)))
        assertTrue(RepetitionLoopDetector.containsLoop("Gracias.\n\n".repeat(30)))
        val huge = (1..1000).joinToString(" ") { "distinct-$it" }
        assertFalse(RepetitionLoopDetector.containsLoop("$huge\n\n$repeated\n\n$huge\n\n$repeated"))
    }
}
