package com.localai.workspace.domain.inference

import com.localai.workspace.domain.model.GenerationEvent
import com.localai.workspace.domain.model.GenerationRequest
import com.localai.workspace.domain.model.AcceleratorType
import com.localai.workspace.domain.model.ModelLoadConfig
import com.localai.workspace.domain.model.ModelDescriptor
import com.localai.workspace.domain.model.ModelFormat
import com.localai.workspace.domain.model.ModelMetadata
import com.localai.workspace.domain.model.ModelSource
import com.localai.workspace.domain.model.RuntimeType
import com.localai.workspace.domain.model.RuntimeCapabilities
import com.localai.workspace.domain.model.RuntimeMetrics
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.CancellationException

interface InferenceRuntime {
    val runtimeType: RuntimeType
    val runtimeId: String

    fun supports(format: ModelFormat): Boolean

    fun availableAccelerators(): Set<AcceleratorType>

    suspend fun inspectModel(source: ModelSource): ModelMetadata

    suspend fun load(config: ModelLoadConfig)

    fun generate(request: GenerationRequest): Flow<GenerationEvent>

    fun cancelGeneration()

    suspend fun unload()

    /** Drop conversation/KV state. Backends without engine/session separation unload safely. */
    suspend fun resetConversation() = unload()
    /** Structural reason only; runtimes without this contract retain their safe reset. */
    suspend fun resetConversation(reason: String) = resetConversation()

    fun capabilities(): RuntimeCapabilities

    fun metrics(): RuntimeMetrics

    fun observeProgress(): Flow<GenerationProgress> = flowOf(GenerationProgress())
}

class InferenceRuntimeRegistry(runtimes: List<InferenceRuntime>) {
    private val byType = runtimes.associateBy { it.runtimeType }

    fun runtimeFor(model: ModelDescriptor): InferenceRuntime = byType[model.runtime]
        ?: throw RuntimeUnavailableException("No runtime is installed for ${model.format.name}")

    fun runtimeFor(format: ModelFormat): InferenceRuntime = byType.values.firstOrNull { it.supports(format) }
        ?: throw RuntimeUnavailableException("No runtime is installed for ${format.name}")

    fun supports(format: ModelFormat): Boolean = byType.values.any { it.supports(format) }

    fun installedRuntimeTypes(): Set<RuntimeType> = byType.keys

    suspend fun loadWithFallback(model: ModelDescriptor, config: ModelLoadConfig,
        runtime: InferenceRuntime = runtimeFor(model)): AcceleratorType {
        return try {
            runtime.load(config)
            runtime.metrics().backendEffective?.let { runCatching { AcceleratorType.valueOf(it) }.getOrNull() }
                ?: config.preferredAccelerator
        } catch (firstError: Throwable) {
            if (firstError is CancellationException) throw firstError
            if (config.preferredAccelerator == AcceleratorType.CPU ||
                AcceleratorType.CPU !in runtime.availableAccelerators()
            ) {
                throw firstError
            }
            runtime.load(config.copy(preferredAccelerator = AcceleratorType.CPU))
            AcceleratorType.CPU
        }
    }
}

class RuntimeUnavailableException(message: String, cause: Throwable? = null) : Exception(message, cause)
