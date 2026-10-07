package com.localai.workspace.inference

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.localai.workspace.data.*
import com.localai.workspace.domain.model.RuntimeMetrics
import com.localai.workspace.validation.ValidationMetricCodec
import org.junit.Assert.*
import org.junit.Test

class ThinkingDiagnosticsTest {
    @Test fun thoughtThenPrimaryFinal() {
        val t=ThinkingCallbackMetrics()
        assertEquals("",t.accept("",mapOf("thought" to "hidden reasoning"),10))
        assertEquals("7",t.accept("7",emptyMap(),20))
        val m=t.applyTo(RuntimeMetrics(thinkingEffective=true),256,true)
        assertEquals(1,m.thoughtCallbackCount);assertEquals(1,m.finalCallbackCount)
        assertEquals(10L,m.timeToFirstThoughtMs);assertEquals(20L,m.timeToFirstFinalMs)
        assertEquals(1,m.visibleOutputLength);assertNull(ThinkingFailure.reason(m))
    }
    @Test fun mixedSdkCallbackRetainsOnlyPrimaryContent() {
        assertEquals("7",VisibleModelOutput.select("7",mapOf("thought" to "secret")))
        assertEquals("7",VisibleModelOutput.select("7",mapOf("analysis" to "secret")))
    }
    @Test fun thoughtOnlyNeverBecomesAnswer() {
        val t=ThinkingCallbackMetrics();assertEquals("",t.accept("",mapOf("thought" to "secret"),1))
        assertEquals("THINKING_NO_FINAL_CHANNEL",ThinkingFailure.reason(t.applyTo(RuntimeMetrics(thinkingEffective=true),256,true)))
    }
    @Test fun finalOnlyWorks() { assertEquals("7",VisibleModelOutput.select("7",emptyMap())) }
    @Test fun namedFinalWorksAlongsideAnalysis() { assertEquals("7",VisibleModelOutput.select("",mapOf("analysis" to "secret","final" to "7"))) }
    @Test fun unknownThenFinal() {
        val t=ThinkingCallbackMetrics();assertEquals("",t.accept("",mapOf("other" to "secret"),1))
        assertEquals("7",t.accept("7",emptyMap(),2));assertEquals(1,t.applyTo(RuntimeMetrics(),256,true).unknownChannelCallbackCount)
    }
    @Test fun unknownContentBlockedConservatively() { assertEquals("",VisibleModelOutput.select("unclassified",mapOf("other" to "secret"))) }
    @Test fun emptyCallbacksAreNotVisibleTokens() {
        val t=ThinkingCallbackMetrics();t.accept("",emptyMap(),1)
        val m=t.applyTo(RuntimeMetrics(thinkingEffective=false),256,false)
        assertEquals(1,m.rawCallbackCount);assertEquals(0,m.finalCallbackCount);assertNull(m.timeToFirstFinalMs)
        assertEquals("THINKING_NO_VISIBLE_OUTPUT",ThinkingFailure.reason(m))
    }
    @Test fun budgetExhaustionUsesRealLimitFlag() {
        assertEquals("THINKING_OUTPUT_BUDGET_EXHAUSTED",ThinkingFailure.reason(RuntimeMetrics(outputLimitReached=true,thinkingEffective=true)))
        assertEquals("THINKING_NO_VISIBLE_OUTPUT",ThinkingFailure.reason(RuntimeMetrics(outputLimitReached=null,thinkingEffective=true)))
    }
    @Test fun filteredFinalIsDistinctFromNoFinal() { assertEquals("THINKING_CALLBACK_FILTERED",ThinkingFailure.reason(RuntimeMetrics(finalCharacterCount=3,visibleOutputLength=0))) }
    @Test fun timeoutIsDistinct() { assertEquals("THINKING_TIMEOUT",ThinkingFailure.reason(RuntimeMetrics(finishState="TIMEOUT"))) }
    @Test fun offDoesNotAddThinkingBudget() { assertFalse(ThinkingOutputPolicy.config(false,256).enableThinking);assertEquals(-1,ThinkingOutputPolicy.config(false,256).thinkingTokenBudget) }
    @Test fun officialBudgetBoundedAndOutputUnchanged() {
        for(cap in listOf(1,2,32,128,256,512,1024,8192)) {
            val m=ThinkingCallbackMetrics().applyTo(RuntimeMetrics(),cap,true)
            assertEquals(cap,m.configuredMaxOutput);assertEquals(cap,m.effectiveMaxOutput)
            assertTrue(m.thinkingTokenBudget!! <= cap/2);assertTrue(m.thinkingTokenBudget <=128)
        }
    }
    @Test fun validationCanReproduceUnboundedBudget() { assertEquals(-1,ThinkingOutputPolicy.config(true,256,-1).thinkingTokenBudget) }
    @Test fun autoSimpleOffAndReasoningOn() {
        assertFalse(AssistantRouting.thinking(ThinkingMode.AUTO,true,"Hola."))
        assertFalse(AssistantRouting.thinking(ThinkingMode.AUTO,true,"¿Cuál es la capital de Japón?"))
        assertTrue(AssistantRouting.thinking(ThinkingMode.AUTO,true,"Analiza cuidadosamente: tres cajas contienen 4 objetos cada una. Se retiran 5. ¿Cuántos quedan?"))
        assertTrue(AssistantRouting.thinking(ThinkingMode.ON,true,"Hola"))
    }
    @Test fun noReasoningIsStoredInTelemetry() {
        val t=ThinkingCallbackMetrics();t.accept("",mapOf("thought" to "PRIVATE_REASONING_SENTINEL"),1)
        val m=t.applyTo(RuntimeMetrics(),256,true)
        assertFalse(Gson().toJson(m).contains("PRIVATE_REASONING_SENTINEL"))
        assertFalse(ValidationMetricCodec.runtime(m).toString().contains("PRIVATE_REASONING_SENTINEL"))
    }
    @Test fun changingThinkingInvalidatesNativeConversation() {
        val off=LiteRtConversationReuse.Settings("policy",.3f,.95f,40,0,256,"model",true,thinking=false)
        val tracker=LiteRtConversationReuse()
        tracker.begin(off,emptyList())
        tracker.completed("hola","Hola")
        val history=listOf(com.localai.workspace.domain.model.ChatMessage(com.localai.workspace.domain.model.MessageRole.USER,"hola"),
            com.localai.workspace.domain.model.ChatMessage(com.localai.workspace.domain.model.MessageRole.ASSISTANT,"Hola"))
        assertEquals("THINKING_CHANGED",tracker.reason(off.copy(thinking=true),history))
        assertFalse(tracker.canContinue(off.copy(thinking=true),history))
    }
    @Test fun prefixedExportAllowsOnlyKnownScalarKeys() {
        val input=JsonObject().apply {
            addProperty("ON_REASONING.thoughtCharacterCount",8)
            addProperty("ON_REASONING.thoughtText","PRIVATE_REASONING_SENTINEL")
            addProperty("ON_REASONING.reasonCode","THINKING_NO_FINAL_CHANNEL")
            addProperty("UNKNOWN.thoughtCharacterCount",8)
        }
        val output=ValidationMetricCodec.sanitize(input)
        assertEquals(2,output.size());assertFalse(output.toString().contains("PRIVATE_REASONING_SENTINEL"))
        assertTrue(output.has("ON_REASONING.reasonCode"))
    }
}
