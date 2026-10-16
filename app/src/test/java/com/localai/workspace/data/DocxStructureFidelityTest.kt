package com.localai.workspace.data

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import kotlinx.coroutines.runBlocking

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class DocxStructureFidelityTest {
    private fun fixture(): File {
        var directory: File? = File(System.getProperty("user.dir") ?: ".").canonicalFile
        while (directory != null) {
            val candidate = File(directory, "docs/validation/master-program/05-docx-structure/fixtures/structure-v1.docx")
            if (candidate.isFile) return candidate
            directory = directory.parentFile
        }
        error("Synthetic DOCX structure fixture not found")
    }

    @Test fun headingsTableLocationsAndExplicitPageBreaksRemainTraceable() = runBlocking {
        val file = fixture()
        val parsed = DocumentParser(RuntimeEnvironment.getApplication()).parse(
            file,
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            file.name,
        )
        assertEquals("The explicit page break should define two traceable page segments", 2, parsed.pages.size)
        val first = parsed.pages[0]
        val second = parsed.pages[1]
        assertEquals(1, first.pageNumber)
        assertEquals(2, second.pageNumber)
        assertTrue(first.text.contains("[DOCX paragraph=1 heading=Heading1]"))
        assertTrue(first.text.contains("Informe anual"))
        assertTrue(first.text.contains("Introducción del documento."))
        assertTrue(first.text.contains("[DOCX table=1 row=1 cell=1]"))
        assertTrue(first.text.contains("[DOCX table=1 row=2 cell=1]"))
        assertTrue(first.text.contains("Álvaro"))
        assertTrue(first.text.contains("Antes del salto."))
        assertTrue(second.text.contains("Después del salto."))
        val chunks = TextChunker(maxCharacters = 1800, overlapCharacters = 0).chunk(parsed)
        assertTrue(chunks.any { it.pageStart == 1 && it.text.contains("Álvaro") })
        assertTrue(chunks.any { it.pageStart == 2 && it.text.contains("Después del salto.") })
    }
}
