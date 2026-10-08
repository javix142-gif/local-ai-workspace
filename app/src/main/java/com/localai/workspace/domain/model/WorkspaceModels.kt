package com.localai.workspace.domain.model

enum class MessageRole { SYSTEM, USER, ASSISTANT, TOOL }

enum class MessageStatus { COMPLETE, GENERATING, CANCELED, FAILED }

enum class ModelFormat { GGUF, LITERT_LM, UNKNOWN }

enum class RuntimeType { LLAMA_CPP, LITERT_LM, UNKNOWN }

enum class ModelSourceType { LOCAL_IMPORT, HUGGING_FACE, APP_DOWNLOAD, OTHER }

enum class ModelCapability {
    TEXT,
    VISION,
    AUDIO,
    TOOL_CALLING,
    THINKING,
    EMBEDDING,
    IMAGE_GENERATION,
    MULTIMODAL,
    SPECULATIVE_DECODING,
}

enum class AcceleratorType { CPU, GPU, NPU }

enum class ModelImportStatus {
    INSPECTING,
    COMPATIBLE,
    COMPATIBLE_WARNING,
    UNSUPPORTED_ARCHITECTURE,
    UNSUPPORTED_FEATURE,
    INSUFFICIENT_STORAGE,
    CORRUPT,
    DUPLICATE,
    IMPORTING,
    READY,
    FAILED,
}

enum class ModelBundleStatus { COMPLETE, INCOMPLETE_MODEL_BUNDLE }

enum class ModelCompatibilityStatus {
    COMPATIBLE,
    COMPATIBLE_WITH_WARNING,
    UNSUPPORTED_ARCHITECTURE,
    UNSUPPORTED_FEATURE,
    INSUFFICIENT_MEMORY_RISK,
    CORRUPT_OR_INCOMPLETE,
    UNKNOWN,
}

data class ModelFileDescriptor(
    val role: String,
    val displayName: String,
    val absolutePath: String,
    val sizeBytes: Long,
    val sha256: String? = null,
)

data class ModelBundle(
    val primaryModel: ModelFileDescriptor,
    val auxiliaryFiles: List<ModelFileDescriptor> = emptyList(),
    val status: ModelBundleStatus = ModelBundleStatus.COMPLETE,
)

data class ModelDescriptor(
    val id: String,
    val displayName: String,
    val family: String? = null,
    val architecture: String? = null,
    val format: ModelFormat = ModelFormat.UNKNOWN,
    val runtime: RuntimeType = RuntimeType.UNKNOWN,
    val source: ModelSourceType = ModelSourceType.LOCAL_IMPORT,
    val localPath: String,
    val auxiliaryFiles: List<ModelFileDescriptor> = emptyList(),
    val sizeBytes: Long = 0,
    val quantization: String? = null,
    val parameterCount: Long? = null,
    val contextLength: Int? = null,
    val capabilities: Set<ModelCapability> = emptySet(),
    val accelerators: Set<AcceleratorType> = setOf(AcceleratorType.CPU),
    val preferredAccelerator: AcceleratorType = AcceleratorType.CPU,
    val metadata: Map<String, String> = emptyMap(),
    val compatibility: ModelCompatibilityStatus = ModelCompatibilityStatus.UNKNOWN,
    val compatibilityWarning: String? = null,
    val importStatus: ModelImportStatus = ModelImportStatus.READY,
    val bundleStatus: ModelBundleStatus = ModelBundleStatus.COMPLETE,
    val sourceRepository: String? = null,
    val originalFilename: String? = null,
    val backendVersion: String? = null,
    val importedAt: Long = 0L,
    val lastTestedAt: Long? = null,
    val lastMetrics: RuntimeMetrics? = null,
)

enum class SourceTrust {
    TRUSTED_SYSTEM,
    USER_INSTRUCTION,
    USER_CONTROLLED_MEMORY,
    UNTRUSTED_DOCUMENT,
    UNTRUSTED_WEB,
    UNTRUSTED_EMAIL,
    UNTRUSTED_TOOL_OUTPUT,
}

data class ModelSource(
    val absolutePath: String,
    val displayName: String,
    val sourceUri: String? = null,
    val format: ModelFormat = ModelFormat.UNKNOWN,
    val auxiliaryFiles: List<String> = emptyList(),
    val sourceType: ModelSourceType = ModelSourceType.LOCAL_IMPORT,
    val expectedSizeBytes: Long? = null,
    val sha256: String? = null,
)

data class ModelMetadata(
    val format: String,
    val architecture: String?,
    val quantization: String?,
    val parameterLabel: String?,
    val declaredContextLength: Int?,
    val tokenizerChatTemplate: String?,
    val fileSizeBytes: Long,
    val compatibility: ModelCompatibilityStatus,
    val warning: String? = null,
    val supportsTextGeneration: Boolean = true,
    val supportsVision: Boolean = false,
    val supportsToolCalling: Boolean = false,
    val supportsAudio: Boolean = false,
    val supportsThinking: Boolean = false,
    val supportsEmbeddings: Boolean = false,
    val supportsImageGeneration: Boolean = false,
    val supportsSpeculativeDecoding: Boolean = false,
    val capabilities: Set<ModelCapability> = emptySet(),
    val accelerators: Set<AcceleratorType> = setOf(AcceleratorType.CPU),
    val family: String? = null,
    val parameterCount: Long? = null,
    val recommendedContextLength: Int? = null,
    val backendVersion: String? = null,
    val capabilityEvidence: Map<String,String> = emptyMap(),
)

data class ModelLoadConfig(
    val model: ModelSource,
    val contextSize: Int,
    val threads: Int? = null,
    val maxOutputTokens: Int = 512,
    val temperature: Float = 0.3f,
    val topP: Float = 0.95f,
    val topK: Int = 40,
    val repeatPenalty: Float = 1.0f,
    val seed: Int = 0,
    val preferredAccelerator: AcceleratorType = AcceleratorType.CPU,
    val diagnosticMode: Boolean = false,
    val enableVision: Boolean = false,
    val nextHasImage: Boolean = false,
    val conversation: ConversationPrompt? = null,
    val warmupEnabled: Boolean = false,
    val speculativeEnabled: Boolean = false,
    val retryExperimental: Boolean = false,
    val enabledTools: Set<String> = emptySet(),
    val projectId: String? = null,
    val messageId: String? = null,
    val enableAudio: Boolean = false,
    val nextHasAudio: Boolean = false,
    // Internal, private self-test DB. Normal requests always use the default workspace.
    val validationScope: Boolean = false,
)

data class GenerationRequest(
    val prompt: String,
    val maxOutputTokens: Int = 512,
    val contextSize: Int = 4096,
    val temperature: Float = 0.3f,
    val topP: Float = 0.95f,
    val topK: Int = 40,
    val repeatPenalty: Float = 1.0f,
    val seed: Int = 0,
    val imagePath: String? = null,
    val conversation: ConversationPrompt? = null,
    val audioPath: String? = null,
)

sealed interface GenerationEvent {
    data class Token(val text: String, val receivedAtElapsedMs: Long? = null) : GenerationEvent
    data class Metrics(val metrics: RuntimeMetrics) : GenerationEvent
    data class StateChanged(val progress: com.localai.workspace.domain.inference.GenerationProgress) : GenerationEvent
    data class Error(val error: com.localai.workspace.domain.inference.GenerationError) : GenerationEvent
    data object Cancelled : GenerationEvent
    data object Completed : GenerationEvent
}

data class RuntimeCapabilities(
    val runtimeId: String,
    val textGeneration: Boolean,
    val embeddings: Boolean,
    val vision: Boolean,
    val toolCalling: Boolean,
    val promptCaching: Boolean,
    val audio: Boolean = false,
    val thinking: Boolean = false,
    val imageGeneration: Boolean = false,
    val speculativeDecoding: Boolean = false,
    val accelerators: Set<AcceleratorType> = setOf(AcceleratorType.CPU),
    val backendVersion: String? = null,
)

data class RuntimeMetrics(
    val modelLoadDurationMs: Long? = null,
    val promptTokens: Int? = null,
    val outputTokens: Int? = null,
    val timeToFirstTokenMs: Long? = null,
    val prefillTokensPerSecond: Double? = null,
    val decodeTokensPerSecond: Double? = null,
    val totalGenerationDurationMs: Long? = null,
    val contextSize: Int? = null,
    val backend: String? = null,
    val cancellationLatencyMs: Long? = null,
    val estimatedMemoryBytes: Long? = null,
    val errorCategory: String? = null,
    val sessionCreationDurationMs: Long? = null,
    val prefillDurationMs: Long? = null,
    val generationRequestedAt: Long? = null,
    val prefillStartedAt: Long? = null,
    // Not exposed by the Conversation API; do not invent a native timestamp.
    val prefillCompletedAt: Long? = null,
    val firstTokenAt: Long? = null,
    val outputChunks: Int? = null,
    val eosObserved: Boolean? = null,
    // Completion callback is distinct from EOS; LiteRT does not expose finish reason.
    val nativeCompletionObserved: Boolean? = null,
    val outputLimitReached: Boolean? = null,
    // Null for runtimes without an engine/session reuse contract. Warm turns have no loadMs.
    val engineReused: Boolean? = null,
    val sessionReused: Boolean? = null,
    // Includes integrity, initialization (if needed) and fresh conversation creation.
    val modelPreparationDurationMs: Long? = null,
    // True only after native stop acknowledged and the old conversation was released safely.
    val modelRetainedAfterStop: Boolean? = null,
    // UI request to first visible output, including retrieval, private-file preparation and load.
    val requestTimeToFirstTokenMs: Long? = null,
    val sendStartedAt: Long? = null,
    val inferenceGateWaitMs: Long? = null,
    val contextBuildMs: Long? = null,
    val skillRoutingMs: Long? = null,
    val memoryRetrievalMs: Long? = null,
    val sourceRetrievalMs: Long? = null,
    val conversationRetrievalMs: Long? = null,
    val endToEndTotalMs: Long? = null,
    val callbackToUiStateMs: Long? = null,
    val backendRequested: String? = null,
    val backendEffective: String? = null,
    val backendFallbackReason: String? = null,
    val speculativeSupported: Boolean? = null,
    val speculativeEnabled: Boolean? = null,
    // Successful engine initialization with the explicit flag, not measured token acceptance.
    val speculativeActive: Boolean? = null,
    val speculativeAcceptanceRate: Double? = null,
    val warmupDurationMs: Long? = null,
    val warmupStatus: String? = null,
    val conversationRebuildReason: String? = null,
    val cachedTokenCount: Int? = null,
    val workerPssBeforeBytes: Long? = null,
    val workerPssAfterBytes: Long? = null,
    val appPssBeforeBytes: Long? = null,
    val appPssAfterBytes: Long? = null,
    val availableRamBeforeBytes: Long? = null,
    val availableRamAfterBytes: Long? = null,
    val totalRamBytes: Long? = null,
    val thermalBefore: String? = null,
    val thermalAfter: String? = null,
    val finishState: String? = null,
    val failedBackendInitializationMs: Long? = null,
    val failedBackendPreparationMs: Long? = null,
    val backendFailureDetail: String? = null,
    val warmupPerformedThisPreparation: Boolean? = null,
    // Config passed to SDK, not a measured count of running OS threads.
    val configuredCpuThreads: Int? = null,
    val prefillDurationSource: String? = null,
    // Optional Compose observation; null when no active screen observes first content.
    val uiObservedTimeToFirstContentMs: Long? = null,
    // Privacy-safe callback telemetry: never contains channel text or private payloads.
    val rawCallbackCount: Int? = null,
    val thoughtCallbackCount: Int? = null,
    val finalCallbackCount: Int? = null,
    val unknownChannelCallbackCount: Int? = null,
    val thoughtCharacterCount: Int? = null,
    val finalCharacterCount: Int? = null,
    val visibleOutputLength: Int? = null,
    val firstCallbackMs: Long? = null,
    val timeToFirstThoughtMs: Long? = null,
    val timeToFirstFinalMs: Long? = null,
    val thinkingTokenBudget: Int? = null,
    val configuredMaxOutput: Int? = null,
    val effectiveMaxOutput: Int? = null,
    val thinkingRequested: String? = null,
    val thinkingPolicyDecision: String? = null,
    val thinkingEffective: Boolean? = null,
    val automaticToolCallingActive: Boolean? = null,
    val visionBackendInitialized: Boolean? = null,
    val audioBackendInitialized: Boolean? = null,
    val visionInputPresent: Boolean? = null,
    val audioInputPresent: Boolean? = null,
)

data class Evidence(
    val id: String,
    val segmentId: Long,
    val documentId: String,
    val documentTitle: String,
    val excerpt: String,
    val pageStart: Int?,
    val pageEnd: Int?,
    val charStart: Int?,
    val charEnd: Int?,
    val trust: SourceTrust = SourceTrust.UNTRUSTED_DOCUMENT,
    val retrievalScore: Double,
)

data class CitationCandidate(
    val evidenceId: String,
    val claimAnchor: String? = null,
)

data class ResolvedCitation(
    val evidence: Evidence,
    val claimAnchor: String?,
)
