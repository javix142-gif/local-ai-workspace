package com.localai.workspace.ui

import com.localai.workspace.domain.inference.GenerationStage
import com.localai.workspace.domain.model.ModelCapability
import com.localai.workspace.domain.model.ModelDescriptor
import com.localai.workspace.domain.model.ModelImportStatus
import com.localai.workspace.domain.model.RuntimeType

/** Presentation only: never changes persisted names, capabilities or configuration. */
fun humanModelName(name: String): String = when {
    Regex("(?i)^gemma[-_ ]4[-_ ]E2B(?:[-_. ].*)?$").matches(name) -> "Gemma 4 E2B"
    Regex("(?i)^qwen3\\.5[-_ ]2B(?:[-_. ].*)?$").matches(name) -> "Qwen 3.5 2B"
    else -> name
}

fun runtimeDisplayName(runtime: RuntimeType): String = when (runtime) {
    RuntimeType.LITERT_LM -> "LiteRT-LM"
    RuntimeType.LLAMA_CPP -> "llama.cpp"
    else -> "Runtime unavailable"
}

fun compactGenerationLabel(stage: GenerationStage, cancelling: Boolean = false): String =
    if (cancelling) "Stopping…" else when (stage) {
        GenerationStage.LOADING_MODEL -> "Loading…"
        GenerationStage.WARMING_MODEL -> "Warming…"
        GenerationStage.GENERATING -> "Generating…"
        GenerationStage.ERROR -> "Could not finish"
        GenerationStage.CANCELLED -> "Stopped"
        GenerationStage.UNLOADING -> "Unloading…"
        GenerationStage.IDLE, GenerationStage.COMPLETED -> "Ready"
        else -> "Processing…"
    }

enum class CapabilityAvailability { AVAILABLE, MODEL_ONLY, EXPERIMENTAL, UNAVAILABLE }

fun capabilityAvailability(model: ModelDescriptor, capability: ModelCapability): CapabilityAvailability {
    if (capability !in model.capabilities) return CapabilityAvailability.UNAVAILABLE
    if (model.importStatus !in setOf(ModelImportStatus.READY, ModelImportStatus.COMPATIBLE_WARNING))
        return CapabilityAvailability.MODEL_ONLY
    return when (capability) {
        ModelCapability.TEXT -> if (model.runtime in setOf(RuntimeType.LITERT_LM, RuntimeType.LLAMA_CPP))
            CapabilityAvailability.AVAILABLE else CapabilityAvailability.MODEL_ONLY
        ModelCapability.TOOL_CALLING, ModelCapability.THINKING -> if (model.runtime == RuntimeType.LITERT_LM) CapabilityAvailability.AVAILABLE else CapabilityAvailability.MODEL_ONLY
        ModelCapability.VISION, ModelCapability.AUDIO -> if (model.runtime == RuntimeType.LITERT_LM)
            CapabilityAvailability.AVAILABLE else CapabilityAvailability.MODEL_ONLY
        ModelCapability.SPECULATIVE_DECODING -> if (model.runtime == RuntimeType.LITERT_LM)
            CapabilityAvailability.EXPERIMENTAL else CapabilityAvailability.MODEL_ONLY
        else -> CapabilityAvailability.MODEL_ONLY
    }
}
