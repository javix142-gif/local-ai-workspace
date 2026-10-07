package com.localai.workspace.domain.model

import com.localai.workspace.data.ModelEntity
import java.util.Base64

/** Compact, deterministic codecs for Room columns. Model binaries never enter the database. */
object ModelDescriptorCodec {
    fun encodeCapabilities(values: Set<ModelCapability>): String = values
        .map { it.name }
        .sorted()
        .joinToString(",")

    fun decodeCapabilities(raw: String?): Set<ModelCapability> = raw.orEmpty()
        .split(',', ';')
        .mapNotNull { value ->
            when (value.trim().lowercase()) {
                "text", "text-generation" -> ModelCapability.TEXT
                "vision" -> ModelCapability.VISION
                "audio" -> ModelCapability.AUDIO
                "tool-calling", "tool_calling" -> ModelCapability.TOOL_CALLING
                "thinking" -> ModelCapability.THINKING
                "embedding", "embeddings" -> ModelCapability.EMBEDDING
                "image-generation", "image_generation" -> ModelCapability.IMAGE_GENERATION
                "multimodal" -> ModelCapability.MULTIMODAL
                "speculative-decoding", "speculative_decoding" -> ModelCapability.SPECULATIVE_DECODING
                else -> runCatching { ModelCapability.valueOf(value.trim().uppercase()) }.getOrNull()
            }
        }
        .toSet()

    fun encodeAccelerators(values: Set<AcceleratorType>): String = values
        .map { it.name }
        .sorted()
        .joinToString(",")

    fun decodeAccelerators(raw: String?): Set<AcceleratorType> = raw.orEmpty()
        .split(',', ';')
        .mapNotNull { runCatching { AcceleratorType.valueOf(it.trim().uppercase()) }.getOrNull() }
        .toSet()
        .ifEmpty { setOf(AcceleratorType.CPU) }

    fun encodeFiles(values: List<ModelFileDescriptor>): String = values.joinToString(";") { file ->
        listOf(file.role, file.displayName, file.absolutePath, file.sizeBytes.toString(), file.sha256.orEmpty())
            .joinToString("|") { encode(it) }
    }

    fun decodeFiles(raw: String?): List<ModelFileDescriptor> = raw.orEmpty()
        .split(';')
        .filter { it.isNotBlank() }
        .mapNotNull { row ->
            val values = row.split('|').map(::decode)
            if (values.size < 5) return@mapNotNull null
            ModelFileDescriptor(
                role = values[0],
                displayName = values[1],
                absolutePath = values[2],
                sizeBytes = values[3].toLongOrNull() ?: 0L,
                sha256 = values[4].ifBlank { null },
            )
        }

    fun encodeMetadata(values: Map<String, String>): String = values.entries
        .sortedBy { it.key }
        .joinToString(";") { "${encode(it.key)}|${encode(it.value)}" }

    fun decodeMetadata(raw: String?): Map<String, String> = raw.orEmpty()
        .split(';')
        .filter { it.isNotBlank() }
        .mapNotNull { row ->
            val values = row.split('|').map(::decode)
            if (values.size == 2) values[0] to values[1] else null
        }
        .toMap()

    fun entityToDescriptor(model: ModelEntity): ModelDescriptor = ModelDescriptor(
        id = model.id,
        displayName = model.displayName,
        family = model.family,
        architecture = model.architecture,
        format = runCatching { ModelFormat.valueOf(model.format.uppercase()) }.getOrDefault(ModelFormat.UNKNOWN),
        runtime = when {
            model.runtimeId.contains("litert", ignoreCase = true) -> RuntimeType.LITERT_LM
            model.runtimeId.contains("llama", ignoreCase = true) -> RuntimeType.LLAMA_CPP
            else -> RuntimeType.UNKNOWN
        },
        source = runCatching { ModelSourceType.valueOf(model.sourceType) }.getOrDefault(ModelSourceType.LOCAL_IMPORT),
        localPath = model.localPath,
        auxiliaryFiles = decodeFiles(model.auxiliaryFiles),
        sizeBytes = model.fileSize + decodeFiles(model.auxiliaryFiles).sumOf { it.sizeBytes },
        quantization = model.quantization,
        parameterCount = model.parameterCount,
        contextLength = model.declaredContext,
        capabilities = decodeCapabilities(model.capabilities),
        accelerators = decodeAccelerators(model.accelerators),
        preferredAccelerator = runCatching { AcceleratorType.valueOf(model.preferredAccelerator) }
            .getOrDefault(AcceleratorType.CPU),
        metadata = decodeMetadata(model.metadataJson),
        compatibility = runCatching { ModelCompatibilityStatus.valueOf(model.compatibilityStatus) }
            .getOrDefault(ModelCompatibilityStatus.UNKNOWN),
        compatibilityWarning = model.compatibilityWarning,
        importStatus = runCatching { ModelImportStatus.valueOf(model.importStatus) }
            .getOrDefault(ModelImportStatus.READY),
        bundleStatus = runCatching { ModelBundleStatus.valueOf(model.bundleStatus) }
            .getOrDefault(ModelBundleStatus.COMPLETE),
        sourceRepository = model.sourceRepository,
        originalFilename = model.originalFilename,
        backendVersion = model.backendVersion,
        importedAt = model.importedAt,
        lastTestedAt = model.lastTestedAt,
    )

    private fun encode(value: String): String = Base64.getUrlEncoder().withoutPadding()
        .encodeToString(value.toByteArray(Charsets.UTF_8))

    private fun decode(value: String): String = runCatching {
        String(Base64.getUrlDecoder().decode(value), Charsets.UTF_8)
    }.getOrDefault(value)
}

fun ModelEntity.toDescriptor(): ModelDescriptor = ModelDescriptorCodec.entityToDescriptor(this)
