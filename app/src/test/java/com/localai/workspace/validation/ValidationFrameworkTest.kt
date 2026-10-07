package com.localai.workspace.validation

import com.google.gson.JsonObject
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** HOST_TESTED framework tests only. These fakes can never certify a device function. */
@OptIn(ExperimentalCoroutinesApi::class)
class ValidationFrameworkTest {
    @get:Rule val folder=TemporaryFolder()
    private fun report(id:String="test",phase:ValidationPhase=ValidationPhase.COMPLETED)=ValidationReport(runId=id,appVersion="0.2.1",device="host",androidApi=29,runtime="LiteRT-LM 0.17.1",startedAt="2026-10-06T00:00:00Z",suiteType=ValidationMode.QUICK,
        phase=phase,cleanupStatus="SUCCESS",model=ValidationModel("gemma","Gemma 4 E2B","abc",4096,256,0.3f,40,0.95f),
        tests=ValidationCatalog.cases.map { ValidationResult(it.id,it.name,it.expected,it.quick,it.critical,status=if(it.quick)ValidationStatus.PASS else ValidationStatus.NOT_TESTED) })
    private fun change(r:ValidationReport,id:String,status:ValidationStatus,critical:Boolean?=null)=r.copy(tests=r.tests.map { if(it.id==id)it.copy(status=status,critical=critical ?: it.critical) else it })
    @Test fun quickAndFullSelectNineAndThirtyThreeCases() { assertEquals(9,ValidationCatalog.selected(ValidationMode.QUICK).size);assertEquals(33,ValidationCatalog.selected(ValidationMode.FULL).size);assertEquals(33,ValidationCatalog.cases.map { it.id }.toSet().size) }
    @Test fun thinkingBudgetProbesDoNotRerunOtherSubsystems() {
        assertEquals(listOf("thinking_budget_256","thinking_budget_512","thinking_budget_1024"),
            ValidationCatalog.selected(ValidationMode.THINKING_BUDGETS).map { it.id })
    }
    @Test fun allDemonstratedCasesPass() { assertEquals("PASS",ValidationRules.summary(report()).overall) }
    @Test fun missingOptionalEncoderIsSkipNotFail() { assertEquals("PASS_WITH_SKIPS",ValidationRules.summary(change(report(),"semantic",ValidationStatus.SKIPPED,false)).overall) }
    @Test fun installedEncoderFailureCannotPass() { assertEquals("FAIL",ValidationRules.summary(change(report(),"semantic",ValidationStatus.FAIL)).overall) }
    @Test fun blockedCriticalNeverPasses() { assertEquals("BLOCKED",ValidationRules.summary(change(report(),"vision",ValidationStatus.BLOCKED)).overall) }
    @Test fun inconclusiveCriticalNeverPasses() { assertEquals("INCONCLUSIVE",ValidationRules.summary(change(report(),"calculator",ValidationStatus.INCONCLUSIVE)).overall) }
    @Test fun cleanupFailureNeverPasses() { assertEquals("INCONCLUSIVE",ValidationRules.summary(report().copy(cleanupStatus="FAILED")).overall) }
    @Test fun aSuiteWithNoSelectedTestsCannotPass() { assertEquals("INCONCLUSIVE",ValidationRules.summary(report().copy(tests=emptyList())).overall) }
    @Test fun unselectedCasesDoNotChangeQuickSummary() { assertEquals("PASS",ValidationRules.summary(change(report(),"python_network",ValidationStatus.NOT_TESTED)).overall) }
    @Test fun interruptedActiveCaseIsNotFailure() {
        val r=change(report(phase=ValidationPhase.RUNNING),"vision",ValidationStatus.NOT_TESTED).copy(activeTestId="vision:1")
        val recovered=ValidationRules.interrupted(r)
        assertEquals(ValidationPhase.INTERRUPTED,recovered.phase);assertEquals(ValidationStatus.INCONCLUSIVE,recovered.tests.first { it.id=="vision" }.status)
        assertEquals("INCONCLUSIVE",recovered.summary.overall)
    }
    @Test fun runningReportNeverClaimsPass() { assertEquals("INCONCLUSIVE",ValidationRules.summary(report(phase=ValidationPhase.RUNNING)).overall) }
    @Test fun storageRetainsTenRunsAndReplacesPartialAtomically() {
        val store=ValidationStore(folder.newFile("runs.json"));(0..14).forEach { store.put(report(it.toString())) }
        assertEquals((5..14).map { it.toString() },store.read().map { it.runId })
        store.put(change(report("14"),"text",ValidationStatus.FAIL));assertEquals(10,store.read().size);assertEquals("FAIL",store.read().last().summary.overall)
        assertFalse(java.io.File(folder.root,"runs.json.tmp").exists())
    }
    @Test fun corruptHistoryDoesNotInventResults() { val file=folder.newFile();file.writeText("invalid JSON");assertTrue(ValidationStore(file).read().isEmpty()) }
    @Test fun exportWhitelistsScalarMetricsAndOmitsPayloads() {
        val data=JsonObject().apply { addProperty("timeToFirstTokenMs",25);addProperty("prompt","SECRET");addProperty("audio","SECRET");addProperty("toolName","/private/path");addProperty("pythonStatus","SUCCESS");add("outputTokens",com.google.gson.JsonArray().apply { add("PRIVATE") }) }
        val r=report().copy(tests=report().tests.map { it.copy(metrics=data) })
        val json=ValidationStore(folder.newFile()).export(r)
        assertFalse(json.contains("SECRET"));assertFalse(json.contains("PRIVATE"));assertFalse(json.contains("/private/path"));assertTrue(json.contains("timeToFirstTokenMs"));assertTrue(json.contains("HOST_TESTED"))
    }
    @Test fun nullMetricsStayUnavailable() { val data=ValidationMetricCodec.runtime(com.localai.workspace.domain.model.RuntimeMetrics());assertTrue(data["timeToFirstTokenMs"].isJsonNull);assertTrue(data["visionInputPresent"].isJsonNull) }
    @Test fun jsonRoundTripRetainsStatusesAndConfiguration() { val store=ValidationStore(folder.newFile());val r=change(report(),"semantic",ValidationStatus.SKIPPED,false);store.put(r);val back=store.read().single();assertEquals(r.model,back.model);assertEquals(ValidationStatus.SKIPPED,back.tests.first { it.id=="semantic" }.status);assertEquals("PASS_WITH_SKIPS",back.summary.overall) }
    @Test fun comparisonRequiresSameModelConfigurationAndSuite() { val a=report();assertTrue(ValidationRules.compatible(a,a.copy(appVersion="0.2.2")));assertFalse(ValidationRules.compatible(a,a.copy(model=a.model!!.copy(context=8192))));assertFalse(ValidationRules.compatible(a,a.copy(suiteType=ValidationMode.FULL))) }
    @Test fun onlyFunctionalPassToFailIsReported() { val r=report();assertEquals(listOf("Vision: PASS → FAIL"),ValidationRules.regressions(r,change(r,"vision",ValidationStatus.FAIL)));assertTrue(ValidationRules.regressions(r,change(r,"vision",ValidationStatus.BLOCKED)).isEmpty()) }
    private class Backend(private val run:suspend (String)->ValidationOutcome):ValidationBackend {
        override val environment=EvidenceEnvironment.HOST_TESTED
        var closed=false
        override suspend fun execute(id:String)=run(id)
        override suspend fun close() { closed=true }
    }
    @Test fun coordinatorPersistsProgressAndHostEvidence() = runTest {
        val saved=mutableListOf<ValidationReport>();val backend=Backend { ValidationOutcome(ValidationStatus.PASS) }
        val initial=report(phase=ValidationPhase.RUNNING).copy(tests=report().tests.map { it.copy(status=ValidationStatus.NOT_TESTED) })
        val result=ValidationEngine(backend,persist={saved.add(it)}).run(initial)
        assertTrue(backend.closed);assertEquals(ValidationPhase.COMPLETED,result.phase);assertEquals("PASS",result.summary.overall)
        assertTrue(saved.any { it.activeTestId=="calculator:1" });assertTrue(result.tests.all { it.evidence==EvidenceEnvironment.HOST_TESTED });assertEquals(EvidenceEnvironment.HOST_TESTED,result.environment)
    }
    @Test fun timeoutIsBlockedAndStillCleansResources() = runTest {
        val backend=Backend { delay(1000);ValidationOutcome(ValidationStatus.PASS) }
        val initial=report(phase=ValidationPhase.RUNNING).copy(tests=listOf(report().tests.first().copy(status=ValidationStatus.NOT_TESTED)))
        val result=ValidationEngine(backend,10,persist={}).run(initial)
        assertTrue(backend.closed);assertEquals(ValidationStatus.BLOCKED,result.tests.single().status);assertEquals("CASE_TIMEOUT",result.tests.single().reasonCode)
    }
    @Test fun timeoutPreservesSafeThinkingSubcaseEvidence() = runTest {
        val backend=object:ValidationBackend {
            override val environment=EvidenceEnvironment.HOST_TESTED
            override suspend fun execute(id:String):ValidationOutcome { delay(1000);return ValidationOutcome(ValidationStatus.PASS) }
            override fun timeoutOutcome(id:String)=ValidationOutcome(ValidationStatus.BLOCKED,"THINKING_TIMEOUT",JsonObject().apply {
                addProperty("ON_REASONING.status","BLOCKED")
                addProperty("ON_REASONING.reasonCode","THINKING_TIMEOUT")
                addProperty("ON_REASONING.thoughtText","PRIVATE_REASONING_SENTINEL")
            })
            override suspend fun close()=Unit
        }
        val initial=report(phase=ValidationPhase.RUNNING).copy(tests=listOf(report().tests.first().copy(status=ValidationStatus.NOT_TESTED)))
        val result=ValidationEngine(backend,10,persist={}).run(initial)
        assertEquals("THINKING_TIMEOUT",result.tests.single().reasonCode)
        assertEquals("BLOCKED",result.tests.single().metrics.get("ON_REASONING.status").asString)
        assertFalse(result.tests.single().metrics.toString().contains("PRIVATE_REASONING_SENTINEL"))
    }
    @Test fun cancellationPersistsPartialAndClosesWorker() = runTest {
        val entered=CompletableDeferred<Unit>();val saved=mutableListOf<ValidationReport>()
        val backend=Backend { entered.complete(Unit);awaitCancellation() }
        val initial=report(phase=ValidationPhase.RUNNING).copy(tests=report().tests.map { it.copy(status=ValidationStatus.NOT_TESTED) })
        val job=launch { ValidationEngine(backend,persist={saved.add(it)}).run(initial) };entered.await();job.cancelAndJoin()
        assertTrue(backend.closed);assertEquals(ValidationPhase.CANCELLED,saved.last().phase);assertEquals(ValidationStatus.INCONCLUSIVE,saved.last().tests.first().status)
        assertEquals(ValidationStatus.NOT_TESTED,saved.last().tests[1].status)
    }
    @Test fun cleanupExceptionIsVisibleAndCannotCertifyPass() = runTest {
        val backend=object:ValidationBackend { override val environment=EvidenceEnvironment.HOST_TESTED;override suspend fun execute(id:String)=ValidationOutcome(ValidationStatus.PASS);override suspend fun close(){error("cleanup")} }
        val result=ValidationEngine(backend,persist={}).run(report(phase=ValidationPhase.RUNNING));assertEquals("FAILED",result.cleanupStatus);assertEquals("INCONCLUSIVE",result.summary.overall)
    }
    @Test fun evenAFakeClaimingDeviceEvidenceIsForcedToHostTested() = runTest {
        val fake=object:ValidationBackend {
            override val environment=EvidenceEnvironment.DEVICE_TESTED
            override suspend fun execute(id:String)=ValidationOutcome(ValidationStatus.PASS)
            override suspend fun close()=Unit
        }
        val result=ValidationEngine(fake,persist={}).run(report(phase=ValidationPhase.RUNNING))
        assertEquals(EvidenceEnvironment.HOST_TESTED,result.environment)
        assertTrue(result.tests.filter { it.selected }.all { it.evidence==EvidenceEnvironment.HOST_TESTED })
    }
}
