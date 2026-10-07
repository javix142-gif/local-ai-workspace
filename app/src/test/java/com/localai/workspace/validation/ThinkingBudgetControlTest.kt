package com.localai.workspace.validation

import com.google.gson.JsonObject
import org.junit.Assert.*
import org.junit.Test

class ThinkingBudgetControlTest {
    private fun exhaustion() = JsonObject().apply {
        addProperty("thinkingRequested", "ON"); addProperty("thinkingEffective", true)
        addProperty("thinkingTokenBudget", -1); addProperty("configuredMaxOutput", 256)
        addProperty("effectiveMaxOutput", 256); addProperty("outputTokens", 256)
        addProperty("outputLimitReached", true); addProperty("nativeCompletionObserved", true)
        addProperty("rawCallbackCount", 253); addProperty("thoughtCallbackCount", 253)
        addProperty("thoughtCharacterCount", 924); addProperty("finalCallbackCount", 0)
        addProperty("finalCharacterCount", 0); addProperty("visibleOutputLength", 0)
        addProperty("unknownChannelCallbackCount", 0)
    }
    private fun evaluate(data: JsonObject = exhaustion(), reason: String? = ThinkingBudgetControl.EXHAUSTED,
        final: Boolean = false) = ThinkingBudgetControl.evaluate(data, reason, final)
    @Test fun exactPhysicalEvidenceIsExpectedPassWithoutHidingCondition() {
        val result=evaluate()
        assertEquals(ValidationStatus.PASS,result.status);assertNull(result.reason)
        assertTrue(result.metrics["expectedExhaustionObserved"].asBoolean)
        assertEquals(ThinkingBudgetControl.EXHAUSTED,result.metrics["observedCondition"].asString)
        assertEquals(253,result.metrics["thoughtCallbackCount"].asInt)
        assertEquals(0,result.metrics["visibleOutputLength"].asInt)
    }
    @Test fun noFinalWithoutOutputLimitFails() { assertEquals(ValidationStatus.FAIL,evaluate(exhaustion().apply { addProperty("outputLimitReached",false) }).status) }
    @Test fun shortOutputWithoutFinalFails() { assertEquals(ValidationStatus.FAIL,evaluate(exhaustion().apply { addProperty("outputTokens",200) }).status) }
    @Test fun ineffectiveThinkingFails() { assertEquals(ValidationStatus.FAIL,evaluate(exhaustion().apply { addProperty("thinkingEffective",false) }).status) }
    @Test fun boundedThinkingFails() { assertEquals(ValidationStatus.FAIL,evaluate(exhaustion().apply { addProperty("thinkingTokenBudget",128) }).status) }
    @Test fun missingThoughtFails() { assertEquals(ValidationStatus.FAIL,evaluate(exhaustion().apply { addProperty("thoughtCallbackCount",0) }).status) }
    @Test fun missingNativeCompletionFails() { assertEquals(ValidationStatus.FAIL,evaluate(exhaustion().apply { addProperty("nativeCompletionObserved",false) }).status) }
    @Test fun inconsistentCallbacksFail() { assertEquals(ValidationStatus.FAIL,evaluate(exhaustion().apply { addProperty("rawCallbackCount",1) }).status) }
    @Test fun unknownChannelFails() { assertEquals(ValidationStatus.FAIL,evaluate(exhaustion().apply { addProperty("unknownChannelCallbackCount",1) }).status) }
    @Test fun filteredFinalDoesNotCountAsExhaustion() { assertEquals(ValidationStatus.FAIL,evaluate(exhaustion().apply { addProperty("finalCharacterCount",1) }).status) }
    @Test fun missingMetricIsNotInvented() { assertEquals(ValidationStatus.FAIL,evaluate(exhaustion().apply { remove("outputTokens") }).status) }
    @Test fun unexpectedSdkErrorFailsEvenWhenOtherMetricsMatch() { assertEquals(ValidationStatus.FAIL,evaluate(reason="THINKING_SDK_ERROR").status) }
    @Test fun unexpectedReasonFails() { assertEquals(ValidationStatus.FAIL,evaluate(reason="THINKING_NO_FINAL_CHANNEL").status) }
    @Test fun timeoutAfterRequestStartedFails() {
        val result=ThinkingBudgetControl.timeout(JsonObject().apply { addProperty("ON_REASONING.executionStarted",true) })
        assertEquals(ValidationStatus.FAIL,result.status);assertEquals("THINKING_TIMEOUT",result.reason)
        assertFalse(result.metrics["expectedExhaustionObserved"].asBoolean)
    }
    @Test fun unavailableInfrastructureBeforeExecutionIsBlocked() {
        assertEquals(ValidationStatus.BLOCKED,ThinkingBudgetControl.timeout(JsonObject()).status)
        assertEquals(ValidationStatus.BLOCKED,ThinkingBudgetControl.evaluate(JsonObject(),"MODEL_UNAVAILABLE",false,false).status)
    }
    @Test fun correctFinalAt256IsValidAlternativeCharacterization() {
        val data=exhaustion().apply {
            addProperty("rawCallbackCount",254);addProperty("finalCallbackCount",1)
            addProperty("finalCharacterCount",1);addProperty("visibleOutputLength",1)
        }
        val result=evaluate(data,null,true)
        assertEquals(ValidationStatus.PASS,result.status);assertNull(result.reason)
        assertFalse(result.metrics["expectedExhaustionObserved"].asBoolean)
        assertEquals("CORRECT_FINAL_ANSWER",result.metrics["observedCondition"].asString)
        assertEquals(ValidationStatus.FAIL,evaluate(data,null,false).status)
    }
    @Test fun exportRetainsSafeEvidenceAndNoPrivatePayload() {
        val data=evaluate().metrics.apply { addProperty("prompt","PRIVATE");addProperty("thought","PRIVATE") }
        val safe=ValidationMetricCodec.sanitize(data)
        assertTrue(safe["expectedExhaustionObserved"].asBoolean)
        assertEquals(ThinkingBudgetControl.EXHAUSTED,safe["observedCondition"].asString)
        assertFalse(safe.toString().contains("PRIVATE"))
    }
    @Test fun catalogAndQuickMembershipRemainUnchanged() {
        assertEquals(33,ValidationCatalog.selected(ValidationMode.FULL).size)
        assertEquals(listOf("text","calculator","tools_off","thinking","xlsx","semantic","python","vision","audio"),ValidationCatalog.selected(ValidationMode.QUICK).map { it.id })
        assertEquals("Thinking ON / output 512",ValidationCatalog.cases.first { it.id=="thinking_budget_512" }.name)
        assertEquals("Thinking ON / output 1024",ValidationCatalog.cases.first { it.id=="thinking_budget_1024" }.name)
    }
    private suspend fun crashResult(id:String,error:Throwable):ValidationResult {
        val case=ValidationCatalog.cases.first { it.id==id }
        val report=ValidationReport(runId="host-control",appVersion="0.2.4",device="HOST",androidApi=29,
            runtime="0.17.1",startedAt="2026-10-06T00:00:00Z",suiteType=ValidationMode.FULL,
            tests=listOf(ValidationResult(id,case.name,case.expected,true,case.critical)))
        val backend=object:ValidationBackend {
            override val environment=EvidenceEnvironment.HOST_TESTED
            override suspend fun execute(id:String):ValidationOutcome { throw error }
            override suspend fun close() {}
        }
        return ValidationEngine(backend,persist={}).run(report).tests.single()
    }
    @Test fun uncaughtControlCrashesFailAndCorruptionIsNotTraversal() = kotlinx.coroutines.test.runTest {
        for(id in listOf("thinking_budget_256","zip_traversal")) {
            val result=crashResult(id,AssertionError("synthetic crash"))
            assertEquals(ValidationStatus.FAIL,result.status)
            assertEquals(EvidenceEnvironment.HOST_TESTED,result.evidence)
        }
        val corrupt=crashResult("zip_traversal",java.util.zip.ZipException("synthetic corruption"))
        assertEquals(ValidationStatus.FAIL,corrupt.status);assertEquals("ZIP_CORRUPT",corrupt.reasonCode)
        assertEquals(ValidationStatus.FAIL,crashResult("thinking_budget_256",java.io.IOException("unexpected SDK IO")).status)
    }
    @Test fun unavailableFilesystemAndUnrelatedCasesKeepBlockedContract() = kotlinx.coroutines.test.runTest {
        assertEquals(ValidationStatus.BLOCKED,crashResult("thinking_budget_256",java.io.FileNotFoundException("missing infrastructure")).status)
        assertEquals(ValidationStatus.BLOCKED,crashResult("text",AssertionError("synthetic crash")).status)
        assertEquals(ValidationStatus.BLOCKED,crashResult("thinking_budget_512",AssertionError("synthetic crash")).status)
    }
}
