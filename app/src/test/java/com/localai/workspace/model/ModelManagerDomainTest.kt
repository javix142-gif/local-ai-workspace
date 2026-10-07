package com.localai.workspace.model

import com.localai.workspace.data.HuggingFaceReferenceParser
import com.localai.workspace.data.ModelEntity
import com.localai.workspace.domain.inference.InferenceRuntime
import com.localai.workspace.domain.inference.InferenceRuntimeRegistry
import com.localai.workspace.domain.model.AcceleratorType
import com.localai.workspace.domain.model.GenerationEvent
import com.localai.workspace.domain.model.GenerationRequest
import com.localai.workspace.domain.model.ModelCapability
import com.localai.workspace.domain.model.ModelDescriptorCodec
import com.localai.workspace.domain.model.ModelFormat
import com.localai.workspace.domain.model.ModelFormatDetector
import com.localai.workspace.domain.model.ModelLoadConfig
import com.localai.workspace.domain.model.ModelMetadata
import com.localai.workspace.domain.model.ModelSource
import com.localai.workspace.domain.model.ModelSourceType
import com.localai.workspace.domain.model.RuntimeCapabilities
import com.localai.workspace.domain.model.RuntimeMetrics
import com.localai.workspace.domain.model.RuntimeType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ModelManagerDomainTest {
    @Test
    fun detectsSupportedFormatsAndRejectsBadMagic() {
        assertEquals(ModelFormat.GGUF, ModelFormatDetector.fromFilename("model.Q4_K_M.gguf"))
        assertEquals(ModelFormat.LITERT_LM, ModelFormatDetector.fromFilename("android.litertlm"))
        assertEquals(ModelFormat.UNKNOWN, ModelFormatDetector.fromFilename("model.safetensors"))
        assertTrue(ModelFormatDetector.hasGgufMagic("GGUF".toByteArray()))
        assertTrue(ModelFormatDetector.hasLiteRtLmMagic("LITERTLM\u0001".toByteArray()))
        assertFalse(ModelFormatDetector.hasGgufMagic("text".toByteArray()))
    }

    @Test
    fun sanitizesNamesAndParsesSafeHuggingFaceReferences() {
        assertEquals("model.gguf", ModelFormatDetector.sanitizeFilename("../../model.gguf", "fallback.gguf"))
        assertEquals("owner/repo", HuggingFaceReferenceParser.parseRepositoryId("https://huggingface.co/owner/repo/tree/main"))
        assertEquals("owner/repo", HuggingFaceReferenceParser.parseRepositoryId("owner/repo"))
        assertEquals(null, HuggingFaceReferenceParser.parseRepositoryId("https://example.com/owner/repo"))
        assertEquals(null, HuggingFaceReferenceParser.parseRepositoryId("../owner/repo"))
        assertTrue(HuggingFaceReferenceParser.resolveUrl("owner/repo", "nested/model Q4.gguf").startsWith("https://huggingface.co/owner/repo/resolve/main/"))
    }

    @Test
    fun descriptorCodecPreservesCapabilitiesFilesAndMetadata() {
        val source = ModelEntity(
            id = "id",
            displayName = "Vision",
            localPath = "/data/user/0/app/files/models/id/model.gguf",
            fileHash = "hash",
            fileSize = 100,
            format = ModelFormat.GGUF.name,
            runtimeId = "llama.cpp-android",
            compatibilityStatus = "COMPATIBLE",
            capabilities = "TEXT,VISION",
            importedAt = 1L,
            auxiliaryFiles = ModelDescriptorCodec.encodeFiles(
                listOf(com.localai.workspace.domain.model.ModelFileDescriptor("projector", "mmproj.gguf", "/data/user/0/app/files/models/id/mmproj.gguf", 20, "aux")),
            ),
            metadataJson = ModelDescriptorCodec.encodeMetadata(mapOf("key" to "value")),
        )
        val descriptor = ModelDescriptorCodec.entityToDescriptor(source)
        assertEquals(ModelFormat.GGUF, descriptor.format)
        assertEquals(RuntimeType.LLAMA_CPP, descriptor.runtime)
        assertTrue(ModelCapability.VISION in descriptor.capabilities)
        assertEquals("mmproj.gguf", descriptor.auxiliaryFiles.single().displayName)
        assertEquals("value", descriptor.metadata["key"])
    }

    @Test
    fun registryMapsRuntimeAndFallsBackToCpuOnlyWhenAdvertised() = runBlocking {
        val fake = FakeRuntime(RuntimeType.LITERT_LM, ModelFormat.LITERT_LM, setOf(AcceleratorType.CPU))
        val registry = InferenceRuntimeRegistry(listOf(fake))
        val descriptor = ModelDescriptorCodec.entityToDescriptor(
            ModelEntity("id", "m", "/tmp/m.litertlm", fileHash = "h", fileSize = 1, format = "LITERT_LM", runtimeId = "litert-lm-android", compatibilityStatus = "COMPATIBLE", importedAt = 1L),
        )
        assertEquals(RuntimeType.LITERT_LM, registry.runtimeFor(descriptor).runtimeType)
        assertEquals(AcceleratorType.CPU, registry.loadWithFallback(descriptor, ModelLoadConfig(ModelSource("/tmp/m.litertlm", "m", format = ModelFormat.LITERT_LM), 1024)))
    }

    @Test
    fun cancelledLoadMustNeverStartCpuFallback() {
        var attempts = 0
        val fake = FakeRuntime(RuntimeType.LITERT_LM, ModelFormat.LITERT_LM, setOf(AcceleratorType.CPU, AcceleratorType.GPU)) {
            attempts++
            throw CancellationException("cancelled native load")
        }
        val registry = InferenceRuntimeRegistry(listOf(fake))
        val descriptor = com.localai.workspace.domain.model.ModelDescriptor(
            "id", "m", format = ModelFormat.LITERT_LM, runtime = RuntimeType.LITERT_LM, localPath = "/tmp/m.litertlm",
        )
        org.junit.Assert.assertThrows(CancellationException::class.java) {
            runBlocking {
                registry.loadWithFallback(descriptor, ModelLoadConfig(ModelSource(descriptor.localPath, "m"), 1024, preferredAccelerator = AcceleratorType.GPU))
            }
        }
        assertEquals(1, attempts)
    }

    private class FakeRuntime(
        override val runtimeType: RuntimeType,
        private val format: ModelFormat,
        private val accelerators: Set<AcceleratorType>,
        private val onLoad: () -> Unit = {},
    ) : InferenceRuntime {
        override val runtimeId: String = "fake"
        override fun supports(format: ModelFormat) = this.format == format
        override fun availableAccelerators() = accelerators
        override suspend fun inspectModel(source: ModelSource) = ModelMetadata(format.name, null, null, null, null, null, 0, com.localai.workspace.domain.model.ModelCompatibilityStatus.COMPATIBLE)
        override suspend fun load(config: ModelLoadConfig) = onLoad()
        override fun generate(request: GenerationRequest): Flow<GenerationEvent> = emptyFlow()
        override fun cancelGeneration() = Unit
        override suspend fun unload() = Unit
        override fun capabilities() = RuntimeCapabilities("fake", true, false, false, false, false)
        override fun metrics() = RuntimeMetrics()
    }
}
