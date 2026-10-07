package com.localai.workspace.ui

import com.localai.workspace.domain.inference.GenerationStage
import com.localai.workspace.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class UiPresentationTest {
    @Test fun reliableNamesArePresentationOnlyAndCustomNamesStayIntact() {
        assertEquals("Gemma 4 E2B", humanModelName("gemma-4-E2B-it.litertlm"))
        assertEquals("Qwen 3.5 2B", humanModelName("Qwen3.5-2B_int8.litertlm"))
        assertEquals("My Gemma experiment", humanModelName("My Gemma experiment"))
        assertEquals("unknown-model.gguf", humanModelName("unknown-model.gguf"))
    }
    @Test fun technicalProgressIsCompactWithoutChangingTheSourceStage() {
        assertEquals("Processing…", compactGenerationLabel(GenerationStage.PREFILLING))
        assertEquals("Warming…", compactGenerationLabel(GenerationStage.WARMING_MODEL))
        assertEquals("Stopping…", compactGenerationLabel(GenerationStage.GENERATING, true))
    }
    @Test fun audioOnlyAvailableWithDeclaredLiteRtSupport() {
        val model = descriptor(RuntimeType.LITERT_LM)
        assertEquals(CapabilityAvailability.AVAILABLE, capabilityAvailability(model, ModelCapability.TEXT))
        assertEquals(CapabilityAvailability.AVAILABLE, capabilityAvailability(model, ModelCapability.VISION))
        assertEquals(CapabilityAvailability.AVAILABLE, capabilityAvailability(model, ModelCapability.AUDIO))
        assertEquals(CapabilityAvailability.EXPERIMENTAL, capabilityAvailability(model, ModelCapability.SPECULATIVE_DECODING))
    }
    @Test fun unsupportedRuntimeAndBrokenImportAreNeverAvailable() {
        val gguf = descriptor(RuntimeType.LLAMA_CPP)
        assertEquals(CapabilityAvailability.MODEL_ONLY, capabilityAvailability(gguf, ModelCapability.VISION))
        assertEquals(CapabilityAvailability.UNAVAILABLE, capabilityAvailability(gguf, ModelCapability.THINKING))
        assertEquals(CapabilityAvailability.MODEL_ONLY, capabilityAvailability(gguf.copy(importStatus = ModelImportStatus.FAILED), ModelCapability.TEXT))
    }
    private fun descriptor(runtime: RuntimeType) = ModelDescriptor("model", "Model", localPath = "/model", runtime = runtime,
        capabilities = setOf(ModelCapability.TEXT, ModelCapability.VISION, ModelCapability.AUDIO, ModelCapability.SPECULATIVE_DECODING))
}
