package com.localai.workspace.validation

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.localai.workspace.data.DocumentParser
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29], application=Application::class)
class ZipTraversalControlTest {
    @get:Rule val temporary=TemporaryFolder()
    private fun fixture(name:String=ZipTraversalControl.UNSAFE):Pair<File,File> {
        val sandbox=File(temporary.newFolder(),"sandbox").apply { mkdirs() }
        val file=File(sandbox,"traversal.zip")
        ZipTraversalControl.create(file,name)
        return file to sandbox
    }
    private suspend fun production(file:File,sandbox:File) {
        DocumentParser(ValidationContext(ApplicationProvider.getApplicationContext(),File(sandbox,"cache")))
            .parse(file,"application/zip",file.name)
    }
    @Test fun fixtureOpensWithCentralDirectoryAndLiteralNames() {
        val (file,_)=fixture()
        ZipFile(file).use { zip ->
            assertEquals(listOf("safe.txt","../escape.txt"),zip.entries().asSequence().map { it.name }.toList())
            assertEquals("Synthetic safe fixture.",zip.getInputStream(zip.getEntry("safe.txt")).bufferedReader().use { it.readText() })
            assertNotNull(zip.getEntry("../escape.txt"))
        }
        assertTrue(ZipTraversalControl.verifyFixture(file))
        assertEquals(listOf("traversal.zip"),file.parentFile!!.list()!!.toList())
    }
    @Test fun actualProductionParserRejectsTraversalAndNoFileEscapes() = runBlocking {
        val (file,sandbox)=fixture()
        val result=ZipTraversalControl.run(file,sandbox) { production(it,sandbox) }
        assertEquals(ValidationStatus.PASS,result.status);assertNull(result.reason)
        for(key in listOf("fixtureValid","unsafeEntryDetected","unsafeEntryRejected","allWrittenPathsInsideSandbox"))assertTrue(key,result.metrics[key].asBoolean)
        assertFalse(result.metrics["escapedFileExists"].asBoolean)
        assertFalse(File(sandbox.parentFile,"escape.txt").exists())
        assertFalse(File(sandbox,"escape.txt").exists())
    }
    @Test fun nestedTraversalUsesActualProductionRejection() = runBlocking {
        val name="folder/../../escape2.txt"
        val (file,sandbox)=fixture(name)
        assertTrue(ZipTraversalControl.verifyFixture(file,name))
        val result=ZipTraversalControl.run(file,sandbox,name) { production(it,sandbox) }
        assertEquals(ValidationStatus.PASS,result.status)
        assertFalse(File(sandbox.parentFile,"escape2.txt").exists())
    }
    @Test fun corruptFixtureFailsBeforeProductionIsCalled() = runBlocking {
        val (file,sandbox)=fixture();file.writeText("not a zip")
        var invoked=false
        val result=ZipTraversalControl.run(file,sandbox) { invoked=true }
        assertFalse(invoked);assertEquals(ValidationStatus.FAIL,result.status)
        assertEquals("FIXTURE_INVALID",result.reason)
        assertEquals("ZIP_CORRUPT",result.metrics["observedCondition"].asString)
    }
    @Test fun truncatedCentralDirectoryFailsPrevalidation() = runBlocking {
        val (file,sandbox)=fixture();file.writeBytes(file.readBytes().dropLast(22).toByteArray())
        val result=ZipTraversalControl.run(file,sandbox) { fail("Invalid fixture reached ingestion") }
        assertEquals("FIXTURE_INVALID",result.reason)
    }
    @Test fun arbitraryZipExceptionNeverPasses() = runBlocking {
        val (file,sandbox)=fixture()
        val result=ZipTraversalControl.run(file,sandbox) { throw ZipException("bad central directory") }
        assertEquals(ValidationStatus.FAIL,result.status);assertEquals("ZIP_CORRUPT",result.reason)
    }
    @Test fun exactAndroidPathRejectionIsRecognizedWithoutChangingValidator() = runBlocking {
        val (file,sandbox)=fixture()
        // Host simulation tests classification only; actual Android protection requires a device.
        val result=ZipTraversalControl.run(file,sandbox) { throw ZipException("Invalid zip entry path: ../escape.txt") }
        assertEquals(ValidationStatus.PASS,result.status)
        assertEquals("ZIP_CORRUPT",ZipTraversalControl.rejectionCode(ZipException("Invalid zip entry path: other"),ZipTraversalControl.UNSAFE))
    }
    @Test fun outsideWriteFailsEvenWithExpectedSecurityException() = runBlocking {
        val (file,sandbox)=fixture()
        val result=ZipTraversalControl.run(file,sandbox) {
            File(sandbox.parentFile,"escape.txt").writeText("synthetic")
            throw IllegalArgumentException("Unsafe or duplicate archive path")
        }
        assertEquals(ValidationStatus.FAIL,result.status);assertEquals("ZIP_FILESYSTEM_ESCAPE",result.reason)
        assertTrue(result.metrics["escapedFileExists"].asBoolean)
        assertFalse(result.metrics["allWrittenPathsInsideSandbox"].asBoolean)
    }
    @Test fun anyNewOutsidePathFailsCanonicalCheck() = runBlocking {
        val (file,sandbox)=fixture()
        val result=ZipTraversalControl.run(file,sandbox) {
            File(sandbox.parentFile,"unexpected.txt").writeText("synthetic")
            throw IllegalArgumentException("Unsafe or duplicate archive path")
        }
        assertEquals(ValidationStatus.FAIL,result.status)
        assertFalse(result.metrics["allWrittenPathsInsideSandbox"].asBoolean)
    }
    @Test fun partialUnsafeFileWithinRootFails() = runBlocking {
        val (file,sandbox)=fixture()
        val result=ZipTraversalControl.run(file,sandbox) {
            File(sandbox,"escape.txt").writeText("partial")
            throw IllegalArgumentException("Unsafe or duplicate archive path")
        }
        assertEquals(ValidationStatus.FAIL,result.status)
    }
    @Test fun safeZipStillPassesActualProductionParser() = runBlocking {
        val sandbox=temporary.newFolder();val file=File(sandbox,"safe.zip")
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("safe.txt"));zip.write("SAFE_MARKER".toByteArray());zip.closeEntry()
        }
        val parsed=DocumentParser(ValidationContext(ApplicationProvider.getApplicationContext(),File(sandbox,"cache"))).parse(file,"application/zip",file.name)
        assertTrue(parsed.pages.any { it.text.contains("SAFE_MARKER") })
    }
    @Test fun differentLimitsAndIoAreNotTraversal() {
        val failures=mapOf("Suspicious compression ratio" to "ZIP_BOMB_LIMIT","Archive exceeds 100 entries" to "ZIP_TOO_MANY_FILES","Archive exceeds 32 MiB expanded" to "ZIP_SIZE_LIMIT")
        failures.forEach { (message,code)->assertEquals(code,ZipTraversalControl.rejectionCode(IllegalArgumentException(message),ZipTraversalControl.UNSAFE)) }
        assertEquals("ZIP_IO_ERROR",ZipTraversalControl.rejectionCode(java.io.IOException("synthetic"),ZipTraversalControl.UNSAFE))
    }
    @Test fun exportedZipEvidenceContainsNoPaths() = runBlocking {
        val (file,sandbox)=fixture()
        val result=ZipTraversalControl.run(file,sandbox) { production(it,sandbox) }
        val safe=ValidationMetricCodec.sanitize(result.metrics)
        assertTrue(safe["fixtureValid"].asBoolean)
        assertFalse(safe.toString().contains(sandbox.path));assertFalse(safe.toString().contains("escape.txt"))
    }
}
