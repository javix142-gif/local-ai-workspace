package com.localai.workspace.data

import com.localai.workspace.domain.model.RuntimeMetrics
import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

class GenerationMetricsPresentationTest {
    @Test fun actualPhoneLocaleCommaMetricsKeepCorrectTimingsAndSpeedInsideDetails() {
        val metrics = GenerationMetricsPresentation.decode("engineReused=true,sessionReused=true,prepareMs=96,prefillMs=1330,prompt=21,output=85,ttftMs=3116,requestTtftMs=3338,prefillTps=15,79,decodeTps=3,55,totalMs=25290,chunks=84,nativeDone=true,outputLimitReached=false")!!
        val rows = metrics.rows().toMap()
        assertEquals("3.34 s", rows["Wait until first text"])
        assertEquals("25.29 s", rows["Response generation"])
        assertEquals("3.55 tokens/s", rows["Generation speed"])
        assertEquals("21", rows["New input tokens"])
        assertEquals("Yes", rows["Conversation reused"])
        assertFalse(metrics.outputLimitReached)
        assertFalse(rows.containsKey("Model initialization"))
    }

    @Test fun outputCapNeedsExplicitNativeMetricRatherThanOnDoneOrTextThatLooksIncomplete() {
        assertTrue(GenerationMetricsPresentation.decode("nativeDone=true,output=128,outputLimitReached=true")!!.outputLimitReached)
        for (raw in listOf("nativeDone=true,output=128", "outputLimitReached=false", "outputLimitReached=unknown", "4")) {
            assertFalse(GenerationMetricsPresentation.decode(raw)!!.outputLimitReached)
        }
    }

    @Test fun newEncodingsUseStableNumbersRegardlessOfPhoneLocaleAndKeepRealFinishFlags() {
        val before = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            val raw = GenerationMetricsPresentation.encode(RuntimeMetrics(outputTokens = 256, decodeTokensPerSecond = 3.55,
                engineReused = true, sessionReused = true, nativeCompletionObserved = true, outputLimitReached = true))
            assertTrue(raw.contains("decodeTps=3.55,"))
            assertFalse(raw.contains("eos="))
            assertTrue(GenerationMetricsPresentation.decode(raw)!!.outputLimitReached)
        } finally { Locale.setDefault(before) }
    }

    @Test fun absentInvalidOrDiagnosticValuesDoNotBecomeInventedBenchmarkRows() {
        for (raw in listOf(null, "", " ", "x".repeat(32_001), "{\"type\":\"generation_error\"}")) assertNull(GenerationMetricsPresentation.decode(raw))
        val metrics = GenerationMetricsPresentation.decode("requestTtftMs=-1,decodeTps=NaN,output=unknown,sessionReused=maybe")!!
        assertTrue(metrics.rows().isEmpty())
        assertFalse(metrics.outputLimitReached)
        assertEquals("legacy benchmark", GenerationMetricsPresentation.decode("legacy benchmark")!!.raw)
    }
}
