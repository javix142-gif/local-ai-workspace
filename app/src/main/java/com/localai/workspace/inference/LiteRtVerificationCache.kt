package com.localai.workspace.inference

import java.io.File

/** Bounded worker-lifetime receipts, never persisted or inferred from the filename. */
internal class LiteRtVerificationCache(private val capacity: Int = 8) {
    data class Fingerprint(val path: String, val size: Long, val modifiedMs: Long, val inode: Long,
        val device: Long, val changedSeconds: Long)
    private data class Receipt(val fingerprint: Fingerprint, val expectedSize: Long?, val hash: String, val version: String)
    private val receipts = linkedMapOf<String, Receipt>()
    var lastCheckReused: Boolean = false
        private set

    fun verify(file: File, expectedSize: Long?, hash: String?, fingerprint: () -> Fingerprint?,
        onProgress: (Long, Long) -> Unit = { _, _ -> }, checkCancelled: () -> Unit = {}): String {
        lastCheckReused = false
        checkCancelled()
        val before = fingerprint()
        val normalized = hash?.lowercase()
        val validHash = normalized?.matches(Regex("[a-f0-9]{64}")) == true
        val previous = receipts[file.canonicalPath]
        // Always recheck header/size/readability, including on a cache hit.
        val header = LiteRtModelIntegrity.verify(file, expectedSize, null)
        if (before != null && validHash && previous != null && previous.fingerprint == before &&
            previous.expectedSize == expectedSize && previous.hash == normalized && previous.version == header) {
            checkCancelled()
            if (fingerprint() == before) {
                lastCheckReused = true
                onProgress(file.length(), file.length())
                return header
            }
        }
        receipts.remove(file.canonicalPath)
        val version = LiteRtModelIntegrity.verify(file, expectedSize, hash, onProgress, checkCancelled)
        val after = fingerprint()
        if (before != null && after != before) throw ModelIntegrityException("MODEL_CHANGED_DURING_VERIFICATION")
        checkCancelled()
        if (before != null && validHash) {
            if (receipts.size >= capacity) receipts.remove(receipts.keys.first())
            receipts[file.canonicalPath] = Receipt(before, expectedSize, normalized!!, version)
        }
        return version
    }
}
