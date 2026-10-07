package com.localai.workspace.validation

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.localai.workspace.domain.model.RuntimeMetrics
import java.io.File

enum class ValidationMode { QUICK, FULL, THINKING_BUDGETS }
enum class ValidationStatus { PASS, FAIL, SKIPPED, BLOCKED, INCONCLUSIVE, NOT_TESTED }
enum class ValidationPhase { RUNNING, COMPLETED, CANCELLED, INTERRUPTED }
enum class EvidenceEnvironment { HOST_TESTED, DEVICE_TESTED }
data class ValidationCase(val id: String, val name: String, val expected: String, val quick: Boolean = false, val critical: Boolean = false)
object ValidationCatalog {
    val cases = listOf(
        ValidationCase("text", "Text inference", "Native completion; Tokyo/Tokio", true, true),
        ValidationCase("calculator", "Calculator Tool", "Native calculator call + result + final answer 5781", true, true),
        ValidationCase("tools_off", "Tools Off", "No native tool calls or registry executions", true, true),
        ValidationCase("thinking", "Thinking Off / Auto / On", "Effective SDK mode, final-only output; answer 7", true, true),
        ValidationCase("thinking_budget_256", "Thinking unlimited / 256 exhaustion", "Expected exhaustion detected, or a verified final answer 7"),
        ValidationCase("thinking_budget_512", "Thinking ON / output 512", "Temporary output override, unbounded thinking: final 7 or precise exhaustion evidence"),
        ValidationCase("thinking_budget_1024", "Thinking ON / output 1024", "Temporary output override, unbounded thinking: final 7 or precise exhaustion evidence"),
        ValidationCase("xlsx", "XLSX parser / tool", "Sales B2=12, B3=4; native structured sum=16", true, true),
        ValidationCase("semantic", "EmbeddingGemma / RAG", "Real JNI 768D finite L2 vector; document A ranked first", true, true),
        ValidationCase("python", "Python / WebView", "Real CPython/WASM SUCCESS; stdout 2", true, true),
        ValidationCase("vision", "Vision", "Real image input/backend; final answer 42", true, true),
        ValidationCase("vision_lifecycle", "Vision lifecycle", "Composer consumed; next request has no image; new chat isolated"),
        ValidationCase("audio", "Audio", "Real audio input/backend; final transcription bicicleta", true, true),
        ValidationCase("audio_lifecycle", "Audio lifecycle", "Composer consumed; next request has no audio; new chat isolated"),
        ValidationCase("warmup", "Engine warm-up", "Runtime warm-up WARMED with measured duration"),
        ValidationCase("cold", "Cold text", "Fresh native engine and native completion"),
        ValidationCase("warm", "Warm / new conversation", "Loaded engine reused, fresh conversation"),
        ValidationCase("continuation", "Continuation / KV", "Code 3817 + real session reuse/cached token signal"),
        ValidationCase("files", "Files list / read", "Native files.list/read; fixture marker 12345"),
        ValidationCase("csv", "CSV structured tool", "Quoted delimiter; native numeric sum=16"),
        ValidationCase("zip", "Safe ZIP", "TXT/CSV listed and extracted through production parser"),
        ValidationCase("zip_traversal", "ZIP traversal", "Unsafe path rejected; no escaped file written"),
        ValidationCase("memory", "Semantic memory", "Approved scoped preference retrieved; irrelevant arithmetic filtered"),
        ValidationCase("lexical", "Lexical fallback", "No encoder; lexical retrieval finds fixture A"),
        ValidationCase("python_timeout", "Python timeout", "Timeout result from actual worker, not process crash"),
        ValidationCase("python_subprocess", "Python subprocess", "Explicit policy rejection"),
        ValidationCase("python_network", "Python network", "Explicit rejection/blocking"),
        ValidationCase("python_filesystem", "Python host filesystem", "Explicit policy rejection"),
        ValidationCase("python_output", "Python output limit", "OUTPUT_LIMIT from real Worker"),
        ValidationCase("rebuild", "Tools / Thinking rebuild", "Recorded conservative configuration/thinking rebuild"),
        ValidationCase("persistence", "Local persistence", "Independent Room connection reads fixture turn / vectors"),
        ValidationCase("errors", "Error isolation", "Invalid media rejected; no text-only fallback"),
        ValidationCase("device_metrics", "RAM / PSS / thermal", "Actual Android observations, null when unavailable"),
    )
    fun selected(mode: ValidationMode) = cases.filter { when(mode) {
        ValidationMode.QUICK -> it.quick
        ValidationMode.FULL -> true
        ValidationMode.THINKING_BUDGETS -> it.id.startsWith("thinking_budget_")
    } }
}
data class ValidationResult(
    val id: String, val name: String, val expected: String, val selected: Boolean,
    val critical: Boolean, val iteration: Int = 1, val status: ValidationStatus = ValidationStatus.NOT_TESTED,
    val durationMs: Long? = null, val reasonCode: String? = null,
    val metrics: JsonObject = JsonObject(), val evidence: EvidenceEnvironment = EvidenceEnvironment.HOST_TESTED,
)
data class ValidationModel(val id: String, val name: String, val hash: String?, val context: Int?, val maxOutput: Int?, val temperature: Float?, val topK: Int?, val topP: Float?, val repeatPenalty:Float?=null, val seed:Int?=null, val backend:String="CPU",val warmup:Boolean=true,val speculative:Boolean=false)
data class ValidationReport(
    val schemaVersion: Int = 1, val runId: String, val appVersion: String, val device: String,
    val androidApi: Int, val runtime: String, val startedAt: String, val completedAt: String? = null,
    val suiteType: ValidationMode, val iterations: Int = 1, val model: ValidationModel? = null,
    val environment: EvidenceEnvironment = EvidenceEnvironment.HOST_TESTED,
    val phase: ValidationPhase = ValidationPhase.RUNNING, val activeTestId: String? = null,
    val tests: List<ValidationResult>, val summary: ValidationSummary = ValidationSummary(),
    val previousRunId: String? = null, val restartPersistence: String = "MANUAL_RESTART_REQUIRED",
    val deviceBefore: JsonObject = JsonObject(), val deviceAfter: JsonObject = JsonObject(),
    val cleanupStatus: String = "PENDING",
)
data class ValidationSummary(val overall: String = "INCONCLUSIVE", val counts: Map<String, Int> = emptyMap())
object ValidationRules {
    fun summary(report: ValidationReport): ValidationSummary {
        val tests = report.tests.filter { it.selected }
        val counts = ValidationStatus.entries.associate { s -> s.name to tests.count { it.status == s } }
        val overall = when {
            tests.isEmpty() -> "INCONCLUSIVE"
            tests.any { it.status == ValidationStatus.FAIL } -> "FAIL"
            report.phase != ValidationPhase.COMPLETED -> "INCONCLUSIVE"
            report.cleanupStatus != "SUCCESS" -> "INCONCLUSIVE"
            tests.any { it.critical && it.status == ValidationStatus.BLOCKED } -> "BLOCKED"
            tests.any { it.critical && it.status in setOf(ValidationStatus.INCONCLUSIVE, ValidationStatus.NOT_TESTED, ValidationStatus.SKIPPED) } -> "INCONCLUSIVE"
            tests.any { it.status != ValidationStatus.PASS } -> "PASS_WITH_SKIPS"
            else -> "PASS"
        }
        return ValidationSummary(overall, counts)
    }
    fun interrupted(report: ValidationReport): ValidationReport = report.copy(phase = ValidationPhase.INTERRUPTED, activeTestId = null,
        tests = report.tests.map { if(it.id+":"+it.iteration == report.activeTestId && it.selected && it.status == ValidationStatus.NOT_TESTED) it.copy(status=ValidationStatus.INCONCLUSIVE, reasonCode="INTERRUPTED") else it })
        .let { it.copy(summary = summary(it)) }
    fun compatible(a: ValidationReport, b: ValidationReport) = a.schemaVersion == b.schemaVersion && a.device == b.device && a.androidApi == b.androidApi && a.model?.hash != null && a.model == b.model && a.runtime == b.runtime && a.suiteType == b.suiteType && a.environment == b.environment
    fun regressions(previous: ValidationReport, current: ValidationReport): List<String> = if(!compatible(previous,current)) emptyList() else current.tests.filter { c -> c.selected && c.status == ValidationStatus.FAIL && previous.tests.any { p -> p.id==c.id && p.iteration==c.iteration && p.status==ValidationStatus.PASS } }.map { it.name + ": PASS → FAIL" }
}

/** All exportable metrics are scalar and explicitly enumerated; no payload/path/CoT/vector. */
object ValidationMetricCodec {
    private val gson = GsonBuilder().serializeNulls().create()
    private val runtimeKeys = setOf("thinkingRequested","thinkingPolicyDecision","rawCallbackCount","thoughtCallbackCount","finalCallbackCount","unknownChannelCallbackCount","thoughtCharacterCount","finalCharacterCount","visibleOutputLength","firstCallbackMs","timeToFirstThoughtMs","timeToFirstFinalMs","thinkingTokenBudget","configuredMaxOutput","effectiveMaxOutput","maxOutputTokens","temperature","topP","topK","repeatPenalty","seed","prefillDurationSource","backendFallbackReason","speculativeActive","uiObservedTimeToFirstContentMs","modelLoadDurationMs", "modelPreparationDurationMs", "sessionCreationDurationMs", "warmupDurationMs", "warmupStatus", "warmupPerformedThisPreparation", "inferenceGateWaitMs", "contextBuildMs", "timeToFirstTokenMs", "requestTimeToFirstTokenMs", "prefillTokensPerSecond", "prefillDurationMs", "promptTokens", "decodeTokensPerSecond", "outputTokens", "totalGenerationDurationMs", "endToEndTotalMs", "callbackToUiStateMs", "engineReused", "sessionReused", "cachedTokenCount", "conversationRebuildReason", "backendRequested", "backendEffective", "speculativeEnabled", "configuredCpuThreads", "workerPssBeforeBytes", "workerPssAfterBytes", "appPssBeforeBytes", "appPssAfterBytes", "availableRamBeforeBytes", "availableRamAfterBytes", "totalRamBytes", "thermalBefore", "thermalAfter", "nativeCompletionObserved", "thinkingEffective", "automaticToolCallingActive", "visionBackendInitialized", "audioBackendInitialized", "visionInputPresent", "audioInputPresent", "finishState", "contextSize")
    private val controlKeys = setOf("expectedExhaustionObserved","observedCondition","executionStarted","fixtureValid","unsafeEntryDetected","unsafeEntryRejected","escapedFileExists","allWrittenPathsInsideSandbox")
    val allowed = runtimeKeys + controlKeys + setOf("embeddingImportStage","embeddingImportCode","embeddingNativeCode","semanticScoreA","semanticScoreB","coldPreparationEngineReused","thinkingOffEffective","thinkingAutoSimpleEffective","thinkingAutoReasonEffective","thinkingOnEffective","finalOnlyValidated","toolCallCount", "registryExecutions", "toolName", "toolDurationMs", "toolResultStatus", "embeddingDurationMs", "embeddingModelHash", "dimension", "vectorNorm", "finite", "semanticUsed", "topDocument", "retrievalDurationMs", "memoryRetrieved", "retrievalMode", "bootstrapMs", "executionMs", "pythonRequestMs", "pythonStatus", "audioPreparationMs", "audioDurationMs", "composerCleared", "newChatIsolated", "nextRequestMediaAbsent", "runtimeAccepted", "appPssBytes", "availableRamBytes", "thermalStatus", "parserValidated", "cleanupPending", "persistedRows", "outputLimitReached")
    private val thinkingSubcaseKeys = runtimeKeys + controlKeys + setOf("thinkingRequested","thinkingPolicyDecision","status","reasonCode","sourceReason","timeoutReached","cancelled","thoughtTokenCount","finalTokenCount","finishReason","outputLimitReached")
    private fun permitted(key:String):Boolean = key in allowed ||
        (key.substringBefore('.') in setOf("OFF_SIMPLE","AUTO_SIMPLE","AUTO_REASONING","ON_REASONING") && key.substringAfter('.', "") in thinkingSubcaseKeys)
    fun runtime(metrics: RuntimeMetrics): JsonObject = sanitize(gson.toJsonTree(metrics).asJsonObject)
    fun sanitize(input: JsonObject): JsonObject = JsonObject().apply {
        input.entrySet().filter { permitted(it.key) }.forEach { (key, value) ->
            if(value.isJsonNull) add(key,value)
            else if(value.isJsonPrimitive) {
                val p=value.asJsonPrimitive
                if(!p.isNumber || p.asDouble.isFinite()) {
                    if(!p.isString || (p.asString.length <= 160 && !p.asString.contains('/') && !p.asString.contains('\\'))) add(key,value)
                }
            }
        }
    }
}
class ValidationStore(private val file: File) {
    private val gson = GsonBuilder().serializeNulls().setPrettyPrinting().create()
    @Synchronized fun read(): List<ValidationReport> = runCatching {
        if(!file.exists() || file.length()>2_000_000) emptyList() else gson.fromJson(file.readText(),Array<ValidationReport>::class.java)?.toList().orEmpty().takeLast(10)
    }.getOrDefault(emptyList())
    @Synchronized fun put(report: ValidationReport): List<ValidationReport> {
        var saved=read().filterNot { it.runId == report.runId }.plus(report.copy(summary=ValidationRules.summary(report), tests=report.tests.map { it.copy(metrics=ValidationMetricCodec.sanitize(it.metrics)) },deviceBefore=ValidationMetricCodec.sanitize(report.deviceBefore),deviceAfter=ValidationMetricCodec.sanitize(report.deviceAfter))).takeLast(10)
        while(saved.size > 1 && gson.toJson(saved).toByteArray().size > 2_000_000) saved=saved.drop(1)
        write(saved);return saved
    }
    @Synchronized fun remove(id: String): List<ValidationReport> = read().filterNot { it.runId==id }.also(::write)
    private fun write(values: List<ValidationReport>) {
        file.parentFile!!.mkdirs(); val temp=File(file.parentFile,file.name+".tmp")
        temp.outputStream().use { output -> output.write(gson.toJson(values).toByteArray()); output.fd.sync() }
        check(temp.renameTo(file)) { "Validation report could not be saved" }
    }
    fun export(report: ValidationReport): String = gson.toJson(report.copy(summary=ValidationRules.summary(report),tests=report.tests.map { it.copy(metrics=ValidationMetricCodec.sanitize(it.metrics)) },deviceBefore=ValidationMetricCodec.sanitize(report.deviceBefore),deviceAfter=ValidationMetricCodec.sanitize(report.deviceAfter)))
}
