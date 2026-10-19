package com.localai.workspace.performance

import com.localai.workspace.domain.model.RuntimeMetrics
import org.junit.Test
import org.junit.Assert.*

class BenchmarkSummaryTest {
    private val config=BenchmarkConfiguration("CPU",4,4096,512,0.2f,40,0.9f,1f,1,false,false)
    private fun row(id:String)=BenchmarkRecord(id=id,runId="r",startedAtEpochMs=1,modelId="e2b",modelFile="model",modelSha256="a".repeat(64),device="same-device",androidApi=36,iteration=1,mode=BenchmarkMode.WARM,testId="reasoning",configuration=config,metrics=RuntimeMetrics(timeToFirstTokenMs=100,requestTimeToFirstTokenMs=200,decodeTokensPerSecond=10.0,backendEffective="CPU",speculativeActive=false),result=BenchmarkResult.SUCCESS,validationPassed=true)
    @Test fun distributionsPreserveMissingAndIgnoreInvalidSamples() {
        assertEquals(MetricDistribution(0,null,null),BenchmarkSummaries.distribution(listOf(null,Double.NaN,Double.POSITIVE_INFINITY,-1.0)))
        val d=BenchmarkSummaries.distribution((1..20).map{it.toDouble()});assertEquals(10.5,d.p50!!,0.0);assertEquals(19.0,d.p95!!,0.0)
        assertEquals(1,BenchmarkSummaries.distribution(listOf(0.0,null)).measuredSamples)
    }
    @Test fun cohortsDoNotMixDevicesModelsConfigurationBoundariesOrFallback() {
        val r=row("a");val all=listOf(r,r.copy(id="b"),r.copy(id="c",device="other"),r.copy(id="d",modelSha256="b".repeat(64)),r.copy(id="e",configuration=config.copy(context=8192)),r.copy(id="f",ttftBoundary="UI_VISIBLE"),r.copy(id="g",metrics=r.metrics.copy(backendEffective="GPU")))
        val groups=BenchmarkSummaries.groups(all);assertEquals(6,groups.size);assertEquals(2,groups.single{it.records==2}.successful)
        assertEquals(2,BenchmarkSummaries.groups(listOf(r.copy(modelSha256=null,runId="one"),r.copy(modelSha256=null,runId="two"))).size)
    }
    @Test fun wrongAnswersAndCancelledRunsDoNotMakeSpeedLookBetter() {
        val r=row("a");val bad=r.copy(id="b",validationPassed=false,metrics=r.metrics.copy(timeToFirstTokenMs=1));val cancel=r.copy(id="c",result=BenchmarkResult.CANCELLED)
        val group=BenchmarkSummaries.groups(listOf(r,bad,cancel)).single();assertEquals(1,group.successful);assertEquals(1,group.rejectedQuality);assertEquals(100.0,group.nativeTtftMs.p50!!,0.0)
        assertEquals(2,group.qualityChecked);assertEquals(1,group.qualityPassed)
        assertNull(group.observedWorkerPssBytes.p50)
    }

    @Test fun comparableCohortsExposePreparationPrefillAndObservedMemoryDistributions() {
        val first=row("a").copy(metrics=row("a").metrics.copy(modelLoadDurationMs=100,modelPreparationDurationMs=50,
            prefillTokensPerSecond=8.0,appPssBeforeBytes=100,appPssAfterBytes=130,
            workerPssBeforeBytes=1_000,workerPssAfterBytes=900,availableRamAfterBytes=6_000))
        val second=row("b").copy(metrics=row("b").metrics.copy(modelLoadDurationMs=200,modelPreparationDurationMs=75,
            prefillTokensPerSecond=10.0,appPssBeforeBytes=200,appPssAfterBytes=190,
            workerPssBeforeBytes=1_100,workerPssAfterBytes=1_200,availableRamAfterBytes=5_000))
        val summary=BenchmarkSummaries.groups(listOf(first,second)).single()
        assertEquals(MetricDistribution(2,150.0,200.0),summary.modelLoadMs)
        assertEquals(MetricDistribution(2,62.5,75.0),summary.modelPreparationMs)
        assertEquals(MetricDistribution(2,9.0,10.0),summary.prefillTokensPerSecond)
        assertEquals(MetricDistribution(2,165.0,200.0),summary.observedAppPssBytes)
        assertEquals(MetricDistribution(2,1_100.0,1_200.0),summary.observedWorkerPssBytes)
        assertEquals(MetricDistribution(2,5_500.0,6_000.0),summary.availableRamAfterBytes)
        assertEquals(mapOf(
            "Model load p50/p95" to "150 / 200 ms (n=2)",
            "Model preparation p50/p95" to "62.5 / 75 ms (n=2)",
            "Prefill p50/p95" to "9 / 10 tokens/s (n=2)",
            "Observed app PSS max(before, after) p50/p95" to "165 / 200 bytes (n=2)",
            "Observed worker PSS max(before, after) p50/p95" to "1100 / 1200 bytes (n=2)",
            "Available RAM after p50/p95" to "5500 / 6000 bytes (n=2)",
        ),BenchmarkSummaryPresentation.additionalMetricRows(summary).toMap())
    }

    @Test fun unavailableAdditionalMeasurementsStayExplicit() {
        val summary=BenchmarkSummaries.groups(listOf(row("a"))).single()
        assertTrue(BenchmarkSummaryPresentation.additionalMetricRows(summary).all { it.second=="unavailable (0 measured)" })
    }
}
