package com.localai.workspace.validation

import com.google.gson.JsonObject
import kotlinx.coroutines.*
import java.time.Instant

internal data class ValidationOutcome(val status: ValidationStatus, val reason: String? = null,
    val metrics: JsonObject = JsonObject(), val critical: Boolean? = null)
internal interface ValidationBackend {
    val environment: EvidenceEnvironment
    suspend fun execute(id: String): ValidationOutcome
    fun timeoutOutcome(id: String) = ValidationOutcome(ValidationStatus.BLOCKED,"CASE_TIMEOUT")
    suspend fun close()
    suspend fun measurement(): JsonObject = JsonObject()
}

/** Host-testable coordinator. A backend determines evidence, never the UI or a mock report. */
internal class ValidationEngine(private val backend: ValidationBackend, private val caseTimeoutMs: Long = 180_000,
    private val suiteTimeoutMs:Long=Long.MAX_VALUE,
    private val persist: suspend (ValidationReport) -> Unit) {
    // A fake cannot opt into DEVICE_TESTED by overriding a property.
    private val evidenceEnvironment=if(backend is ProductionValidationBackend)EvidenceEnvironment.DEVICE_TESTED else EvidenceEnvironment.HOST_TESTED
    suspend fun run(initial: ValidationReport): ValidationReport {
        val suiteStart=System.nanoTime()
        var report=initial.copy(environment=evidenceEnvironment, deviceBefore=backend.measurement())
        suspend fun publish() { report=report.copy(summary=ValidationRules.summary(report));persist(report) }
        try {
            publish()
            for(index in report.tests.indices) {
                val test=report.tests[index];if(!test.selected)continue
                val remaining=suiteTimeoutMs-(System.nanoTime()-suiteStart)/1_000_000
                if(remaining<=0) {
                    report=report.copy(tests=report.tests.map { if(it.selected && it.status==ValidationStatus.NOT_TESTED)it.copy(status=ValidationStatus.BLOCKED,reasonCode="SUITE_TIMEOUT",evidence=evidenceEnvironment) else it })
                    publish();break
                }
                report=report.copy(activeTestId=test.id+":"+test.iteration);publish()
                val start=System.nanoTime()
                val outcome=try { withTimeout(minOf(caseTimeoutMs,remaining)) { backend.execute(test.id) } }
                catch(_:TimeoutCancellationException) { backend.timeoutOutcome(test.id) }
                catch(cancel:CancellationException) { throw cancel }
                catch(error:Throwable) {
                    // Only these QA controls characterize failures. Preserve all other contracts.
                    val control=test.id in setOf("thinking_budget_256","zip_traversal")
                    val unavailable=error is java.io.FileNotFoundException || error is java.nio.file.AccessDeniedException
                    val reason=if(test.id=="zip_traversal" && error is java.util.zip.ZipException) "ZIP_CORRUPT"
                        else "EXECUTION_"+error.javaClass.simpleName.take(60)
                    ValidationOutcome(if(control && !unavailable)ValidationStatus.FAIL else ValidationStatus.BLOCKED,reason)
                }
                report=report.copy(tests=report.tests.toMutableList().also { it[index]=test.copy(status=outcome.status,
                    reasonCode=outcome.reason,durationMs=(System.nanoTime()-start)/1_000_000,
                    critical=outcome.critical ?: test.critical,metrics=ValidationMetricCodec.sanitize(outcome.metrics),evidence=evidenceEnvironment) }, activeTestId=null)
                publish()
            }
            report=report.copy(phase=ValidationPhase.COMPLETED)
        } catch(cancel:CancellationException) {
            report=report.copy(phase=ValidationPhase.CANCELLED,tests=report.tests.map { if(it.id+":"+it.iteration == report.activeTestId) it.copy(status=ValidationStatus.INCONCLUSIVE,reasonCode="CANCELLED",evidence=evidenceEnvironment) else it })
        } finally {
            withContext(NonCancellable) {
                val clean=runCatching { withTimeout(90_000) { backend.close() } }.isSuccess
                report=report.copy(completedAt=Instant.now().toString(),activeTestId=null,
                    deviceAfter=runCatching { backend.measurement() }.getOrDefault(JsonObject()),cleanupStatus=if(clean)"SUCCESS" else "FAILED")
                publish()
            }
        }
        return report
    }
}
