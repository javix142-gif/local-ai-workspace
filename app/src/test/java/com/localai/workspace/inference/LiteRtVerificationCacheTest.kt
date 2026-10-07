package com.localai.workspace.inference

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.concurrent.CancellationException
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LiteRtVerificationCacheTest {
    @get:Rule val tmp = TemporaryFolder()
    private fun file() = tmp.newFile().apply {
        writeBytes("LITERTLM".toByteArray() + ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(1).putInt(6).putInt(0).array() + ByteArray(4096) { 42 })
    }
    private fun hash(file: File) = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }
    private fun stamp(file: File, inode: Long = 1, changed: Long = 1) = LiteRtVerificationCache.Fingerprint(
        file.canonicalPath, file.length(), file.lastModified(), inode, 1, changed)

    @Test fun repeatedConfigOrModelSwitchCanReuseActualPriorHashVerification() {
        val a = file(); val b = file(); val cache = LiteRtVerificationCache()
        val progress = mutableListOf<Long>()
        fun check(f: File) = cache.verify(f, f.length(), hash(f), { stamp(f) }, { n, _ -> progress.add(n) })
        check(a); assertFalse(cache.lastCheckReused); assertEquals(0L, progress.first())
        check(b); assertFalse(cache.lastCheckReused)
        progress.clear(); check(a); assertTrue(cache.lastCheckReused); assertEquals(listOf(a.length()), progress)
    }

    @Test fun replacingFileWithSameLengthAndRestoredMtimeStillRequiresHash() {
        val f = file(); val original = hash(f); val before = stamp(f); val cache = LiteRtVerificationCache()
        cache.verify(f, f.length(), original, { before })
        RandomAccessFile(f, "rw").use { it.seek(32); it.writeByte(0) }
        assertThrows(ModelIntegrityException::class.java) {
            cache.verify(f, f.length(), original, { before.copy(inode = 2) })
        }
        assertFalse(cache.lastCheckReused)
    }

    @Test fun cachedHeaderAndExpectedSizeAreAlwaysRechecked() {
        val f = file(); val h = hash(f); val original = stamp(f); val cache = LiteRtVerificationCache()
        cache.verify(f, f.length(), h, { original })
        assertThrows(ModelIntegrityException::class.java) { cache.verify(f, f.length() + 1, h, { original }) }
        RandomAccessFile(f, "rw").use { it.writeByte(0) }
        assertThrows(ModelIntegrityException::class.java) { cache.verify(f, f.length(), h, { original }) }
    }

    @Test fun changedHashAndChangedCtimeNeverReuseOldReceipt() {
        val f = file(); val h = hash(f); val initial = stamp(f); val cache = LiteRtVerificationCache()
        cache.verify(f, f.length(), h, { initial })
        assertThrows(ModelIntegrityException::class.java) { cache.verify(f, f.length(), "0".repeat(64), { initial }) }
        cache.verify(f, f.length(), h, { initial })
        cache.verify(f, f.length(), h, { initial.copy(changedSeconds = 2) })
        assertFalse(cache.lastCheckReused)
    }

    @Test fun missingStatOrMissingHashCannotProduceVerificationReuseClaim() {
        val f = file(); val cache = LiteRtVerificationCache(); val h = hash(f)
        repeat(2) { cache.verify(f, f.length(), h, { null }); assertFalse(cache.lastCheckReused) }
        repeat(2) { cache.verify(f, f.length(), null, { stamp(f) }); assertFalse(cache.lastCheckReused) }
    }

    @Test fun changedFileDuringHashAndCancellationCannotCreateReceipt() {
        val f = file(); val cache = LiteRtVerificationCache(); val h = hash(f); var revision = 1L
        assertThrows(ModelIntegrityException::class.java) {
            cache.verify(f, f.length(), h, { stamp(f, changed = revision++) })
        }
        assertThrows(CancellationException::class.java) {
            cache.verify(f, f.length(), h, { stamp(f) }, checkCancelled = { throw CancellationException() })
        }
        cache.verify(f, f.length(), h, { stamp(f) }); assertFalse(cache.lastCheckReused)
    }

    @Test fun boundedReceiptsEvictAndNewWorkerStartsWithoutTrust() {
        val a = file(); val b = file(); val cache = LiteRtVerificationCache(capacity = 1)
        cache.verify(a, a.length(), hash(a), { stamp(a) })
        cache.verify(b, b.length(), hash(b), { stamp(b) })
        cache.verify(a, a.length(), hash(a), { stamp(a) }); assertFalse(cache.lastCheckReused)
        val fresh = LiteRtVerificationCache(); fresh.verify(a, a.length(), hash(a), { stamp(a) })
        assertFalse(fresh.lastCheckReused)
    }
}
