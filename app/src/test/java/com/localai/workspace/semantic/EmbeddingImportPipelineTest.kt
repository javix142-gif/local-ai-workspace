package com.localai.workspace.semantic

import com.arm.aichat.EmbeddingNativeException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.security.MessageDigest

class EmbeddingImportPipelineTest {
    private val bytes=ByteArray(64) { it.toByte() }
    private val hash=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private class Encoder(override val dimension:Int=768,override val architecture:String="gemma-embedding",
        val vector:FloatArray=FloatArray(768).apply { this[0]=1f }):ImportEncoder {
        var closed=false
        override fun embed(text:String)=vector
        override fun close(){closed=true}
    }
    private fun run(data:ByteArray=bytes,size:Long?=data.size.toLong(),encoder:Encoder=Encoder(),
        expected:String=hash,open:()->java.io.InputStream?={ByteArrayInputStream(data)},
        loader:(File)->ImportEncoder={encoder},commit:(File,String)->Unit={_,_->}):EmbeddingDiagnostics = runBlocking {
        val file=File.createTempFile("embedding-import-", ".gguf")
        try { EmbeddingImportPipeline(expected).run(file,size,open,loader,commit,{}) }
        finally { file.delete() }
    }
    private fun failure(block:()->Unit)=assertThrows(EmbeddingImportFailure::class.java,block).diagnostics
    @Test fun successfulProbeCommitsOnlyAfterEncoderClosed() {
        val encoder=Encoder();var committed=false
        val d=run(encoder=encoder,commit={file,sha->assertTrue(encoder.closed);assertEquals(hash,sha);assertEquals(bytes.size.toLong(),file.length());committed=true})
        assertTrue(committed);assertEquals("IMPORTED",d.status);assertEquals(768,d.dimension);assertEquals("gemma-embedding",d.architecture)
    }
    @Test fun badShaNeverLoadsOrCommits() {
        val d=failure { run(expected="incorrect",loader={error("native loader must not be called")},commit={_,_->error("commit must not be called")}) }
        assertEquals(EmbeddingImportStage.HASH_VERIFY,d.stage);assertEquals("HASH_MISMATCH",d.code)
    }
    @Test fun truncatedFileIsReportedExplicitly() {
        assertEquals("TRUNCATED_FILE",failure { run(data=byteArrayOf(1)) }.code)
    }
    @Test fun sourceSizeMismatchPreventsNativeLoad() {
        assertEquals("COPY_SIZE_MISMATCH",failure { run(size=500L) }.code)
    }
    @Test fun sourceFailureWithholdsPrivateExceptionText() {
        val d=failure { run(open={throw IOException("/private/sensitive/source")}) }
        assertEquals(EmbeddingImportStage.SOURCE_OPEN,d.stage);assertEquals("SOURCE_OPEN_FAILED",d.code)
        assertFalse(d.text().contains("/private"))
    }
    @Test fun nullSourceRemainsSourceOpenFailure() {
        assertEquals("SOURCE_NOT_READABLE",failure { run(open={null}) }.code)
    }
    @Test fun copyFailureWithholdsRawMessage() {
        val d=failure { run(open={object:java.io.InputStream(){override fun read():Int=throw IOException("private path")}}) }
        assertEquals(EmbeddingImportStage.COPY_PRIVATE,d.stage);assertEquals("COPY_FAILED",d.code)
    }
    @Test fun nativeLinkFailureIsDistinct() {
        val d=failure { run(loader={throw UnsatisfiedLinkError("private path")}) }
        assertEquals(EmbeddingImportStage.NATIVE_LIBRARY_LOAD,d.stage);assertEquals("NATIVE_LIBRARY_UNAVAILABLE",d.code)
        assertFalse(d.text().contains("private path"))
    }
    @Test fun nativeOpenFailureKeepsRealStageAndCode() {
        val d=failure { run(loader={throw EmbeddingNativeException("GGUF_OPEN|GGUF_OPEN_FAILED|Native GGUF header could not be opened")}) }
        assertEquals(EmbeddingImportStage.GGUF_OPEN,d.stage);assertEquals("GGUF_OPEN_FAILED",d.nativeCode)
    }
    @Test fun nativeModelLoadFailurePreservesKnownArchitecture() {
        val d=failure { run(loader={throw EmbeddingNativeException("MODEL_LOAD|MODEL_LOAD_NULL|GGUF architecture=gemma-embedding; native model loader returned null")}) }
        assertEquals(EmbeddingImportStage.MODEL_LOAD,d.stage);assertEquals("gemma-embedding",d.architecture)
    }
    @Test fun wrongArchitectureClosesEncoderWithoutCommit() {
        val encoder=Encoder(architecture="llama");var commit=false
        val d=failure { run(encoder=encoder,commit={_,_->commit=true}) }
        assertEquals("WRONG_ARCHITECTURE",d.code);assertTrue(encoder.closed);assertFalse(commit)
    }
    @Test fun wrongNativeDimensionIsRejected() {
        val encoder=Encoder(dimension=256)
        assertEquals("WRONG_DIMENSION",failure { run(encoder=encoder) }.code);assertTrue(encoder.closed)
    }
    @Test fun wrongProbeDimensionIsRejected() {
        assertEquals("WRONG_DIMENSION",failure { run(encoder=Encoder(vector=FloatArray(256))) }.code)
    }
    @Test fun nonfiniteProbeIsRejected() {
        val vector=FloatArray(768).apply { this[0]=Float.NaN }
        assertEquals("NONFINITE_VECTOR",failure { run(encoder=Encoder(vector=vector)) }.code)
    }
    @Test fun unnormalizedProbeIsRejected() {
        assertEquals("INVALID_NORM",failure { run(encoder=Encoder(vector=FloatArray(768))) }.code)
    }
    @Test fun failedImportDoesNotReplacePreviouslyValidConfiguration() {
        var previous="old-config"
        failure { run(expected="bad",commit={_,_->previous="new-config"}) }
        assertEquals("old-config",previous)
    }
    @Test fun commitFailureHasOwnStage() {
        val d=failure { run(commit={_,_->throw IOException("private")}) }
        assertEquals(EmbeddingImportStage.CONFIG_COMMIT,d.stage);assertEquals("CONFIG_COMMIT_FAILED",d.code)
    }
    @Test fun cancellationIsNotMisreportedAsImportFailure() {
        assertThrows(CancellationException::class.java) { run(open={throw CancellationException()}) }
    }
}
