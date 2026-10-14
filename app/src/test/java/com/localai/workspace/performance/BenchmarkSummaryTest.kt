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
        assertNull(group.observedWorkerPssBytes.p50)
    }
}
