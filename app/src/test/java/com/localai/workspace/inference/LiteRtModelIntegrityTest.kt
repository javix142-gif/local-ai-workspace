package com.localai.workspace.inference

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.concurrent.CancellationException

class LiteRtModelIntegrityTest {
    @get:Rule val temporary = TemporaryFolder()

    private fun fixture(major: Int = 1): File {
        val file = temporary.newFile()
        val header = "LITERTLM".toByteArray() + ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(major).putInt(6).putInt(0).array()
        file.outputStream().use { it.write(header); it.write(ByteArray(8192) { 17 }) }
        return file
    }

    @Test fun validatesFormatVersionSizeAndActualSha256() {
        val file = fixture()
        val digest = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }
        assertEquals("1.6.0", LiteRtModelIntegrity.verify(file, file.length(), digest))
    }

    @Test fun detectsModificationAfterImportAndIncompleteFiles() {
        val file = fixture()
        assertEquals("MODEL_SIZE_MISMATCH", rejected { LiteRtModelIntegrity.verify(file, file.length() + 1, null) })
        assertEquals("MODEL_HASH_MISMATCH", rejected { LiteRtModelIntegrity.verify(file, file.length(), "0".repeat(64)) })
        val truncated = temporary.newFile().apply { outputStream().use { it.write("LITERTLM".toByteArray()) } }
        assertEquals("MODEL_HEADER_TRUNCATED", rejected { LiteRtModelIntegrity.verify(truncated, null, null) })
    }

    @Test fun rejectsCorruptionAndIncompatibleFormatMajor() {
        assertEquals("MODEL_FORMAT_VERSION_UNSUPPORTED", rejected { LiteRtModelIntegrity.verify(fixture(2), null, null) })
        val corrupt = fixture().apply { java.io.RandomAccessFile(this, "rw").use { it.writeByte(0) } }
        assertEquals("MODEL_MAGIC_INVALID", rejected { LiteRtModelIntegrity.verify(corrupt, null, null) })
    }

    @Test fun streamedHashValidationIsCancellable() {
        val file = fixture()
        assertThrows(CancellationException::class.java) {
            LiteRtModelIntegrity.verify(file, file.length(), "0".repeat(64)) { throw CancellationException() }
        }
    }

    @Test fun integrityProgressReportsActualBytesWithoutClaimingUnhashedFilesWereVerified() {
        val file = fixture()
        val digest = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }
        val progress = mutableListOf<Long>()
        LiteRtModelIntegrity.verify(file, file.length(), digest, onProgress = { bytes, total ->
            assertEquals(file.length(), total); progress.add(bytes)
        })
        assertEquals(0L, progress.first())
        assertEquals(file.length(), progress.last())
        assertTrue(progress.zipWithNext().all { (before, after) -> after > before })
        progress.clear()
        LiteRtModelIntegrity.verify(file, file.length(), null, onProgress = { bytes, _ -> progress.add(bytes) })
        assertTrue(progress.isEmpty())
    }

    private fun rejected(action: () -> Unit): String = try { action(); fail("Expected integrity failure"); "" }
    catch (error: ModelIntegrityException) { error.code }
}
