package com.localai.workspace.validation

import android.os.Build
import com.localai.workspace.AppGraph
import com.localai.workspace.BuildConfig
import com.localai.workspace.data.ApplicationModelPreparation
import com.localai.workspace.domain.model.RuntimeType
import com.localai.workspace.domain.model.toDescriptor
import com.localai.workspace.inference.LiteRtIpc
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File
import java.time.Instant
import java.util.UUID

/** App-owned developer operation; navigation never cancels a run. */
class DeviceValidation(private val graph:AppGraph, private val scope:CoroutineScope) {
    private val store=ValidationStore(File(graph.contextForMeasurements.filesDir,"device-validation/reports.json"))
    val current=MutableStateFlow<ValidationReport?>(null)
    val history=MutableStateFlow<List<ValidationReport>>(emptyList())
    val running=MutableStateFlow(false)
    val initialized=MutableStateFlow(false)
    val status=MutableStateFlow("Preparing validation history")
    private var job:Job?=null
    init { scope.launch {
        try {
            withContext(Dispatchers.IO) {
                var saved=store.read()
                saved.filter { it.phase==ValidationPhase.RUNNING }.forEach { store.put(ValidationRules.interrupted(it)) }
                ValidationResources.recover(graph.contextForMeasurements)
                saved=store.read();history.value=saved;current.value=saved.lastOrNull()
            }
            status.value=if(current.value?.phase==ValidationPhase.INTERRUPTED)"Interrupted run · restart safely or discard" else "Ready · synthetic private fixtures only"
        } catch(_:Exception) { status.value="History recovery failed · validation blocked" }
        finally { initialized.value=!status.value.startsWith("History recovery failed") }
    } }
    fun start(mode:ValidationMode,iterations:Int=1,previousRunId:String?=null) {
        if(running.value || !initialized.value || graph.validationBusy.value)return
        if(graph.performance.running.value || graph.chatSessions.activities.value.any { it.generating || it.importing } || graph.chatSessions.hasGeneration) {
            status.value="Finish the active chat/import/benchmark before validation";return
        }
        running.value=true;graph.validationBusy.value=true;status.value="Preparing isolated validation"
        job=scope.launch {
            var backend:ProductionValidationBackend?=null
            var engineStarted=false
            val runId=UUID.randomUUID().toString()
            val count=if(mode==ValidationMode.QUICK)1 else iterations.coerceIn(1,3)
            val tests=(1..count).flatMap { iteration->ValidationCatalog.cases.map { case->
                ValidationResult(case.id,case.name,case.expected,case.id in ValidationCatalog.selected(mode).map { it.id },case.critical,iteration) } }
            var report=ValidationReport(runId=runId,appVersion=BuildConfig.VERSION_NAME,
                device=Build.MANUFACTURER+" "+Build.MODEL,androidApi=Build.VERSION.SDK_INT,runtime="LiteRT-LM "+LiteRtIpc.VERSION,
                startedAt=Instant.now().toString(),suiteType=mode,iterations=count,tests=tests,previousRunId=previousRunId)
            suspend fun publish(value:ValidationReport) {
                report=value
                history.value=withContext(Dispatchers.IO) { store.put(value) }
                current.value=history.value.lastOrNull { it.runId==runId }
                status.value=if(value.phase==ValidationPhase.RUNNING)value.suiteType.name+" Validation · Running · "+minOf(value.tests.count { it.selected && it.status!=ValidationStatus.NOT_TESTED }+(if(value.activeTestId!=null)1 else 0),value.tests.count { it.selected })+" / "+value.tests.count { it.selected }
                    else value.phase.name+" · "+ValidationRules.summary(value).overall
            }
            try {
                publish(report)
                graph.modelPreparation.pauseForBenchmark()
                com.localai.workspace.inference.LiteRtCapabilityRefresh.refresh(graph.database.modelDao(),graph.liteRtLmRuntime)
                val available=graph.workspace.allModels.first()
                val preferred=graph.modelPreparation.selectedModelId()
                val candidates=available.filter { m->ApplicationModelPreparation.isUsable(m) && m.toDescriptor().runtime==RuntimeType.LITERT_LM &&
                    (m.displayName+" "+m.originalFilename+" "+m.family).lowercase().let { "gemma" in it && ("e2b" in it || "e2-b" in it) } }
                val model=candidates.firstOrNull { it.id==preferred } ?: candidates.firstOrNull()
                val reason=when {
                    model==null->"GEMMA_E2B_NOT_INSTALLED"
                    graph.runtimes.runtimeFor(model.toDescriptor()) !is com.localai.workspace.inference.LiteRtLmInferenceRuntime->"REAL_LITERT_RUNTIME_REQUIRED"
                    !File(model.localPath).isFile->"MODEL_FILE_UNAVAILABLE"
                    else->null
                }
                if(reason!=null) {
                    publish(report.copy(phase=ValidationPhase.COMPLETED,completedAt=Instant.now().toString(),cleanupStatus="SUCCESS",
                        tests=report.tests.map { if(it.selected)it.copy(status=ValidationStatus.BLOCKED,reasonCode=reason) else it }))
                    return@launch
                }
                checkNotNull(model)
                report=report.copy(model=ValidationModel(model.id,"Gemma 4 E2B",model.fileHash,
                    model.configuredContext ?: model.declaredContext ?: 4096,model.maxOutputTokens,model.temperature,model.topK,model.topP,model.repeatPenalty,model.seed))
                backend=ProductionValidationBackend(graph,runId,model)
                engineStarted=true
                ValidationEngine(backend,suiteTimeoutMs=if(mode==ValidationMode.QUICK)6*60_000L else count*15*60_000L, persist=::publish).run(report)
            } catch(cancel:CancellationException) {
                withContext(NonCancellable) { publish(report.copy(phase=ValidationPhase.CANCELLED,completedAt=Instant.now().toString(),cleanupStatus="PENDING")) }
            } catch(error:Throwable) {
                withContext(NonCancellable) { publish(report.copy(phase=ValidationPhase.COMPLETED,completedAt=Instant.now().toString(),cleanupStatus="PENDING",
                    tests=report.tests.map { if(it.selected && it.status==ValidationStatus.NOT_TESTED)it.copy(status=ValidationStatus.BLOCKED,reasonCode="SETUP_"+error.javaClass.simpleName.take(60)) else it })) }
            } finally {
                withContext(NonCancellable) {
                    if(!engineStarted) {
                        val clean=runCatching { backend?.close();withContext(Dispatchers.IO) { ValidationResources.clean(graph.contextForMeasurements,runId);ValidationResources.deleteDatabase(graph.contextForMeasurements) } }.isSuccess
                        if(report.cleanupStatus=="PENDING") publish(report.copy(cleanupStatus=if(clean)"SUCCESS" else "FAILED"))
                    }
                    graph.modelPreparation.resumeAfterBenchmark()
                    graph.validationBusy.value=false;running.value=false
                }
            }
        }
    }
    fun cancel() { status.value="Cancelling · waiting for native cleanup";job?.cancel() }
    fun select(report:ValidationReport) { if(!running.value)current.value=report }
    fun restart(report:ValidationReport) { start(report.suiteType,report.iterations,report.runId) }
    fun discard(report:ValidationReport) { if(running.value)return;scope.launch { history.value=withContext(Dispatchers.IO) { store.remove(report.runId) };current.value=history.value.lastOrNull() } }
    fun export(report:ValidationReport)=store.export(report)
    fun summary(report:ValidationReport):String=buildString {
        appendLine("DEVICE VALIDATION · "+report.appVersion+" · "+report.suiteType)
        appendLine(report.device+" · Android API "+report.androidApi)
        appendLine((report.model?.name ?: "No model")+" · "+report.runtime+" · "+report.environment)
        appendLine(report.phase.name+" · "+ValidationRules.summary(report).overall+" · cleanup "+report.cleanupStatus)
        ValidationRules.summary(report).counts.forEach { (key,value)->appendLine(key+": "+value) }
    }
}
